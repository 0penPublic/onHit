package mba.vm.onhit.ui.adapter

import android.annotation.SuppressLint
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.ui.NdefFileItem

class NdefFileAdapter(
    private var items: List<NdefFileItem>,
    private val onItemClick: (NdefFileItem) -> Unit,
    private val onItemLongClick: (NdefFileItem) -> Unit
)
    : RecyclerView.Adapter<NdefViewHolder>() {

    fun updateData(newItems: List<NdefFileItem>) {
        val diffCallback = NdefDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    fun getPosition(item: NdefFileItem): Int {
        return items.indexOf(item)
    }

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
        } else {
            holder.icon.setImageResource(R.drawable.baseline_nfc_24)
        }
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onItemClick(items[pos])
            }
        }
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onItemLongClick(items[pos])
            }
            return@setOnLongClickListener true
        }
    }
    override fun getItemCount() = items.size

    class NdefDiffCallback(
        private val oldList: List<NdefFileItem>,
        private val newList: List<NdefFileItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].uri == newList[newItemPosition].uri &&
                    oldList[oldItemPosition].name == newList[newItemPosition].name
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
