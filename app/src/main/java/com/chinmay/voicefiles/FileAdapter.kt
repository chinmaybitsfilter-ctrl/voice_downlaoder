package com.chinmay.voicefiles

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FileAdapter(
    private val onOpen: (FileItem) -> Unit,
    private val onLongPress: (FileItem) -> Unit,
) : RecyclerView.Adapter<FileAdapter.Holder>() {

    private var items: List<FileItem> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<FileItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val badge: TextView = view.findViewById(R.id.badge)
        private val name: TextView = view.findViewById(R.id.name)
        private val meta: TextView = view.findViewById(R.id.meta)

        fun bind(item: FileItem) {
            val ctx = itemView.context
            val density = ctx.resources.displayMetrics.density
            badge.text = item.ext.take(4).ifEmpty { "?" }
            badge.background = GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(item.kind.color)
            }
            name.text = item.name
            val parts = mutableListOf(
                Formatter.formatShortFileSize(ctx, item.size),
                DateUtils.getRelativeTimeSpanString(
                    item.modified, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString(),
            )
            if (item.folder.isNotEmpty()) parts += "📁 " + item.folder
            meta.text = parts.joinToString("  ·  ")
            itemView.setOnClickListener { onOpen(item) }
            itemView.setOnLongClickListener { onLongPress(item); true }
        }
    }
}
