package com.palma.minimal.launcher

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.*

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
    private var itemHeight: Int = 0

    fun updateData(newApps: List<AppInfo>, isFavorites: Boolean) {
        apps = newApps.toMutableList()
        isFavoritesView = isFavorites
        notifyDataSetChanged()
    }

    fun setItemHeight(height: Int) {
        if (this.itemHeight != height) {
            this.itemHeight = height
            notifyDataSetChanged()
        }
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
        holder.ivAppIcon.setImageDrawable(app.icon)

        if (isFavoritesView && itemHeight > 0) {
            holder.itemView.layoutParams.height = itemHeight
        } else {
            holder.itemView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
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
