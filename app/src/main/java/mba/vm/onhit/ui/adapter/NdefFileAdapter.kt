import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.ui.FragmentNdefFilePicker
import mba.vm.onhit.ui.adapter.NdefViewHolder

class NdefFileAdapter(
    private val items: List<FragmentNdefFilePicker.NdefFileItem>,
    private val onItemClick: (FragmentNdefFilePicker.NdefFileItem) -> Unit
)
    : RecyclerView.Adapter<NdefViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NdefViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return NdefViewHolder(view)
    }

    override fun onBindViewHolder(holder: NdefViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        val size = item.size
        val dateStr = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", item.lastModified)
        @SuppressLint("SetTextI18n")
        holder.subtitle.text = "$size bytes | $dateStr"
        holder.itemView.setOnClickListener {
            val position = holder.absoluteAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onItemClick(items[position])
            }
        }
    }
    override fun getItemCount() = items.size
}
