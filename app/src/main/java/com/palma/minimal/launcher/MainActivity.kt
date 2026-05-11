package com.palma.minimal.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvDateBattery: TextView
    private lateinit var tvAllApps: TextView
    private lateinit var recyclerViewApps: RecyclerView
    private lateinit var btnRefreshScreen: View
    private lateinit var ivSettings: ImageView
    private lateinit var indexBarLayout: LinearLayout
    private lateinit var indexBarContainer: View
    
    private lateinit var appAdapter: AppAdapter
    private var allAppsList = mutableListOf<AppInfo>()
    private var favoriteAppsList = mutableListOf<AppInfo>()
    private var filteredAppsList = mutableListOf<AppInfo>()
    
    private lateinit var prefs: SharedPreferences
    private var isShowingAllApps = false
    private var currentSelectedIndex = -1
    private var availableIndices = mutableListOf<String>()
    
    private var favColumns = 2
    private var isAnimationEnabled = false
    private var dateLocale = "KO"

    companion object {
        private const val TAG = "PalmaLauncher"
        private const val PREFS_NAME = "LauncherPrefs"
        private const val KEY_FAVORITES = "favorites_list"
        private const val KEY_COLUMNS = "fav_columns"
        private const val KEY_ANIMATION = "animation_enabled"
        private const val KEY_LOCALE = "date_locale"
        private val POTENTIAL_INDICES = listOf("★", "ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ", 
                                              "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#")
    }

    // Localized Strings
    private val textFavorites: String get() = when (dateLocale) { "EN" -> "Favorites"; "JA" -> "お気に入り"; else -> "즐겨찾기" }
    private val textAllApps: String get() = when (dateLocale) { "EN" -> "All Apps"; "JA" -> "すべてのアプリ"; else -> "모든 앱" }
    private val textSettings: String get() = when (dateLocale) { "EN" -> "Launcher Settings"; "JA" -> "ランチャー設定"; else -> "런처 설정" }
    private val textClose: String get() = when (dateLocale) { "EN" -> "Close"; "JA" -> "閉じる"; else -> "닫기" }
    private val textConfirm: String get() = when (dateLocale) { "EN" -> "OK"; "JA" -> "確認"; else -> "확인" }
    private val textCancel: String get() = when (dateLocale) { "EN" -> "Cancel"; "JA" -> "キャンセル"; else -> "취소" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        favColumns = prefs.getInt(KEY_COLUMNS, 2)
        isAnimationEnabled = prefs.getBoolean(KEY_ANIMATION, false)
        dateLocale = prefs.getString(KEY_LOCALE, "KO") ?: "KO"

        initViews()
        setupRecyclerView()
        loadApps()
        updateHeader()
        setupListeners()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Wait for layout to finish updating before recalculating height
        recyclerViewApps.post {
            if (!isShowingAllApps) {
                calculateItemHeight()
                appAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun initViews() {
        tvTime = findViewById(R.id.tvTime)
        tvDateBattery = findViewById(R.id.tvDateBattery)
        tvAllApps = findViewById(R.id.tvAllApps)
        recyclerViewApps = findViewById(R.id.recyclerViewApps)
        btnRefreshScreen = findViewById(R.id.btnRefreshScreen)
        ivSettings = findViewById(R.id.ivSettings)
        indexBarLayout = findViewById(R.id.indexBarLayout)
        indexBarContainer = findViewById(R.id.indexBarContainer)
        
        tvAllApps.text = textFavorites
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter(mutableListOf(), this::onAppClicked, this::onAppLongClicked, this::onOrderChanged)
        recyclerViewApps.adapter = appAdapter
        recyclerViewApps.itemAnimator = null
        updateDisplayList()

        recyclerViewApps.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                recyclerViewApps.viewTreeObserver.removeOnGlobalLayoutListener(this)
                calculateItemHeight()
            }
        })

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (isShowingAllApps) return false
                appAdapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = !isShowingAllApps
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerViewApps)
    }

    private fun setupIndexBar() {
        indexBarLayout.removeAllViews()
        availableIndices.clear()
        POTENTIAL_INDICES.forEach { label ->
            val hasApp = if (label == "★") {
                favoriteAppsList.isNotEmpty()
            } else {
                allAppsList.any { app ->
                    val firstChar = app.name.firstOrNull() ?: return@any false
                    val initial = getInitialSound(firstChar)
                    if (label == "#") {
                        !initial[0].isLetter() && !isKorean(firstChar)
                    } else if (label.length == 1 && isKoreanConsonant(label[0])) {
                        initial == label
                    } else {
                        app.name.uppercase().startsWith(label)
                    }
                }
            }
            if (hasApp) availableIndices.add(label)
        }

        availableIndices.forEach { label ->
            val tv = TextView(this).apply {
                text = label
                textSize = 24f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(0xFF888888.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            indexBarLayout.addView(tv)
        }

        indexBarLayout.setOnTouchListener { v, event ->
            if (!isShowingAllApps) return@setOnTouchListener false
            val y = event.y
            val height = v.height
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (height > 0) {
                        val index = ((y / height) * availableIndices.size).toInt()
                        if (index >= 0 && index < availableIndices.size) {
                            if (index != currentSelectedIndex) {
                                currentSelectedIndex = index
                                filterAppsByLabel(availableIndices[index])
                            }
                            applyWaveEffect(index)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resetWaveEffect()
                    true
                }
                else -> false
            }
        }
        indexBarContainer.visibility = if (isShowingAllApps) View.VISIBLE else View.GONE
    }

    private fun applyWaveEffect(selectedIndex: Int) {
        for (i in 0 until indexBarLayout.childCount) {
            val tv = indexBarLayout.getChildAt(i) as TextView
            val dist = abs(i - selectedIndex)
            
            if (isAnimationEnabled) {
                val translationX = when (dist) {
                    0 -> -150f
                    1 -> -90f
                    2 -> -45f
                    3 -> -22f
                    else -> 0f
                }
                tv.translationX = translationX
            } else {
                tv.translationX = 0f
            }
            
            if (dist == 0) {
                tv.setTextColor(0xFFFFFFFF.toInt())
                tv.textSize = 32f
            } else {
                tv.setTextColor(0xFF888888.toInt())
                tv.textSize = 24f
            }
        }
    }

    private fun resetWaveEffect() {
        for (i in 0 until indexBarLayout.childCount) {
            val tv = indexBarLayout.getChildAt(i) as TextView
            tv.translationX = 0f
            tv.setTextColor(0xFF888888.toInt())
            tv.textSize = 24f
        }
        currentSelectedIndex = -1
    }

    private fun filterAppsByLabel(label: String) {
        if (!isShowingAllApps) return
        filteredAppsList.clear()
        if (label == "★") {
            filteredAppsList.addAll(favoriteAppsList)
        } else {
            allAppsList.forEach { app ->
                val firstChar = app.name.firstOrNull() ?: return@forEach
                val initial = getInitialSound(firstChar)
                val match = if (label == "#") {
                    !initial[0].isLetter() && !isKorean(firstChar)
                } else if (label.length == 1 && isKoreanConsonant(label[0])) {
                    initial == label
                } else {
                    app.name.uppercase().startsWith(label)
                }
                if (match) filteredAppsList.add(app)
            }
        }
        appAdapter.updateData(filteredAppsList, false)
    }

    private fun getInitialSound(c: Char): String {
        val KOREAN_BEGIN = 0xAC00
        val KOREAN_END = 0xD7A3
        val CONSONANTS = arrayOf("ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")
        if (c.code in KOREAN_BEGIN..KOREAN_END) {
            val index = (c.code - KOREAN_BEGIN) / 28 / 21
            return CONSONANTS[index]
        }
        return c.uppercaseChar().toString()
    }

    private fun isKorean(c: Char): Boolean = c.code in 0xAC00..0xD7A3 || c.code in 0x3131..0x318E
    private fun isKoreanConsonant(c: Char): Boolean = c.code in 0x3131..0x314E

    private fun calculateItemHeight() {
        if (!isShowingAllApps) {
            val totalHeight = recyclerViewApps.height
            if (totalHeight <= 0) return
            val rows = 4
            appAdapter.setItemHeight(totalHeight / rows)
        }
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pm: PackageManager = packageManager
        val apps: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        allAppsList.clear()
        for (app in apps) {
            val packageName = app.activityInfo.packageName
            if (packageName == this.packageName) continue
            val className = app.activityInfo.name
            val name = app.loadLabel(pm).toString()
            val icon = app.loadIcon(pm)
            allAppsList.add(AppInfo(name, packageName, className, icon))
        }
        allAppsList.sortBy { it.name.lowercase() }
        filterFavorites()
    }

    private fun filterFavorites() {
        val favString = prefs.getString(KEY_FAVORITES, "") ?: ""
        val favPackageList = if (favString.isEmpty()) mutableListOf() else favString.split(",").toMutableList()
        favoriteAppsList.clear()
        favPackageList.forEach { pkg ->
            allAppsList.find { it.packageName == pkg }?.let { favoriteAppsList.add(it) }
        }
        val limit = favColumns * 4
        if (favoriteAppsList.size > limit) {
            favoriteAppsList = favoriteAppsList.take(limit).toMutableList()
        }
        if (favoriteAppsList.isEmpty() && allAppsList.isNotEmpty()) {
            favoriteAppsList.addAll(allAppsList.take(limit.coerceAtMost(allAppsList.size)))
            saveFavorites()
        }
        updateDisplayList()
    }

    private fun saveFavorites() {
        val favString = favoriteAppsList.joinToString(",") { it.packageName }
        prefs.edit().putString(KEY_FAVORITES, favString).apply()
    }

    private fun updateDisplayList() {
        if (isShowingAllApps) {
            recyclerViewApps.layoutManager = LinearLayoutManager(this)
            appAdapter.updateData(allAppsList, false)
            setupIndexBar()
            indexBarContainer.visibility = View.VISIBLE
            tvAllApps.text = textAllApps
            recyclerViewApps.setPadding(24, 0, 60, 0)
        } else {
            recyclerViewApps.layoutManager = GridLayoutManager(this, favColumns)
            calculateItemHeight()
            appAdapter.updateData(favoriteAppsList, true)
            indexBarContainer.visibility = View.GONE
            tvAllApps.text = textFavorites
            recyclerViewApps.setPadding(24, 0, 24, 0)
        }
    }

    private fun onOrderChanged(newList: List<AppInfo>) {
        if (!isShowingAllApps) {
            favoriteAppsList = newList.toMutableList()
            saveFavorites()
        }
    }

    private fun updateHeader() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val selectedLocale = when (dateLocale) {
            "EN" -> Locale.ENGLISH
            "JA" -> Locale.JAPANESE
            else -> Locale.KOREAN
        }
        val pattern = when (dateLocale) {
            "EN" -> "MMM dd, yyyy (E)"
            "JA" -> "yyyy年 MM月 dd日 (E)"
            else -> "yyyy년 MM월 dd일 (E)"
        }
        val dateFormat = SimpleDateFormat(pattern, selectedLocale)
        val currentTime = Date()
        tvTime.text = timeFormat.format(currentTime)
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        tvDateBattery.text = String.format("%s | %d%%", dateFormat.format(currentTime), batLevel)
        tvAllApps.text = if (isShowingAllApps) textAllApps else textFavorites
    }

    private fun setupListeners() {
        tvAllApps.setOnClickListener {
            isShowingAllApps = !isShowingAllApps
            updateDisplayList()
        }
        ivSettings.setOnClickListener { showSettingsMenu() }
        btnRefreshScreen.setOnClickListener { window.decorView.invalidate() }
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_TICK)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { updateHeader() }
        }, filter)
    }

    private fun showSettingsMenu() {
        val options = when (dateLocale) {
            "EN" -> arrayOf("Grid Settings", "Animation Settings", "Language Settings", "App Info", "Default Launcher", "Uninstall Launcher", "Restart Launcher", "Privacy Policy", "Terms of Service", "GitHub")
            "JA" -> arrayOf("グリッド設定", "アニメーション設定", "言語設定", "アプリ情報", "デフォルトランチャー", "アンインストール", "再起動", "プライバシーポリシー", "利用規約", "GitHub")
            else -> arrayOf("즐겨찾기 배치 설정", "애니메이션 설정", "언어 설정", "앱 정보", "기본 런처 설정", "런처 삭제", "런처 재시작", "개인정보취급방침", "서비스이용약관", "GitHub 저장소")
        }
        AlertDialog.Builder(this)
            .setTitle(textSettings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showColumnSettings()
                    1 -> showAnimationSettings()
                    2 -> showLocaleSettings()
                    3 -> openAppInfo()
                    4 -> openDefaultLauncherSettings()
                    5 -> uninstallLauncher()
                    6 -> restartLauncher()
                    7 -> openUrl("https://github.com/bc1qwerty/e-ink-minimal-launcher/blob/main/PRIVACY.md")
                    8 -> openUrl("https://github.com/bc1qwerty/e-ink-minimal-launcher/blob/main/TERMS.md")
                    9 -> openUrl("https://github.com/bc1qwerty/e-ink-minimal-launcher")
                }
            }
            .setNegativeButton(textClose, null)
            .show()
    }

    private fun showLocaleSettings() {
        val title = when (dateLocale) { "EN" -> "Language Settings"; "JA" -> "言語設定"; else -> "언어 설정" }
        val options = arrayOf("한국어 (KO)", "English (EN)", "日本語 (JA)")
        val checkedItem = when (dateLocale) {
            "EN" -> 1
            "JA" -> 2
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                dateLocale = when (which) {
                    1 -> "EN"
                    2 -> "JA"
                    else -> "KO"
                }
                prefs.edit().putString(KEY_LOCALE, dateLocale).apply()
                updateHeader()
                dialog.dismiss()
            }
            .show()
    }

    private fun showColumnSettings() {
        val title = when (dateLocale) { "EN" -> "Grid Settings"; "JA" -> "グリッド設定"; else -> "즐겨찾기 배치 선택" }
        val cols = when (dateLocale) {
            "EN" -> arrayOf("1 Column (Max 4)", "2 Columns (Max 8)", "3 Columns (Max 12)", "4 Columns (Max 16)", "5 Columns (Max 20)")
            "JA" -> arrayOf("1列 (最大4個)", "2列 (最大8個)", "3列 (最大12個)", "4列 (最大16個)", "5列 (最大20個)")
            else -> arrayOf("1단 (최대 4개)", "2단 (최대 8개)", "3단 (최대 12개)", "4단 (최대 16개)", "5단 (최대 20개)")
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(cols, favColumns - 1) { dialog, which ->
                favColumns = which + 1
                prefs.edit().putInt(KEY_COLUMNS, favColumns).apply()
                filterFavorites()
                dialog.dismiss()
            }
            .show()
    }

    private fun showAnimationSettings() {
        val title = when (dateLocale) { "EN" -> "Animation Settings"; "JA" -> "アニメーション設定"; else -> "애니메이션 설정" }
        val options = when (dateLocale) {
            "EN" -> arrayOf("Animation Off (Low Spec)", "Animation On (High Spec)")
            "JA" -> arrayOf("アニメーション オフ (低スペック用)", "アニメーション オン (高スペック用)")
            else -> arrayOf("애니메이션 끄기 (기본/저사양 권장)", "애니메이션 켜기 (고성능/Y700 권장)")
        }
        val checkedItem = if (!isAnimationEnabled) 0 else 1
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                isAnimationEnabled = (which == 1)
                prefs.edit().putBoolean(KEY_ANIMATION, isAnimationEnabled).apply()
                val toastMsg = when (dateLocale) {
                    "EN" -> if(isAnimationEnabled) "Animation On" else "Animation Off"
                    "JA" -> if(isAnimationEnabled) "アニメーション オン" else "アニメーション オフ"
                    else -> if(isAnimationEnabled) "애니메이션이 켜졌습니다." else "애니메이션이 꺼졌습니다."
                }
                Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    private fun openAppInfo() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:" + packageName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            val msg = when (dateLocale) { "EN" -> "Cannot open settings"; "JA" -> "設定を開けません"; else -> "설정을 열 수 없습니다." }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDefaultLauncherSettings() { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    private fun uninstallLauncher() { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName))) }
    private fun restartLauncher() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } 
        catch (e: Exception) { 
            val msg = when (dateLocale) { "EN" -> "Cannot open URL"; "JA" -> "URLを開けません"; else -> "URL을 열 수 없습니다." }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() 
        }
    }
    private fun onAppClicked(appInfo: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(appInfo.packageName, appInfo.className)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(intent)
        } catch (e: Exception) {
            packageManager.getLaunchIntentForPackage(appInfo.packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(it)
                } catch (e2: Exception) {
                    val msg = when (dateLocale) { "EN" -> "Cannot launch app"; "JA" -> "アプリを起動できません"; else -> "앱을 실행할 수 없습니다." }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                val msg = when (dateLocale) { "EN" -> "Cannot launch app"; "JA" -> "アプリを起動できません"; else -> "앱을 실행할 수 없습니다." }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun onAppLongClicked(appInfo: AppInfo) {
        if (isShowingAllApps) {
            val isFav = favoriteAppsList.any { it.packageName == appInfo.packageName }
            val actionText = if (isFav) {
                when (dateLocale) { "EN" -> "Remove from Favorites"; "JA" -> "お気に入りから削除"; else -> "즐겨찾기에서 제거" }
            } else {
                when (dateLocale) { "EN" -> "Add to Favorites"; "JA" -> "お気に入りに追加"; else -> "즐겨찾기에 추가" }
            }
            val msg = when (dateLocale) { "EN" -> "$actionText?"; "JA" -> "${actionText}しますか？"; else -> "${actionText}하시겠습니까?" }
            
            AlertDialog.Builder(this)
                .setTitle(appInfo.name)
                .setMessage(msg)
                .setPositiveButton(textConfirm) { _, _ ->
                    if (isFav) {
                        favoriteAppsList.removeAll { it.packageName == appInfo.packageName }
                        val tMsg = when(dateLocale) { "EN" -> "Removed"; "JA" -> "削除しました"; else -> "제거됨" }
                        Toast.makeText(this, tMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        if (favoriteAppsList.size < favColumns * 4) {
                            favoriteAppsList.add(appInfo)
                            val tMsg = when(dateLocale) { "EN" -> "Added"; "JA" -> "追加しました"; else -> "추가됨" }
                            Toast.makeText(this, tMsg, Toast.LENGTH_SHORT).show()
                        } else {
                            val tMsg = when(dateLocale) { "EN" -> "Favorites full"; "JA" -> "お気に入りがいっぱいです"; else -> "즐겨찾기가 가득 찼습니다." }
                            Toast.makeText(this, tMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    saveFavorites()
                    updateDisplayList()
                }
                .setNegativeButton(textCancel, null)
                .show()
        }
    }
}
