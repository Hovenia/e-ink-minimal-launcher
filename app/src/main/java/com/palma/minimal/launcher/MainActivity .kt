package com.palma.minimal.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.LruCache
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Suppress("SetTextI18n", "SpellCheckingInspection", "SameParameterValue", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvDateBattery: TextView
    private lateinit var tvAllApps: TextView
    private lateinit var tvSettings: TextView
    private lateinit var recyclerViewApps: RecyclerView
    private lateinit var btnRefreshScreen: View
    private lateinit var indexBarLayout: LinearLayout
    private lateinit var indexBarContainer: View
    private lateinit var layoutSettings: ScrollView
    private lateinit var settingsContainer: LinearLayout

    private lateinit var appAdapter: AppAdapter
    private var allAppsList = mutableListOf<AppInfo>()
    private var favoriteAppsList = mutableListOf<AppInfo>()
    private var filteredAppsList = mutableListOf<AppInfo>()

    private lateinit var prefs: SharedPreferences
    private var isShowingAllApps = false
    private var isShowingSettings = false
    private var currentSelectedIndex = -1
    private var availableIndices = mutableListOf<String>()

    private var settingsStack = mutableListOf<() -> Unit>()

    private var favColumns = 2
    private var dateLocale = "ZH"
    private var favIconSizeDp = 64
    private var allIconSizeDp = 48
    private var favTextSizeSp = 14f
    private var allTextSizeSp = 16f
    private var isStatusBarHidden = false
    private var isAppNamesHidden = false
    private var isIconOptimizationEnabled = true

    private lateinit var gestureDetector: GestureDetector
    private lateinit var iconPackManager: IconPackManager
    private var globalTypeface: Typeface? = null

    private val iconCache = LruCache<String, Drawable>(200)
    private val diskCacheDir by lazy { File(cacheDir, "eink_lineart_cache").apply { mkdirs() } }

    private val chinaCollator by lazy { java.text.Collator.getInstance(Locale.CHINA) }
    private val pinyinLetters = arrayOf("A","B","C","D","E","F","G","H","J","K","L","M","N","O","P","Q","R","S","T","W","X","Y","Z")
    private val pinyinBoundaries = arrayOf("啊","芭","擦","搭","蛾","发","噶","哈","击","喀","垃","妈","拿","哦","啪","期","然","撒","塌","挖","昔","压","匝")

    private val tempIconPackNames = mutableListOf<String>()
    private val tempIconPackPkgs = mutableListOf<String>()

    companion object {
        private const val PREFS_NAME = "LauncherPrefs"
        private const val KEY_FAVORITES = "favorites_list"
        private const val KEY_COLUMNS = "fav_columns"
        private const val KEY_LOCALE = "date_locale"
        private const val KEY_FAV_ICON_SIZE = "fav_icon_size"
        private const val KEY_ALL_ICON_SIZE = "all_icon_size"
        private const val KEY_FAV_TEXT_SIZE = "fav_text_size"
        private const val KEY_ALL_TEXT_SIZE = "all_text_size"
        private const val KEY_HIDE_STATUS_BAR = "hide_status_bar"
        private const val KEY_HIDE_APP_NAMES = "hide_app_names"
        private const val KEY_CUSTOM_FONT_URI = "custom_font_uri"
        private const val KEY_ICON_PACKS = "icon_packs"
        private const val KEY_ICON_PACK_LEGACY = "icon_pack"
        private const val KEY_ICON_OPTIMIZATION = "icon_optimization"

        private val POTENTIAL_INDICES = listOf(
            "★","ㄱ","ㄴ","ㄷ","ㄹ","ㅁ","ㅂ","ㅅ","ㅇ","ㅈ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ",
            "A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","#"
        )
    }

    private val textFavorites: String
        get() = when (dateLocale) { "EN" -> "Favorites"; "JA" -> "お気に入り"; else -> "收藏" }
    private val textAllApps: String
        get() = when (dateLocale) { "EN" -> "All Apps"; "JA" -> "すべてのアプリ"; else -> "所有应用" }
    private val textSettingsMenu: String
        get() = when (dateLocale) { "EN" -> "Settings"; "JA" -> "設定"; else -> "设置" }
    private val textClose: String
        get() = when (dateLocale) { "EN" -> "Close"; "JA" -> "閉じる"; else -> "关闭" }
    private val textConfirm: String
        get() = when (dateLocale) { "EN" -> "OK"; "JA" -> "確認"; else -> "确定" }
    private val textCancel: String
        get() = when (dateLocale) { "EN" -> "Cancel"; "JA" -> "キャンセル"; else -> "取消" }

    // ==================== 生命周期 ====================

    private val fontPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    if (uri.scheme == "content") {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } catch (_: Exception) { }
                prefs.edit { putString(KEY_CUSTOM_FONT_URI, uri.toString()) }
                applyCustomFont(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isStatusBarHidden = prefs.getBoolean(KEY_HIDE_STATUS_BAR, false)
        isAppNamesHidden = prefs.getBoolean(KEY_HIDE_APP_NAMES, false)
        isIconOptimizationEnabled = prefs.getBoolean(KEY_ICON_OPTIMIZATION, true)
        applyStatusBarState()

        setContentView(R.layout.activity_main)

        iconPackManager = IconPackManager(this)
        val savedPacks = loadIconPacksFromPrefs()
        if (savedPacks.isNotEmpty()) iconPackManager.setIconPacks(savedPacks)

        favColumns = prefs.getInt(KEY_COLUMNS, 2)
        dateLocale = prefs.getString(KEY_LOCALE, "ZH") ?: "ZH"
        favIconSizeDp = prefs.getInt(KEY_FAV_ICON_SIZE, 64)
        allIconSizeDp = prefs.getInt(KEY_ALL_ICON_SIZE, 48)
        favTextSizeSp = prefs.getFloat(KEY_FAV_TEXT_SIZE, 14f)
        allTextSizeSp = prefs.getFloat(KEY_ALL_TEXT_SIZE, 16f)

        initViews()
        val savedFontUri = prefs.getString(KEY_CUSTOM_FONT_URI, null)
        if (savedFontUri != null) applyCustomFont(savedFontUri.toUri()) else applyCustomFont(null)
        setupRecyclerView()
        loadAppsAsync()
        updateHeader()
        setupListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryReceiver?.let { unregisterReceiver(it) }
        packageReceiver?.let { unregisterReceiver(it) }
    }

    private fun initViews() {
        tvTime = findViewById(R.id.tvTime)
        tvDateBattery = findViewById(R.id.tvDateBattery)
        tvAllApps = findViewById(R.id.tvAllApps)
        tvSettings = findViewById(R.id.tvSettings)
        recyclerViewApps = findViewById(R.id.recyclerViewApps)
        btnRefreshScreen = findViewById(R.id.btnRefreshScreen)
        indexBarLayout = findViewById(R.id.indexBarLayout)
        indexBarContainer = findViewById(R.id.indexBarContainer)
        layoutSettings = findViewById(R.id.layoutSettings)
        settingsContainer = findViewById(R.id.settingsContainer)
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter(mutableListOf(), this::onAppClicked, this::onAppLongClicked, this::onOrderChanged)
        updateAdapterSizes()
        recyclerViewApps.adapter = appAdapter
        recyclerViewApps.itemAnimator = null
        updateDisplayList()

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (isShowingAllApps || isShowingSettings) return false
                appAdapter.onItemMove(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
            override fun isLongPressDragEnabled(): Boolean = !isShowingAllApps && !isShowingSettings
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerViewApps)
    }

    private fun setupListeners() {
        tvAllApps.setOnClickListener {
            if (isShowingSettings) {
                isShowingSettings = false; settingsStack.clear()
            } else {
                isShowingAllApps = !isShowingAllApps
            }
            updateDisplayList(); updateHeader()
        }

        tvSettings.setOnClickListener {
            if (isShowingSettings) {
                settingsBack()
            } else {
                isShowingSettings = true; settingsStack.clear()
                updateDisplayList()
                navigateTo { buildSettingsContent() }
                updateHeader()
            }
        }

        btnRefreshScreen.setOnClickListener {
            sendBroadcast(Intent("android.intent.action.ONYX_GC_ANYWAY"))
            window.decorView.invalidate()
        }

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { lockScreen(); return true }
        })
        val tl = View.OnTouchListener { _, event -> gestureDetector.onTouchEvent(event); false }
        recyclerViewApps.setOnTouchListener(tl)
        findViewById<View>(android.R.id.content).setOnTouchListener(tl)

        val timeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { updateHeader() }
        }
        registerReceiver(batteryReceiver, timeFilter)

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { iconCache.evictAll(); loadAppsAsync() }
        }
        registerReceiver(packageReceiver, packageFilter)
    }

    // ==================== 设置导航 ====================

    private fun settingsBack() {
        if (settingsStack.size > 1) {
            settingsStack.removeAt(settingsStack.size - 1)
            settingsContainer.removeAllViews()
            settingsStack.last().invoke()
        } else {
            isShowingSettings = false
            settingsStack.clear()
            updateDisplayList()
            updateHeader()
        }
    }

    private fun navigateTo(builder: () -> Unit) {
        settingsStack.add(builder)
        settingsContainer.removeAllViews()
        builder()
    }

    private fun refreshCurrent() {
        settingsContainer.removeAllViews()
        settingsStack.lastOrNull()?.invoke()
    }

    // ==================== 工具方法 ====================

    private fun loadIconPacksFromPrefs(): List<String> {
        val saved = prefs.getString(KEY_ICON_PACKS, null)
        if (saved != null) return if (saved.isEmpty()) emptyList() else saved.split(",").filter { it.isNotEmpty() }
        val legacy = prefs.getString(KEY_ICON_PACK_LEGACY, null)
        if (!legacy.isNullOrEmpty()) {
            prefs.edit { putString(KEY_ICON_PACKS, legacy); remove(KEY_ICON_PACK_LEGACY) }
            return listOf(legacy)
        }
        return emptyList()
    }

    private fun saveIconPacksToPrefs(packs: List<String>) {
        prefs.edit { putString(KEY_ICON_PACKS, packs.joinToString(",")) }
    }

    private fun applyCustomFont(uri: Uri?) {
        if (uri == null) { globalTypeface = null }
        else {
            try {
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) { globalTypeface = Typeface.Builder(pfd.fileDescriptor).build(); pfd.close() }
            } catch (_: Exception) { globalTypeface = null }
        }
        val tf = globalTypeface ?: Typeface.DEFAULT_BOLD
        tvTime.typeface = tf; tvDateBattery.typeface = tf; tvAllApps.typeface = tf; tvSettings.typeface = tf
        if (globalTypeface == null) {
            tvTime.setTypeface(null, Typeface.BOLD); tvDateBattery.setTypeface(null, Typeface.BOLD)
            tvAllApps.setTypeface(null, Typeface.BOLD); tvSettings.setTypeface(null, Typeface.BOLD)
        }
        for (i in 0 until indexBarLayout.childCount) (indexBarLayout.getChildAt(i) as TextView).typeface = tf
        if (::appAdapter.isInitialized) updateAdapterSizes()
        if (isShowingSettings) refreshCurrent()
    }

    @Suppress("DEPRECATION")
    private fun applyStatusBarState() {
        if (isStatusBarHidden) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    private fun toggleStatusBar() {
        isStatusBarHidden = !isStatusBarHidden
        prefs.edit { putBoolean(KEY_HIDE_STATUS_BAR, isStatusBarHidden) }
        applyStatusBarState(); refreshCurrent()
    }

    private fun toggleAppNames() {
        isAppNamesHidden = !isAppNamesHidden
        prefs.edit { putBoolean(KEY_HIDE_APP_NAMES, isAppNamesHidden) }
        updateAdapterSizes(); refreshCurrent()
    }

    private fun toggleIconOptimization() {
        isIconOptimizationEnabled = !isIconOptimizationEnabled
        prefs.edit { putBoolean(KEY_ICON_OPTIMIZATION, isIconOptimizationEnabled) }
        iconCache.evictAll()
        Thread { clearDiskCache() }.start()
        loadAppsAsync(); refreshCurrent()
    }

    private fun lockScreen() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val cn = android.content.ComponentName(this, AdminReceiver::class.java)
        if (dpm.isAdminActive(cn)) { dpm.lockNow() }
        else {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "双击息屏功能需要您激活设备管理器权限。")
            startActivity(intent)
        }
    }

    private fun updateAdapterSizes() {
        val density = resources.displayMetrics.density
        appAdapter.setSizesAndFont((favIconSizeDp*density).toInt(), (allIconSizeDp*density).toInt(),
            favTextSizeSp, allTextSizeSp, isAppNamesHidden, globalTypeface)
        iconCache.evictAll(); loadAppsAsync()
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i != -1) result = c.getString(i) }
                }
            } catch (_: Exception) { }
        }
        if (result == null) { result = uri.path; val cut = result?.lastIndexOf('/') ?: -1; if (cut != -1) result = result?.substring(cut + 1) }
        val n = result ?: "未知文件"
        return if (n.length > 15) n.take(10) + "..." + n.takeLast(4) else n
    }

    // ==================== 缺失的磁盘缓存方法与重启方法 ====================

    private fun clearDiskCache() {
        diskCacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun restartLauncher() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        Runtime.getRuntime().exit(0)
    }

    // ==================== 图标处理 ====================

    private fun applyEinkOptimization(originalDrawable: Drawable): Drawable {
        val mutated = originalDrawable.constantState?.newDrawable()?.mutate() ?: originalDrawable.mutate()
        val matrix = ColorMatrix(); matrix.setSaturation(0f)
        matrix.postConcat(ColorMatrix(floatArrayOf(1.3f,0f,0f,0f,-10f, 0f,1.3f,0f,0f,-10f, 0f,0f,1.3f,0f,-10f, 0f,0f,0f,1f,0f)))
        mutated.colorFilter = ColorMatrixColorFilter(matrix); return mutated
    }

    private fun isSimpleLineIcon(pixels: IntArray): Boolean {
        val colorSet = mutableSetOf<Int>()
        var lightCount = 0; var totalCount = 0
        for (p in pixels) {
            val a = Color.alpha(p); if (a < 10) continue
            totalCount++
            colorSet.add(Color.rgb((Color.red(p)/64)*64, (Color.green(p)/64)*64, (Color.blue(p)/64)*64))
            if ((0.299*Color.red(p) + 0.587*Color.green(p) + 0.114*Color.blue(p)).toInt() > 200) lightCount++
        }
        return colorSet.size <= 5 && (if (totalCount > 0) lightCount.toDouble() / totalCount else 0.0) > 0.50
    }

    private fun processSimpleIcon(gray: IntArray, originalPixels: IntArray): IntArray {
        val histogram = IntArray(256); val total = 192 * 192
        for (g in gray) histogram[g]++
        var sumAll = 0L; for (i in 0 until 256) sumAll += i * histogram[i]
        var sumB = 0L; var wB = 0; var maxVar = 0.0; var bestTh = 128
        for (t in 0 until 256) {
            wB += histogram[t]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += t * histogram[t]
            val mB = sumB.toDouble() / wB; val mF = (sumAll - sumB).toDouble() / wF
            val v = wB.toLong() * wF.toLong() * (mB - mF) * (mB - mF)
            if (v > maxVar) { maxVar = v; bestTh = t }
        }
        val result = IntArray(total) { Color.WHITE }; var nonT = 0; var black = 0
        for (i in gray.indices) { if (Color.alpha(originalPixels[i]) < 10) continue; nonT++; if (gray[i] < bestTh) { result[i] = Color.BLACK; black++ } }
        if (nonT > 0 && black.toDouble() / nonT > 0.65) return processComplexIcon(gray)
        val thickened = result.copyOf()
        for (y in 1 until 191) for (x in 1 until 191) {
            val idx = y * 192 + x
            if (thickened[idx] == Color.WHITE) {
                for (dy in -1..1) for (dx in -1..1) { if (result[(y+dy)*192+(x+dx)] == Color.BLACK) { thickened[idx] = Color.BLACK; break } }
                if (thickened[idx] == Color.BLACK) break
            }
        }
        return thickened
    }

    private fun processComplexIcon(gray: IntArray): IntArray {
        val smoothed = IntArray(192 * 192)
        for (y in 0 until 192) for (x in 0 until 192) {
            var sum = 0; var w = 0
            for (dy in -1..1) for (dx in -1..1) {
                val ny = (y+dy).coerceIn(0,191); val nx = (x+dx).coerceIn(0,191)
                val weight = if (abs(dx)+abs(dy)==0) 4 else if (abs(dx)+abs(dy)==1) 2 else 1
                sum += gray[ny*192+nx]*weight; w += weight
            }
            smoothed[y*192+x] = sum / w
        }
        val sobelMag = IntArray(192 * 192)
        for (y in 1 until 191) for (x in 1 until 191) {
            val tl=smoothed[(y-1)*192+(x-1)]; val tc=smoothed[(y-1)*192+x]; val tr=smoothed[(y-1)*192+(x+1)]
            val ml=smoothed[y*192+(x-1)]; val mr=smoothed[y*192+(x+1)]
            val bl=smoothed[(y+1)*192+(x-1)]; val bc=smoothed[(y+1)*192+x]; val br=smoothed[(y+1)*192+(x+1)]
            sobelMag[y*192+x] = abs(-tl+tr-2*ml+2*mr-bl+br) + abs(-tl-2*tc-tr+bl+2*bc+br)
        }
        val edgeMap = IntArray(192*192) { i -> when { sobelMag[i]>=150->2; sobelMag[i]>=60->1; else->0 } }
        val finalEdge = IntArray(192*192) { Color.WHITE }
        for (i in edgeMap.indices) { if (edgeMap[i]==2) finalEdge[i] = Color.BLACK }
        var changed = true
        while (changed) {
            changed = false
            for (y in 1 until 191) for (x in 1 until 191) {
                val idx = y*192+x
                if (edgeMap[idx]!=1 || finalEdge[idx]==Color.BLACK) continue
                var found = false
                run { for (dy in -1..1) for (dx in -1..1) { if (finalEdge[(y+dy)*192+(x+dx)]==Color.BLACK) { found=true; return@run } } }
                if (found) { finalEdge[idx]=Color.BLACK; changed=true }
            }
        }
        var bc = 0; for (p in finalEdge) if (p==Color.BLACK) bc++
        if (bc.toDouble()/(192*192) > 0.50) {
            val retry = IntArray(192*192) { Color.WHITE }
            for (i in edgeMap.indices) { if (sobelMag[i]>=250) retry[i]=Color.BLACK }
            var rc = true
            while (rc) {
                rc = false
                for (y in 1 until 191) for (x in 1 until 191) {
                    val idx=y*192+x
                    if (sobelMag[idx] !in 120..249 || retry[idx]==Color.BLACK) continue
                    var f=false; run { for (dy in -1..1) for (dx in -1..1) { if (retry[(y+dy)*192+(x+dx)]==Color.BLACK) { f=true; return@run } } }
                    if (f) { retry[idx]=Color.BLACK; rc=true }
                }
            }
            return retry
        }
        return finalEdge
    }

    private fun generateFallbackLineArtIcon(originalDrawable: Drawable): Drawable {
        val displaySize = 128
        val cornerRadiusProc = 192 * 0.22f
        val cornerRadiusDisp = 128 * 0.22f
        val borderWidth = 5f
        val edgeInset = 8f

        fun distToRoundedRect(px: Int, py: Int): Float {
            val x=px.toFloat(); val y=py.toFloat(); val s=192f; val r=cornerRadiusProc
            val ds = minOf(x, s-x, y, s-y)
            val inL=x<r; val inR=x>s-r; val inT=y<r; val inB=y>s-r
            if ((inL||inR)&&(inT||inB)) { val cx=if(inL)r else s-r; val cy=if(inT)r else s-r; return r-kotlin.math.sqrt((x-cx)*(x-cx)+(y-cy)*(y-cy)) }
            return ds
        }

        val srcBitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val srcCanvas = Canvas(srcBitmap); srcCanvas.drawColor(Color.WHITE)
        originalDrawable.setBounds(0, 0, 192, 192); originalDrawable.draw(srcCanvas)

        val pixels = IntArray(192*192)
        srcBitmap.getPixels(pixels, 0, 192, 0, 0, 192, 192)
        val gray = IntArray(192*192) { i ->
            val a = Color.alpha(pixels[i])
            if (a<10) 255 else (0.299*Color.red(pixels[i])+0.587*Color.green(pixels[i])+0.114*Color.blue(pixels[i])).toInt()
        }

        val isSimple = isSimpleLineIcon(pixels)
        val contentPixels = if (isSimple) processSimpleIcon(gray, pixels) else processComplexIcon(gray)

        var totalNT = 0; var totalBlk = 0
        for (i in contentPixels.indices) { if (Color.alpha(pixels[i])<10) continue; totalNT++; if (contentPixels[i]==Color.BLACK) totalBlk++ }
        if (totalNT > 0 && totalBlk.toDouble()/totalNT > 0.55) {
            val fb = applyEinkOptimization(originalDrawable)
            val output = Bitmap.createBitmap(displaySize, displaySize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output); val ds = displaySize.toFloat()
            val op = Path().apply { addRoundRect(RectF(0f,0f,ds,ds), cornerRadiusDisp, cornerRadiusDisp, Path.Direction.CW) }
            val ip = Path().apply { addRoundRect(RectF(borderWidth,borderWidth,ds-borderWidth,ds-borderWidth), cornerRadiusDisp-borderWidth, cornerRadiusDisp-borderWidth, Path.Direction.CW) }
            canvas.drawPath(op, Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;style=Paint.Style.FILL})
            canvas.drawPath(ip, Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;style=Paint.Style.FILL})

            canvas.withClip(ip) {
                val cp = borderWidth + 2f
                fb.setBounds(cp.toInt(), cp.toInt(), (ds-cp).toInt(), (ds-cp).toInt())
                fb.draw(this)
            }
            srcBitmap.recycle()
            return output.toDrawable(resources)
        }

        for (y in 0 until 192) for (x in 0 until 192) {
            val idx = y*192+x
            if (contentPixels[idx]==Color.BLACK && distToRoundedRect(x,y)<edgeInset) contentPixels[idx]=Color.WHITE
        }

        val hr = Bitmap.createBitmap(contentPixels, 192, 192, Bitmap.Config.ARGB_8888)
        val sc = hr.scale(displaySize, displaySize, true)
        val output = Bitmap.createBitmap(displaySize, displaySize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output); val ds = displaySize.toFloat()
        val op = Path().apply { addRoundRect(RectF(0f,0f,ds,ds), cornerRadiusDisp, cornerRadiusDisp, Path.Direction.CW) }
        val ip = Path().apply { addRoundRect(RectF(borderWidth,borderWidth,ds-borderWidth,ds-borderWidth), cornerRadiusDisp-borderWidth, cornerRadiusDisp-borderWidth, Path.Direction.CW) }
        canvas.drawPath(op, Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;style=Paint.Style.FILL})
        canvas.drawPath(ip, Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;style=Paint.Style.FILL})

        canvas.withClip(ip) {
            val cp = borderWidth + 2f
            drawBitmap(sc, null, RectF(cp,cp,ds-cp,ds-cp), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        srcBitmap.recycle(); hr.recycle()
        return output.toDrawable(resources)
    }

    // ==================== 应用加载与磁盘缓存对接 ====================

    private fun loadAppsAsync() {
        Thread {
            val pm = packageManager
            val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            val newAll = mutableListOf<AppInfo>()

            for (app in apps) {
                val pkg = app.activityInfo.packageName; if (pkg == packageName) continue
                val cls = app.activityInfo.name; val name = app.loadLabel(pm).toString()
                val key = "$pkg/$cls"

                val safeCacheName = "${pkg}_${cls}".replace("[^a-zA-Z0-9_.]".toRegex(), "_")
                var icon = iconCache.get(key)

                if (icon == null) {
                    val packIcon = iconPackManager.getIcon(pkg, cls)

                    if (packIcon != null) {
                        icon = applyEinkOptimization(packIcon)
                    } else if (isIconOptimizationEnabled) {
                        val diskFile = File(diskCacheDir, "$safeCacheName.png")

                        if (diskFile.exists()) {
                            try {
                                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                                if (bitmap != null) {
                                    icon = BitmapDrawable(resources, bitmap)
                                }
                            } catch (_: Exception) {}
                        }

                        if (icon == null) {
                            icon = generateFallbackLineArtIcon(app.loadIcon(pm))
                            // 【修复后的代码】
                            val bitmapDrawable = icon as? BitmapDrawable
                            if (bitmapDrawable?.bitmap != null) {
                                try {
                                    FileOutputStream(diskFile).use { out ->
                                        bitmapDrawable.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } else {
                        icon = app.loadIcon(pm)
                    }

                    if (icon != null) {
                        iconCache.put(key, icon)
                    }
                }
                newAll.add(AppInfo(name, pkg, cls, icon))
            }

            newAll.sortBy { val init = getInitialLetter(it.name); (if (init=="#") "~" else init) + it.name.lowercase() }
            runOnUiThread { allAppsList.clear(); allAppsList.addAll(newAll); filterFavorites() }
        }.start()
    }

    private fun filterFavorites() {
        val favStr = prefs.getString(KEY_FAVORITES, "") ?: ""
        val favPkgs = if (favStr.isEmpty()) mutableListOf() else favStr.split(",").toMutableList()
        favoriteAppsList.clear()
        favPkgs.forEach { p -> allAppsList.find { it.packageName == p }?.let { favoriteAppsList.add(it) } }
        if (favoriteAppsList.isEmpty() && allAppsList.isNotEmpty()) { favoriteAppsList.addAll(allAppsList.take(8.coerceAtMost(allAppsList.size))); saveFavorites() }
        updateDisplayList()
    }

    private fun saveFavorites() { prefs.edit { putString(KEY_FAVORITES, favoriteAppsList.joinToString(",") { it.packageName }) } }

    private fun updateDisplayList() {
        if (isShowingSettings) {
            recyclerViewApps.visibility = View.GONE; indexBarContainer.visibility = View.GONE
            layoutSettings.visibility = View.VISIBLE
            return
        }
        layoutSettings.visibility = View.GONE; recyclerViewApps.visibility = View.VISIBLE
        if (isShowingAllApps) {
            recyclerViewApps.layoutManager = LinearLayoutManager(this)
            appAdapter.updateData(allAppsList, false); setupIndexBar()
            indexBarContainer.visibility = View.VISIBLE; tvAllApps.text = textAllApps
            recyclerViewApps.setPadding(24, 0, 60, 0)
        } else {
            recyclerViewApps.layoutManager = GridLayoutManager(this, favColumns)
            appAdapter.updateData(favoriteAppsList, true); indexBarContainer.visibility = View.GONE
            tvAllApps.text = textFavorites; recyclerViewApps.setPadding(24, 0, 24, 0)
        }
    }

    private fun onOrderChanged(newList: List<AppInfo>) { if (!isShowingAllApps) { favoriteAppsList = newList.toMutableList(); saveFavorites() } }

    private fun onAppClicked(appInfo: AppInfo) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER); setClassName(appInfo.packageName, appInfo.className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            })
        } catch (_: Exception) {
            packageManager.getLaunchIntentForPackage(appInfo.packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); try { startActivity(it) } catch (_: Exception) { }
            }
        }
    }

    private fun onAppLongClicked(appInfo: AppInfo) {
        if (isShowingAllApps) {
            val isFav = favoriteAppsList.any { it.packageName == appInfo.packageName }
            AlertDialog.Builder(this, R.style.EinkAlertDialogTheme).setTitle(appInfo.name)
                .setMessage("确认要${if (isFav) "取消收藏" else "加入收藏"}吗？")
                .setPositiveButton(textConfirm) { _, _ ->
                    if (isFav) favoriteAppsList.removeAll { it.packageName == appInfo.packageName } else favoriteAppsList.add(appInfo)
                    saveFavorites(); updateDisplayList()
                }.setNegativeButton(textCancel, null).show()
        }
    }

    // ==================== 头部 ====================

    private fun updateHeader() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sel = when (dateLocale) { "EN"->Locale.ENGLISH; "JA"->Locale.JAPANESE; else->Locale.CHINESE }
        val pattern = when (dateLocale) { "EN"->"MMM dd, yyyy (E)"; else->"yyyy年 MM月 dd日 (E)" }
        val now = Date(); tvTime.text = timeFormat.format(now)
        val bat = (getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        tvDateBattery.text = String.format(Locale.getDefault(), "%s | %d%%", SimpleDateFormat(pattern, sel).format(now), bat)
        tvAllApps.text = if (isShowingAllApps) textAllApps else textFavorites
        tvSettings.text = if (isShowingSettings) textClose else textSettingsMenu
    }

    private var batteryReceiver: BroadcastReceiver? = null
    private var packageReceiver: BroadcastReceiver? = null

    // ==================== 索引栏 ====================

    private fun setupIndexBar() {
        indexBarLayout.removeAllViews(); availableIndices.clear()
        POTENTIAL_INDICES.forEach { label ->
            val has = if (label=="★") favoriteAppsList.isNotEmpty() else allAppsList.any { getInitialLetter(it.name)==label }
            if (has) availableIndices.add(label)
        }
        val density = resources.displayMetrics.density; val h = (36*density).toInt()
        availableIndices.forEach { label ->
            indexBarLayout.addView(TextView(this).apply {
                text = label; textSize = 18f; typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER; setTextColor(0xFF000000.toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
            })
        }
        indexBarLayout.setOnTouchListener { v, event ->
            if (!isShowingAllApps) return@setOnTouchListener false
            val y = event.y; val height = v.height
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (height > 0) { val idx = ((y/height)*availableIndices.size).toInt()
                        if (idx in availableIndices.indices) { if (idx!=currentSelectedIndex) { currentSelectedIndex=idx; filterAppsByLabel(availableIndices[idx]) }; applyWaveEffect(idx) }
                    }; true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { resetWaveEffect(); if (event.action==MotionEvent.ACTION_UP) v.performClick(); true }
                else -> false
            }
        }
        indexBarContainer.visibility = if (isShowingAllApps) View.VISIBLE else View.GONE
    }

    private fun applyWaveEffect(sel: Int) {
        val density = resources.displayMetrics.density
        for (i in 0 until indexBarLayout.childCount) {
            val tv = indexBarLayout.getChildAt(i) as TextView; val dist = abs(i-sel); tv.translationX = 0f
            if (dist == 0) {
                tv.setTextColor(0xFFFFFFFF.toInt()); tv.textSize = 28f; tv.typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
                tv.background = GradientDrawable().apply { setColor(0xFF000000.toInt()); cornerRadius = 8f*density }
            } else { tv.setTextColor(0xFF000000.toInt()); tv.textSize = 18f; tv.typeface = globalTypeface ?: Typeface.DEFAULT; tv.background = null }
        }
    }

    private fun resetWaveEffect() {
        for (i in 0 until indexBarLayout.childCount) {
            val tv = indexBarLayout.getChildAt(i) as TextView; tv.translationX = 0f
            tv.setTextColor(0xFF000000.toInt()); tv.textSize = 18f; tv.typeface = globalTypeface ?: Typeface.DEFAULT_BOLD; tv.background = null
        }
        currentSelectedIndex = -1
    }

    private fun filterAppsByLabel(label: String) {
        if (!isShowingAllApps) return; filteredAppsList.clear()
        if (label=="★") filteredAppsList.addAll(favoriteAppsList)
        else allAppsList.forEach { if (getInitialLetter(it.name)==label) filteredAppsList.add(it) }
        appAdapter.updateData(filteredAppsList, false)
    }

    private fun getInitialLetter(name: String): String {
        val t = name.trim(); if (t.isEmpty()) return "#"; val c = t[0]
        if (c.isLetter()) return c.uppercaseChar().toString()
        if (isKorean(c)) {
            if (c.code in 0xAC00..0xD7A3) return arrayOf("ㄱ","ㄲ","ㄴ","ㄷ","ㄸ","ㄹ","ㅁ","ㅂ","ㅃ","ㅅ","ㅆ","ㅇ","ㅈ","ㅉ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ")[(c.code-0xAC00)/28/21]
            if (isKoreanConsonant(c)) return c.toString()
        }
        if (c.toString().matches(Regex("[\\u4e00-\\u9fa5]"))) {
            val s = c.toString()
            for (i in 0 until pinyinBoundaries.size-1) { if (chinaCollator.compare(s, pinyinBoundaries[i])>=0 && chinaCollator.compare(s, pinyinBoundaries[i+1])<0) return pinyinLetters[i] }
            if (chinaCollator.compare(s, pinyinBoundaries.last())>=0) return "Z"
        }
        return "#"
    }

    private fun isKorean(c: Char): Boolean = c.code in 0xAC00..0xD7A3 || c.code in 0x3131..0x318E
    private fun isKoreanConsonant(c: Char): Boolean = c.code in 0x3131..0x314E

    // ==================== 设置页面构建 ====================

    private fun buildSettingsContent() {
        val cats = when (dateLocale) {
            "EN" -> arrayOf("Visual & Typography", "Desktop & Status", "System & Advanced")
            "JA" -> arrayOf("視覚とタイポグラフィ", "デスクトップと状態", "システムと詳細設定")
            else -> arrayOf("排版与视觉设置", "桌面与状态控制", "系统与高级选项")
        }
        cats.forEachIndexed { i, name ->
            settingsContainer.addView(createNavItem(name) {
                navigateTo { when (i) { 0 -> buildVisualSub(); 1 -> buildStateSub(); 2 -> buildAdvancedSub() } }
            })
        }
    }

    private fun createToggleSwitch(isOn: Boolean): FrameLayout {
        val density = resources.displayMetrics.density
        val trackW = (44 * density).toInt()
        val trackH = (24 * density).toInt()
        val thumbSize = (18 * density).toInt()
        val thumbMargin = (3 * density).toInt()

        val track = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(trackW, trackH)
            background = GradientDrawable().apply {
                if (isOn) setColor(0xFF000000.toInt())
                else { setColor(Color.TRANSPARENT); setStroke((2 * density).toInt(), 0xFF000000.toInt()) }
                cornerRadius = trackH / 2f
            }
        }
        track.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isOn) Color.WHITE else 0xFF000000.toInt())
            }
            layoutParams = FrameLayout.LayoutParams(thumbSize, thumbSize).apply {
                gravity = Gravity.CENTER_VERTICAL or if (isOn) Gravity.END else Gravity.START
                marginStart = thumbMargin; marginEnd = thumbMargin
            }
        })
        return track
    }

    private fun createToggleItem(label: String, isOn: Boolean, onToggle: () -> Unit): LinearLayout {
        val density = resources.displayMetrics.density
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isClickable = true
            setOnClickListener { onToggle() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
        }
        row.addView(TextView(this).apply {
            text = label; textSize = allTextSizeSp
            typeface = globalTypeface ?: Typeface.DEFAULT_BOLD; setTextColor(0xFF000000.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(createToggleSwitch(isOn))
        wrapper.addView(row)
        wrapper.addView(View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (2 * density).toInt())
        })
        return wrapper
    }

    private fun createNavItem(label: String, value: String? = null, onClick: () -> Unit): LinearLayout {
        val density = resources.displayMetrics.density
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isClickable = true
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
        }
        row.addView(TextView(this).apply {
            text = label; textSize = allTextSizeSp
            typeface = globalTypeface ?: Typeface.DEFAULT_BOLD; setTextColor(0xFF000000.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (value != null) {
            row.addView(TextView(this).apply {
                text = value; textSize = (allTextSizeSp - 2f).coerceAtLeast(12f)
                typeface = globalTypeface ?: Typeface.DEFAULT; setTextColor(0xFF666666.toInt())
                setPadding((8 * density).toInt(), 0, 0, 0)
            })
        }
        row.addView(TextView(this).apply {
            text = "›"; textSize = allTextSizeSp + 4f; setTextColor(0xFF999999.toInt())
            typeface = globalTypeface ?: Typeface.DEFAULT
            setPadding((4 * density).toInt(), 0, 0, 0)
        })
        wrapper.addView(row)
        wrapper.addView(View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (2 * density).toInt())
        })
        return wrapper
    }

    private fun createRadioItems(items: Array<String>, checked: Int, onSelect: (Int) -> Unit) {
        val density = resources.displayMetrics.density
        items.forEachIndexed { i, item ->
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                isClickable = true; setOnClickListener { onSelect(i) }
                setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            wrapper.addView(TextView(this).apply {
                text = item; textSize = allTextSizeSp
                typeface = globalTypeface ?: Typeface.DEFAULT_BOLD; setTextColor(0xFF000000.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (i == checked) {
                wrapper.addView(TextView(this).apply {
                    text = "✓"; textSize = allTextSizeSp + 2f; setTextColor(0xFF000000.toInt())
                    typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
                })
            }
            settingsContainer.addView(wrapper)
            settingsContainer.addView(View(this).apply {
                setBackgroundColor(0xFF000000.toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (2 * density).toInt())
            })
        }
    }

    // ---------- 排版与视觉 ----------

    private fun buildVisualSub() {
        val fontUri = prefs.getString(KEY_CUSTOM_FONT_URI, null)
        val fontName = if (fontUri != null) getFileNameFromUri(Uri.parse(fontUri)) else "默认"
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Custom Font" else "自定义字体", fontName) { navigateTo { buildFontSub() } })
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Icon Pack" else "图标包设置") { navigateTo { buildIconPackSub() } })
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Icon Size" else "图标大小设置") { navigateTo { buildIconSizeSub() } })
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Text Size" else "文字大小设置") { navigateTo { buildTextSizeSub() } })
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Grid Columns" else "设置收藏列数", "${favColumns}列") { navigateTo { buildColumnSub() } })
        settingsContainer.addView(createToggleItem(
            if (dateLocale == "EN") "Icon Optimization" else "图标优化",
            isIconOptimizationEnabled
        ) { toggleIconOptimization() })
    }

    private fun buildFontSub() {
        val fontUri = prefs.getString(KEY_CUSTOM_FONT_URI, null)
        val fontName = if (fontUri != null) getFileNameFromUri(Uri.parse(fontUri)) else "默认"
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Select Font File" else "选择字体文件", fontName) {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
            fontPicker.launch(Intent.createChooser(intent, "选择文件管理器"))
        })
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Reset to Default" else "恢复系统默认") {
            prefs.edit { remove(KEY_CUSTOM_FONT_URI) }; applyCustomFont(null)
            refreshCurrent()
        })
    }

    private fun buildIconSizeSub() {
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Favorites Grid Icons" else "收藏网格图标") { navigateTo { buildIconSizePicker(true) } })
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "All Apps Icons" else "所有应用图标") { navigateTo { buildIconSizePicker(false) } })
    }

    private fun buildIconSizePicker(isFav: Boolean) {
        val options = arrayOf("32dp", "48dp", "64dp", "80dp", "96dp", "120dp")
        val sizes = intArrayOf(32, 48, 64, 80, 96, 120)
        val cur = if (isFav) favIconSizeDp else allIconSizeDp
        createRadioItems(options, sizes.indexOfFirst { it == cur }) { i ->
            if (isFav) { favIconSizeDp = sizes[i]; prefs.edit { putInt(KEY_FAV_ICON_SIZE, sizes[i]) } }
            else { allIconSizeDp = sizes[i]; prefs.edit { putInt(KEY_ALL_ICON_SIZE, sizes[i]) } }
            updateAdapterSizes(); refreshCurrent()
        }
    }

    private fun buildTextSizeSub() {
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Favorites Text" else "收藏网格文字") { navigateTo { buildTextSizePicker(true) } })
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "All Apps Text" else "所有应用文字") { navigateTo { buildTextSizePicker(false) } })
    }

    private fun buildTextSizePicker(isFav: Boolean) {
        val options = arrayOf("12sp", "14sp", "16sp", "18sp", "20sp")
        val sizes = floatArrayOf(12f, 14f, 16f, 18f, 20f)
        val cur = if (isFav) favTextSizeSp else allTextSizeSp
        createRadioItems(options, sizes.indexOfFirst { it == cur }) { i ->
            if (isFav) { favTextSizeSp = sizes[i]; prefs.edit { putFloat(KEY_FAV_TEXT_SIZE, sizes[i]) } }
            else { allTextSizeSp = sizes[i]; prefs.edit { putFloat(KEY_ALL_TEXT_SIZE, sizes[i]) } }
            updateAdapterSizes(); refreshCurrent()
        }
    }

    private fun buildColumnSub() {
        createRadioItems(arrayOf("1列", "2列", "3列", "4列", "5列", "6列", "7列", "8列"), favColumns - 1) { i ->
            favColumns = i + 1; prefs.edit { putInt(KEY_COLUMNS, favColumns) }
            filterFavorites(); refreshCurrent()
        }
    }

    // ---------- 桌面与状态 ----------

    private fun buildStateSub() {
        settingsContainer.addView(createToggleItem(
            if (dateLocale == "EN") "Show App Names" else "显示应用名称",
            !isAppNamesHidden
        ) { toggleAppNames() })
        settingsContainer.addView(createToggleItem(
            if (dateLocale == "EN") "Show Status Bar" else "显示系统状态栏",
            !isStatusBarHidden
        ) { toggleStatusBar() })
    }

    // ---------- 系统与高级 ----------

    private fun buildAdvancedSub() {
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Language" else "语言设置", when (dateLocale) { "EN" -> "English"; "JA" -> "日本語"; else -> "简体中文" }) { navigateTo { buildLocaleSub() } })
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Restart" else "重启桌面") { restartLauncher() })
        settingsContainer.addView(createNavItem(if (dateLocale == "EN") "Default Launcher" else "默认桌面设置") { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) })
    }

    private fun buildLocaleSub() {
        val options = arrayOf("简体中文 (ZH)", "English (EN)", "日本語 (JA)")
        createRadioItems(options, when (dateLocale) { "EN" -> 1; "JA" -> 2; else -> 0 }) { i ->
            dateLocale = when (i) { 1 -> "EN"; 2 -> "JA"; else -> "ZH" }
            prefs.edit { putString(KEY_LOCALE, dateLocale) }
            updateHeader(); refreshCurrent()
        }
    }

    // ---------- 图标包 ----------

    private fun buildIconPackSub() {
        tempIconPackNames.clear(); tempIconPackPkgs.clear()
        val allPacks = iconPackManager.getAvailableIconPacks()
        for (pkg in loadIconPacksFromPrefs()) {
            tempIconPackNames.add(allPacks.entries.find { it.value == pkg }?.key ?: pkg)
            tempIconPackPkgs.add(pkg)
        }
        rebuildIconPackList()
    }

    private fun rebuildIconPackList() {
        val density = resources.displayMetrics.density
        settingsContainer.removeAllViews()

        if (tempIconPackPkgs.isEmpty()) {
            settingsContainer.addView(TextView(this).apply {
                text = if (dateLocale == "ZH") "（未选择任何图标包）" else "(No icon packs selected)"
                textSize = allTextSizeSp; typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
                setTextColor(0xFF000000.toInt())
                setPadding(0, (8 * density).toInt(), 0, (16 * density).toInt())
            })
        } else {
            settingsContainer.addView(TextView(this).apply {
                text = if (dateLocale == "ZH") "▲▼ 调整顺序  ✕ 移除" else "▲▼ reorder  ✕ remove"
                textSize = (allTextSizeSp - 2f).coerceAtLeast(12f); typeface = globalTypeface ?: Typeface.DEFAULT
                setTextColor(0xFF666666.toInt())
                setPadding(0, 0, 0, (12 * density).toInt())
            })
            for (i in tempIconPackNames.indices) {
                settingsContainer.addView(createInlineIconPackRow(i))
                settingsContainer.addView(View(this).apply {
                    setBackgroundColor(0xFF000000.toInt())
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (2 * density).toInt())
                })
            }
        }
        settingsContainer.addView(TextView(this).apply {
            text = if (dateLocale == "ZH") "+ 添加图标包" else "+ Add Icon Pack"
            textSize = allTextSizeSp; setTextColor(0xFF000000.toInt())
            typeface = globalTypeface ?: Typeface.DEFAULT_BOLD; isClickable = true
            setPadding(0, (16 * density).toInt(), 0, (12 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * density).toInt() }
            setOnClickListener { showInlineAddIconPack() }
        })
        if (tempIconPackPkgs.isNotEmpty()) {
            settingsContainer.addView(TextView(this).apply {
                text = if (dateLocale == "ZH") "清除全部" else "Clear All"
                textSize = allTextSizeSp; setTextColor(0xFFCC0000.toInt())
                typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
                setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
                setOnClickListener { tempIconPackNames.clear(); tempIconPackPkgs.clear(); applyIconPackChanges(); rebuildIconPackList() }
            })
        }
    }

    private fun createInlineIconPackRow(index: Int): LinearLayout {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
        }
        row.addView(TextView(this).apply {
            text = "${index + 1}."; textSize = allTextSizeSp
            typeface = globalTypeface ?: Typeface.DEFAULT_BOLD; minWidth = (36 * density).toInt()
        })
        row.addView(TextView(this).apply {
            text = tempIconPackNames[index]; textSize = allTextSizeSp; maxLines = 1
            typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (4 * density).toInt(); marginEnd = (4 * density).toInt()
            }
        })
        if (index > 0) row.addView(TextView(this).apply {
            text = "▲"; textSize = allTextSizeSp; typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
            isClickable = true; setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            setOnClickListener { swapIconPacks(index, index - 1); applyIconPackChanges(); rebuildIconPackList() }
        })
        if (index < tempIconPackPkgs.size - 1) row.addView(TextView(this).apply {
            text = "▼"; textSize = allTextSizeSp; typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
            isClickable = true; setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            setOnClickListener { swapIconPacks(index, index + 1); applyIconPackChanges(); rebuildIconPackList() }
        })
        row.addView(TextView(this).apply {
            text = "✕"; textSize = allTextSizeSp; setTextColor(0xFFCC0000.toInt())
            typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
            isClickable = true; setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            setOnClickListener { tempIconPackNames.removeAt(index); tempIconPackPkgs.removeAt(index); applyIconPackChanges(); rebuildIconPackList() }
        })
        return row
    }

    private fun swapIconPacks(a: Int, b: Int) {
        val tmpN = tempIconPackNames[a]; tempIconPackNames[a] = tempIconPackNames[b]; tempIconPackNames[b] = tmpN
        val tmpP = tempIconPackPkgs[a]; tempIconPackPkgs[a] = tempIconPackPkgs[b]; tempIconPackPkgs[b] = tmpP
    }

    private fun showInlineAddIconPack() {
        val allPacks = iconPackManager.getAvailableIconPacks()
        val currentPkgs = tempIconPackPkgs.toSet()
        val available = allPacks.filter { it.value !in currentPkgs }
        settingsContainer.removeAllViews()
        if (available.isEmpty()) {
            val density = resources.displayMetrics.density
            settingsContainer.addView(TextView(this).apply {
                text = if (dateLocale == "ZH") "没有更多可添加的图标包" else "No more icon packs"
                textSize = allTextSizeSp; typeface = globalTypeface ?: Typeface.DEFAULT_BOLD
                setTextColor(0xFF000000.toInt())
                setPadding(0, (16 * density).toInt(), 0, (16 * density).toInt())
            })
        } else {
            for ((name, pkg) in available) {
                settingsContainer.addView(createNavItem(name) {
                    tempIconPackNames.add(name); tempIconPackPkgs.add(pkg); applyIconPackChanges()
                    settingsStack.removeAt(settingsStack.size - 1)
                    rebuildIconPackList()
                })
            }
        }
    }

    private fun applyIconPackChanges() {
        saveIconPacksToPrefs(tempIconPackPkgs); iconPackManager.setIconPacks(tempIconPackPkgs)
        iconCache.evictAll()
        Thread { clearDiskCache() }.start()
        loadAppsAsync()
    }
}

class AdminReceiver : android.app.admin.DeviceAdminReceiver()