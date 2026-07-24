package mba.vm.onhit.ui.adapter

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.ui.model.BuiltRecord
import mba.vm.onhit.ui.nfc.NdefEditor
import mba.vm.onhit.ui.nfc.handler.UriRecordHandler
import mba.vm.onhit.ui.nfc.handler.wifi.NdefWifiHelper

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

        var etValue: EditText? = null
        var etLang: EditText? = null
        var spinnerPrefix: Spinner? = null
        var btnSelectFile: Button? = null
        var btnExportPayload: Button? = null
        var currentLayoutId: Int = -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_built_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        val isExpanded = position == expandedPosition

        holder.tvType.text = record.type
        
        if (record.type == context.getString(R.string.build_ndef_type_wifi)) {
            val lines = record.value.split("\n")
            val ssid = lines.getOrNull(0) ?: ""
            val password = lines.getOrNull(1) ?: ""
            val maskedPassword = "*".repeat(password.length.coerceAtMost(12).coerceAtLeast(4))
            holder.tvValue.text = if (ssid.isNotEmpty()) "$ssid ($maskedPassword)" else maskedPassword
        } else {
            holder.tvValue.text = record.value
        }

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

        inflateTypeLayout(holder, record.type)
        bindDataToEditor(holder, record)

        holder.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val adapterPos = holder.bindingAdapterPosition
                if (adapterPos == RecyclerView.NO_POSITION) return
                val type = types[pos]
                if (type != records[adapterPos].type) {
                    inflateTypeLayout(holder, type)
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

    private fun inflateTypeLayout(holder: ViewHolder, type: String) {
        val layoutId = when (type) {
            context.getString(R.string.build_ndef_type_text) -> R.layout.layout_ndef_input_text
            context.getString(R.string.build_ndef_type_wifi) -> R.layout.layout_ndef_input_wifi
            context.getString(R.string.build_ndef_type_website) -> R.layout.layout_ndef_input_uri
            context.getString(R.string.build_ndef_type_mime),
            context.getString(R.string.build_ndef_type_external) -> R.layout.layout_ndef_input_payload
            else -> R.layout.layout_ndef_input_common
        }

        if (holder.currentLayoutId == layoutId) return
        holder.container.removeAllViews()
        val innerView = LayoutInflater.from(context).inflate(layoutId, holder.container, true)

        holder.etValue = innerView.findViewById(R.id.et_ndef_value)
        holder.etLang = innerView.findViewById(R.id.et_ndef_lang)
        holder.spinnerPrefix = innerView.findViewById(R.id.spinner_uri_prefix)
        holder.btnSelectFile = innerView.findViewById(R.id.btn_select_file)
        holder.btnExportPayload = innerView.findViewById(R.id.btn_export_payload)
        holder.currentLayoutId = layoutId

        holder.etValue?.doAfterTextChanged {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) updateRecord(pos, holder)
        }
        holder.etLang?.doAfterTextChanged {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) updateRecord(pos, holder)
        }
        holder.btnSelectFile?.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onPickFile(pos)
        }
        holder.btnExportPayload?.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onExportPayload(pos, holder.etValue?.text?.toString()?.trim() ?: "")
            }
        }

        innerView.findViewById<TextView>(R.id.tv_ndef_value_label)?.text = when (type) {
            context.getString(R.string.build_ndef_type_website) -> context.getString(R.string.build_ndef_label_url)
            context.getString(R.string.build_ndef_type_phone) -> context.getString(R.string.build_ndef_label_phone)
            context.getString(R.string.build_ndef_type_aar) -> context.getString(R.string.build_ndef_label_package)
            else -> context.getString(R.string.build_ndef_value)
        }
    }

    private fun bindDataToEditor(holder: ViewHolder, record: BuiltRecord) {
        if (record.type == context.getString(R.string.build_ndef_type_wifi)) {
            val lines = record.value.split("\n")
            val etSsid = holder.container.findViewById<EditText>(R.id.et_ndef_wifi_ssid)
            val etPassword = holder.container.findViewById<EditText>(R.id.et_ndef_wifi_password)
            val btnToggle = holder.container.findViewById<ImageButton>(R.id.btn_toggle_password)
            
            etSsid?.setText(lines.getOrNull(0) ?: "")
            etPassword?.setText(lines.getOrNull(1) ?: "")

            btnToggle?.setOnClickListener {
                if (etPassword.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                    etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    btnToggle.setImageResource(R.drawable.baseline_password_show_24)
                } else {
                    etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    btnToggle.setImageResource(R.drawable.baseline_password_hide_24)
                }
                etPassword.setSelection(etPassword.text.length)
            }
            if (etPassword.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                btnToggle?.setImageResource(R.drawable.baseline_password_show_24)
            } else {
                btnToggle?.setImageResource(R.drawable.baseline_password_hide_24)
            }

            val spinnerSec = holder.container.findViewById<Spinner>(R.id.spinner_ndef_wifi_sec)
            val spinnerEnc = holder.container.findViewById<Spinner>(R.id.spinner_ndef_wifi_enc)

            if (spinnerSec != null) {
                val secNames = NdefWifiHelper.authModes.map { it.first }
                spinnerSec.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, secNames).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spinnerSec.setSelection(secNames.indexOf(lines.getOrNull(2) ?: "None").coerceAtLeast(0))
                spinnerSec.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                        val pos = holder.bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) updateRecord(pos, holder)
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }

            if (spinnerEnc != null) {
                val encNames = NdefWifiHelper.encryptionModes.map { it.first }
                spinnerEnc.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, encNames).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spinnerEnc.setSelection(encNames.indexOf(lines.getOrNull(3) ?: "None").coerceAtLeast(0))
                spinnerEnc.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                        val pos = holder.bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) updateRecord(pos, holder)
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }

            etSsid?.doAfterTextChanged {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) updateRecord(pos, holder)
            }
            etPassword?.doAfterTextChanged {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) updateRecord(pos, holder)
            }
        } else if (record.type == context.getString(R.string.build_ndef_type_website)) {
            val handler = ndefEditorHelper.getHandler(record.type) as? UriRecordHandler
            if (handler != null) {
                val prefixes = handler.getPrefixes()
                val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, prefixes)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                holder.spinnerPrefix?.adapter = adapter
                
                var matchedPrefix = ""
                var matchedIndex = 0
                val noneLabel = context.getString(R.string.build_ndef_uri_prefix_none)
                for ((index, prefix) in prefixes.withIndex()) {
                    if (prefix != noneLabel && prefix != "Custom" && 
                        record.value.startsWith(prefix, ignoreCase = true)) {
                        if (prefix.length > matchedPrefix.length) {
                            matchedPrefix = prefix
                            matchedIndex = index
                        }
                    }
                }
                
                holder.spinnerPrefix?.onItemSelectedListener = null
                holder.etValue?.setText(record.value.substring(matchedPrefix.length))
                holder.spinnerPrefix?.setSelection(matchedIndex)
                
                // Update hint based on selection
                if (matchedIndex == 0) {
                    holder.etValue?.hint = context.getString(R.string.build_ndef_value_hint)
                } else {
                    holder.etValue?.hint = "..."
                }
                
                holder.spinnerPrefix?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, id: Long) {
                        if (pos == 0) {
                            holder.etValue?.hint = context.getString(R.string.build_ndef_value_hint)
                        } else {
                            holder.etValue?.hint = "..."
                        }
                        val adapterPos = holder.bindingAdapterPosition
                        if (adapterPos != RecyclerView.NO_POSITION) updateRecord(adapterPos, holder)
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }
        } else {
            holder.etValue?.setText(record.value)
            holder.etLang?.setText(record.lang ?: "")
        }
    }

    private fun updateRecord(position: Int, holder: ViewHolder) {
        val type = holder.spinner.selectedItem.toString()
        val oldRecord = records[position]
        var value: String
        var lang: String? = null
        var payload = oldRecord.payload

        if (type == context.getString(R.string.build_ndef_type_wifi)) {
            val ssid = holder.container.findViewById<EditText>(R.id.et_ndef_wifi_ssid)?.text?.toString() ?: ""
            val password = holder.container.findViewById<EditText>(R.id.et_ndef_wifi_password)?.text?.toString() ?: ""
            val security = holder.container.findViewById<Spinner>(R.id.spinner_ndef_wifi_sec)?.selectedItem?.toString() ?: "None"
            val encryption = holder.container.findViewById<Spinner>(R.id.spinner_ndef_wifi_enc)?.selectedItem?.toString() ?: "None"
            value = "$ssid\n$password\n$security\n$encryption"
            payload = null
        } else if (type == context.getString(R.string.build_ndef_type_website)) {
            val prefix = holder.spinnerPrefix?.selectedItem?.toString() ?: ""
            val noneLabel = context.getString(R.string.build_ndef_uri_prefix_none)
            val actualPrefix = if (prefix == noneLabel || prefix == "Custom (Full URI)" || prefix == "Custom") "" else prefix
            val rest = holder.etValue?.text?.toString()?.trim() ?: ""
            value = actualPrefix + rest
            payload = null
        } else {
            value = holder.etValue?.text?.toString()?.trim() ?: ""
            lang = holder.etLang?.text?.toString()?.trim()?.ifEmpty { null }
            if (oldRecord.type == type && oldRecord.value != value) payload = null
        }

        val newNdefRecord = try {
            ndefEditorHelper.buildNdefRecord(type, value, lang, payload)
        } catch (_: Exception) {
            oldRecord.record
        }

        records[position] = BuiltRecord(type, value, newNdefRecord, lang, payload)
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
