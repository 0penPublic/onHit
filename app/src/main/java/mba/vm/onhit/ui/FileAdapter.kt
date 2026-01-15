package mba.vm.onhit.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.ui.model.FileData
import mba.vm.onhit.utils.FileUtils

class FileAdapter(
    private val context: Context,
    private var fileList: List<FileData>,
    private val onItemClick: (FileData) -> Unit,
    private val onItemLongClick: (View, FileData) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvDetails: TextView = view.findViewById(R.id.tv_details)
        val ivIcon: ImageView = view.findViewById(R.id.iv_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.list_item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = fileList[position]
        holder.tvName.text = item.name
        holder.tvDetails.text = FileUtils.formatDetails(context, item)
        
        val iconRes = when {
            item.isDirectory -> R.drawable.baseline_folder_24
            item.isNdef -> R.drawable.baseline_nfc_24
            else -> R.drawable.baseline_article_24
        }
        holder.ivIcon.setImageResource(iconRes)

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(it, item)
            true
        }
    }

    override fun getItemCount() = fileList.size

    fun updateList(newList: List<FileData>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = fileList.size
            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = fileList[oldItemPosition]
                val newItem = newList[newItemPosition]
                // 假设 DocumentFile 的 URI 或路径可以唯一标识一个文件
                return oldItem.name == newItem.name && oldItem.isDirectory == newItem.isDirectory
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return fileList[oldItemPosition] == newList[newItemPosition]
            }
        })
        fileList = newList
        diffResult.dispatchUpdatesTo(this)
    }
}
