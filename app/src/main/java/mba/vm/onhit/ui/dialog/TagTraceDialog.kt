package mba.vm.onhit.ui.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.model.trace.TagTrace
import mba.vm.onhit.model.trace.TagTraceCodec
import mba.vm.onhit.ui.decorator.SpacingItemDecoration
import mba.vm.onhit.utils.HexUtils
import org.json.JSONArray
import org.json.JSONObject


class TagTraceDialog(
    private val activity: Activity,
    traceBytes: ByteArray
) : Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    init {
        @SuppressLint("InflateParams")
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_tag_trace, null)
        setContentView(view)

        setupWindow()
        try {
            val trace = TagTraceCodec.decode(traceBytes)
            populateData(view, trace)
        } catch (e: Exception) {
            view.findViewById<TextView>(R.id.tv_trace_uid).text = activity.getString(
                R.string.trace_error_open, e.message)
        }

        view.findViewById<Button>(R.id.btn_close).setOnClickListener { dismiss() }
    }

    private fun setupWindow() {
        window?.let { win ->
            win.setGravity(Gravity.BOTTOM)
            win.setBackgroundDrawableResource(android.R.color.transparent)
            val params = win.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            win.attributes = params
            // Animation is fine
            win.setWindowAnimations(android.R.style.Animation_InputMethod)
        }
    }

    private fun populateData(view: View, trace: TagTrace) {
        view.findViewById<TextView>(R.id.tv_trace_uid).text = trace.uid.toHexString()
        view.findViewById<TextView>(R.id.tv_trace_techs).text = trace.technologies.joinToString(", ") { it.tech.name }

        val rv = view.findViewById<RecyclerView>(R.id.rv_transceive_data)
        rv.layoutManager = LinearLayoutManager(activity)
        rv.adapter = TransceiveAdapter(trace.transceiveData)
        rv.addItemDecoration(SpacingItemDecoration(0, 3))
    }

    private class TransceiveAdapter(private val items: List<TagTrace.TransceiveData>) :
        RecyclerView.Adapter<TransceiveAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvIndex: TextView = view.findViewById(R.id.tv_index)
            val tvCmd: TextView = view.findViewById(R.id.tv_cmd)
            val tvResp: TextView = view.findViewById(R.id.tv_resp)
            val tvExtra: TextView = view.findViewById(R.id.tv_extra)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transceive_data, parent, false)
            view.clipToOutline = true
            return ViewHolder(view)
        }


        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            @SuppressLint("SetTextI18n")
            holder.tvIndex.text = "#$position"
            holder.tvCmd.text = HexUtils.toHexString(item.cmd).chunked(2).joinToString(" ")
            holder.tvResp.text = item.resp?.let { HexUtils.toHexString(it).chunked(2).joinToString(" ") } 
                ?: holder.itemView.context.getString(R.string.trace_no_resp)
            holder.tvExtra.text = holder.itemView.context
                .getString(R.string.trace_extra_info, item.raw, item.returnCodes.contentToString())

            holder.itemView.setOnClickListener {
                copyToClipboard(holder.itemView.context, item)
            }
        }

        private fun copyToClipboard(context: Context, item: TagTrace.TransceiveData) {
            val json = JSONObject().apply {
                put("cmd", HexUtils.toHexString(item.cmd))
                put("resp", item.resp?.let { HexUtils.toHexString(it) } ?: JSONObject.NULL)
                put("raw", item.raw)
                put("returnCodes", JSONArray(item.returnCodes))
            }.toString()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Transceive Data", json)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, context.getString(R.string.toast_copy_success), Toast.LENGTH_SHORT).show()
        }

        override fun getItemCount() = items.size
    }
}