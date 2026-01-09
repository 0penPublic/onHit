package mba.vm.onhit.ui.adapter

import android.annotation.SuppressLint
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.ui.NdefFileItem

class NdefFileAdapter(
    private val items: List<NdefFileItem>,
    private val onItemClick: (NdefFileItem) -> Unit,
    private val onItemLongClick: (NdefFileItem) -> Unit
)
    : RecyclerView.Adapter<NdefViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NdefViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return NdefViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: NdefViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        val size = item.size
        val dateStr = DateFormat.format("yyyy-MM-dd HH:mm:ss", item.lastModified)
        holder.subtitle.text = if (item.isDirectory) {
            "$dateStr"
        } else {
            "$size bytes | $dateStr"
        }
        if (item.isDirectory) {
            holder.icon.setImageResource(R.drawable.baseline_folder_24)
        }
        holder.itemView.setOnClickListener {
            val position = holder.absoluteAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onItemClick(items[position])
            }
        }
        holder.itemView.setOnLongClickListener {
            val position = holder.absoluteAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onItemLongClick(items[position])
            }
            return@setOnLongClickListener true
        }
    }
    override fun getItemCount() = items.size
}
