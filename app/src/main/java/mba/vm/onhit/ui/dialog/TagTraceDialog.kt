package mba.vm.onhit.ui.dialog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.core.recorder.trace.TagTrace
import mba.vm.onhit.core.recorder.trace.TagTraceCodec


class TagTraceDialog(
    private val activity: Activity,
    traceBytes: ByteArray
) : Dialog(activity, R.style.Theme_OnHit) {

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
            win.setWindowAnimations(android.R.style.Animation_InputMethod)
        }
    }

    private fun populateData(view: View, trace: TagTrace) {
        view.findViewById<TextView>(R.id.tv_trace_uid).text = trace.uid.toHexString()
        view.findViewById<TextView>(R.id.tv_trace_techs).text = trace.technologies.joinToString(", ") { it.tech.name }

        val rv = view.findViewById<RecyclerView>(R.id.rv_transceive_data)
        rv.layoutManager = LinearLayoutManager(activity)
        rv.adapter = TransceiveAdapter(trace.transceiveData)
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
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvIndex.text = "#$position"
            holder.tvCmd.text = item.cmd.toHexString().chunked(2).joinToString(" ")
            holder.tvResp.text = item.resp?.toHexString()?.chunked(2)?.joinToString(" ") ?: holder.itemView.context.getString(
                R.string.trace_no_resp)
            holder.tvExtra.text = holder.itemView.context.getString(R.string.trace_extra_info, item.raw, item.returnCodes.contentToString())
        }

        override fun getItemCount() = items.size
    }
}