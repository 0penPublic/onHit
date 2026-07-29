package mba.vm.onhit.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.model.BuiltRecord
import mba.vm.onhit.ui.nfc.NdefEditor
import mba.vm.onhit.ui.nfc.handler.UnknownRecordHandler

class NdefRecordAdapter(
    private val context: Context,
    private var records: MutableList<BuiltRecord>,
    private val onRecordUpdated: () -> Unit,
    private val onRecordDelete: (Int) -> Unit,
    private val onPickFile: (Int) -> Unit,
    private val onExportPayload: (Int, String) -> Unit
) : RecyclerView.Adapter<NdefRecordAdapter.ViewHolder>() {

    private var expandedPosition = -1
    var isReadOnly: Boolean = false
    private val ndefEditorHelper = NdefEditor(context)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layoutSummary: View = view.findViewById(R.id.layout_record_summary)
        val layoutEditor: View = view.findViewById(R.id.layout_record_editor)

        val tvType: TextView = view.findViewById(R.id.tv_record_type)
        val tvValue: TextView = view.findViewById(R.id.tv_record_value)

        val spinner: Spinner = view.findViewById(R.id.spinner_ndef_type)
        val container: ViewGroup = view.findViewById(R.id.ndef_type_container)

        val btnSave: Button = view.findViewById(R.id.btn_save_record)
        val btnDelete: Button = view.findViewById(R.id.btn_delete_record)

        var currentLayoutId: Int = -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_built_record, parent, false)
        view.clipToOutline = true
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        val isExpanded = position == expandedPosition

        holder.tvType.text = record.type
        
        val handler = ndefEditorHelper.getHandler(record.type) ?: UnknownRecordHandler(context)
        holder.tvValue.text = handler.getSummary(record)

        holder.layoutSummary.visibility = if (isExpanded) View.GONE else View.VISIBLE
        holder.layoutEditor.visibility = if (isExpanded) View.VISIBLE else View.GONE

        if (isExpanded && !isReadOnly) {
            setupEditorShell(holder, record)
        }

        holder.layoutSummary.setOnClickListener {
            if (isReadOnly) return@setOnClickListener
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val prev = expandedPosition
            expandedPosition = pos
            notifyItemChanged(prev)
            notifyItemChanged(expandedPosition)
        }
    }

    fun onItemMove(from: Int, to: Int) {
        if (from == to) return
        val temp = records.removeAt(from)
        records.add(to, temp)
        if (expandedPosition == from) expandedPosition = to
        notifyItemMoved(from, to)
        onRecordUpdated()
    }

    private fun setupEditorShell(holder: ViewHolder, record: BuiltRecord) {
        val types = listOf(
            context.getString(R.string.build_ndef_type_website),
            context.getString(R.string.build_ndef_type_phone),
            context.getString(R.string.build_ndef_type_text),
            context.getString(R.string.build_ndef_type_aar),
            context.getString(R.string.build_ndef_type_wifi),
            context.getString(R.string.build_ndef_type_mime),
            context.getString(R.string.build_ndef_type_external)
        )

        holder.spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, types).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val typeIndex = types.indexOf(record.type)
        if (typeIndex >= 0) {
            holder.spinner.onItemSelectedListener = null
            holder.spinner.setSelection(typeIndex)
        }

        refreshInnerEditor(holder, record.type, record)

        holder.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val adapterPos = holder.bindingAdapterPosition
                if (adapterPos == RecyclerView.NO_POSITION) return
                val type = types[pos]
                if (type != records[adapterPos].type) {
                    refreshInnerEditor(holder, type, records[adapterPos])
                    updateRecord(adapterPos, holder)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        holder.btnSave.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) {
                expandedPosition = -1
                notifyItemChanged(adapterPos)
            }
        }

        holder.btnDelete.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) onRecordDelete(adapterPos)
        }
    }

    private fun refreshInnerEditor(holder: ViewHolder, type: String, record: BuiltRecord) {
        val handler = ndefEditorHelper.getHandler(type) ?: return
        val layoutId = handler.getLayoutId(type)

        if (holder.currentLayoutId != layoutId) {
            holder.container.removeAllViews()
            LayoutInflater.from(context).inflate(layoutId, holder.container, true)
            holder.currentLayoutId = layoutId
        }

        handler.bindView(
            holder.container,
            record,
            type,
            onUpdate = {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) updateRecord(pos, holder)
            },
            onPickFile = {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onPickFile(pos)
            },
            onExportPayload = { value ->
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onExportPayload(pos, value)
            }
        )
    }

    private fun updateRecord(position: Int, holder: ViewHolder) {
        val type = holder.spinner.selectedItem.toString()
        val handler = ndefEditorHelper.getHandler(type) ?: return

        val oldRecord = records[position]
        val newRecord = handler.updateRecordFromUI(
            holder.container,
            oldRecord,
            type
        ) { t, v, l, p -> ndefEditorHelper.buildNdefRecord(t, v, l, p) }

        records[position] = newRecord
        onRecordUpdated()
    }

    override fun getItemCount() = records.size

    fun setExpanded(position: Int) {
        val prev = expandedPosition
        expandedPosition = position
        if (prev >= 0) notifyItemChanged(prev)
        if (expandedPosition >= 0) notifyItemChanged(expandedPosition)
    }

    fun addRecord(record: BuiltRecord) {
        records.add(record)
        notifyItemInserted(records.size - 1)
        onRecordUpdated()
    }

    fun removeRecord(index: Int) {
        if (index in records.indices) {
            records.removeAt(index)
            notifyItemRemoved(index)
            if (expandedPosition == index) {
                expandedPosition = -1
            } else if (expandedPosition > index) {
                expandedPosition--
            }
            onRecordUpdated()
        }
    }

    fun updatePayload(position: Int, payload: ByteArray) {
        val record = records[position]
        val newNdefRecord = ndefEditorHelper.buildNdefRecord(record.type, record.value, record.lang, payload)
        records[position] = record.copy(payload = payload, record = newNdefRecord)
        notifyItemChanged(position)
        onRecordUpdated()
    }

    fun getRecords(): List<BuiltRecord> = records

}
