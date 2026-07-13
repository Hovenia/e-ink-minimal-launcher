package com.palma.minimal.launcher

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

data class AppInfo(
    val name: String,
    val packageName: String,
    val className: String,
    val icon: Drawable? = null
)

class AppAdapter(
    private var apps: MutableList<AppInfo>,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit,
    private val onOrderChanged: (List<AppInfo>) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    companion object {
        const val VIEW_TYPE_LIST = 1
        const val VIEW_TYPE_GRID = 2
    }

    private var isFavoritesView = true
    private var favIconSizePx: Int = 150
    private var allIconSizePx: Int = 120
    private var favTextSizeSp: Float = 14f
    private var allTextSizeSp: Float = 16f
    private var hideAppNames: Boolean = false
    private var customTypeface: Typeface? = null

    fun updateData(newApps: List<AppInfo>, isFavorites: Boolean) {
        apps = newApps.toMutableList()
        isFavoritesView = isFavorites
        notifyDataSetChanged()
    }

    @Suppress("UNUSED_PARAMETER")
    fun setSizesAndFont(
        favIconPx: Int,
        allIconPx: Int,
        favTextSp: Float,
        allTextSp: Float,
        hideNames: Boolean,
        typeface: Typeface?
    ) {
        this.favIconSizePx = favIconPx
        this.allIconSizePx = allIconPx
        this.favTextSizeSp = favTextSp
        this.allTextSizeSp = allTextSp
        this.hideAppNames = hideNames
        this.customTypeface = typeface
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isFavoritesView) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(apps, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(apps, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onOrderChanged(apps)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_GRID) R.layout.item_app_grid else R.layout.item_app_list
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.tvAppName.text = app.name
        holder.itemView.background = null

        val isGrid = isFavoritesView
        val targetIconSize = if (isGrid) favIconSizePx else allIconSizePx

        holder.ivAppIcon.setImageDrawable(null)
        holder.ivAppIcon.layoutParams.width = targetIconSize
        holder.ivAppIcon.layoutParams.height = targetIconSize
        holder.ivAppIcon.requestLayout()
        holder.ivAppIcon.setImageDrawable(app.icon)

        holder.tvAppName.textSize = if (isGrid) favTextSizeSp else allTextSizeSp
        holder.tvAppName.visibility = if (hideAppNames) View.GONE else View.VISIBLE
        holder.tvAppName.maxLines = 2
        holder.tvAppName.ellipsize = TextUtils.TruncateAt.END

        if (customTypeface != null) {
            holder.tvAppName.typeface = customTypeface
        } else {
            holder.tvAppName.setTypeface(null, Typeface.BOLD)
        }

        val density = holder.itemView.context.resources.displayMetrics.density

        if (isGrid) {
            val pad = (12 * density).toInt()
            holder.itemView.setPadding(pad, pad, pad, pad)

            val currentTextSp = favTextSizeSp
            val lineHeight = (currentTextSp * density * 1.4f).toInt()
            val textAreaHeight = if (hideAppNames) 0 else lineHeight * 2
            holder.tvAppName.layoutParams.height = textAreaHeight

            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                targetIconSize + textAreaHeight + pad * 2
            )
        } else {
            val padH = (16 * density).toInt()
            val padV = (12 * density).toInt()
            holder.itemView.setPadding(padH, padV, padH, padV)
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            holder.tvAppName.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            holder.tvAppName.maxLines = 1
        }

        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener {
            onLongClick(app)
            true
        }
    }

    override fun getItemCount(): Int = apps.size

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
    }
}