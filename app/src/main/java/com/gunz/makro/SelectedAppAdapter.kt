package com.gunz.makro

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SelectedAppAdapter(
    private val packageManager: PackageManager,
    private val apps: MutableList<TargetApp>,
    private val onAppClick: (TargetApp) -> Unit,
    private val onAddClick: () -> Unit,
    private val onOrderChanged: (List<TargetApp>) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_APP = 0
        private const val TYPE_ADD = 1
    }

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivSelectedIcon)
        val label: TextView = view.findViewById(R.id.tvSelectedLabel)
    }

    inner class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val button: View = view.findViewById(R.id.btnAddApp)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == apps.size) TYPE_ADD else TYPE_APP
    }

    override fun getItemCount(): Int = apps.size + 1 // +1 untuk tombol tambah

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ADD) {
            AddViewHolder(inflater.inflate(R.layout.item_add_app, parent, false))
        } else {
            AppViewHolder(inflater.inflate(R.layout.item_selected_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AddViewHolder) {
            holder.button.setOnClickListener { onAddClick() }
            return
        }
        if (holder is AppViewHolder) {
            val app = apps[position]
            holder.label.text = app.label
            try {
                holder.icon.setImageDrawable(packageManager.getApplicationIcon(app.packageName))
            } catch (e: PackageManager.NameNotFoundException) {
                holder.icon.setImageDrawable(null)
            }
            holder.itemView.setOnClickListener { onAppClick(app) }
        }
    }

    fun isDraggable(position: Int): Boolean = position < apps.size

    fun onMoveItems(fromPosition: Int, toPosition: Int) {
        if (fromPosition >= apps.size || toPosition >= apps.size) return
        val moved = apps.removeAt(fromPosition)
        apps.add(toPosition, moved)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun onDragFinished() {
        onOrderChanged(apps.toList())
    }

    fun setApps(newApps: List<TargetApp>) {
        apps.clear()
        apps.addAll(newApps)
        notifyDataSetChanged()
    }
}
