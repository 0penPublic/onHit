package mba.vm.onhit.ui.handler

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.ui.model.BuiltRecord

class NdefRecordAdapter(
    private var records: List<BuiltRecord>,
    private val onItemClick: (Int, BuiltRecord) -> Unit
) : RecyclerView.Adapter<NdefRecordAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tv_record_type)
        val tvValue: TextView = view.findViewById(R.id.tv_record_value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_built_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.tvType.text = record.type
        holder.tvValue.text = record.value
        holder.itemView.setOnClickListener { onItemClick(position, record) }
    }

    override fun getItemCount() = records.size

    fun updateData(newRecords: List<BuiltRecord>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = records.size
            override fun getNewListSize(): Int = newRecords.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                records[oldItemPosition] == newRecords[newItemPosition]

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                records[oldItemPosition] == newRecords[newItemPosition]
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        records = newRecords
        diffResult.dispatchUpdatesTo(this)
    }
}
