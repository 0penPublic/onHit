package mba.vm.onhit.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.net.Uri
import android.transition.TransitionManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.ui.adapter.NdefRecordAdapter
import mba.vm.onhit.ui.decorator.SpacingItemDecoration
import mba.vm.onhit.model.BuiltRecord
import mba.vm.onhit.ui.nfc.NdefEditor

class NdefEditorDialog(
    private val activity: Activity,
    private val isReadOnly: Boolean = false,
    private val initialBytes: ByteArray? = null,
    private val onResult: (ByteArray) -> Unit
) : Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    private val builtRecords: MutableList<BuiltRecord> = mutableListOf()
    private lateinit var recordAdapter: NdefRecordAdapter
    private val ndefEditorHelper = NdefEditor(activity)

    private var lastPickedIndex: Int? = null
    private var isFullScreen = false
    private var isModified = false

    init {
        @Suppress("InflateParams")
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_ndef_editor, null)
        setContentView(view)
        setCanceledOnTouchOutside(false) // Handle manually via scrim click

        if (initialBytes != null) {
            builtRecords.addAll(ndefEditorHelper.parseNdefMessage(initialBytes))
        }

        setupWindow()
        setupUI()
        refreshRecordStatus()
        setupBackIntercept()
    }

    private fun markModified() {
        if (!isModified && !isReadOnly) {
            isModified = true
            setCanceledOnTouchOutside(false)
        }
    }

    private fun setupBackIntercept() {
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                handleExitRequest()
                true
            } else {
                false
            }
        }
    }

    private fun handleExitRequest() {
        if (!isModified || isReadOnly) {
            dismiss()
            return
        }
        DialogHelper.showConfirmBottomSheet(
            activity,
            activity.getString(R.string.dialog_title_confirm_cancel_edit),
            activity.getString(R.string.confirm_exit_ndef_editor)
        ) {
            dismiss()
        }
    }

    private fun setupWindow() {
        window?.apply {
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            val params = attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            attributes = params
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }
    }

    private fun setupUI() {
        val scrim = findViewById<View>(R.id.dialog_scrim)
        
        scrim.setOnClickListener { handleExitRequest() }

        val tvTitle = findViewById<TextView>(R.id.tv_title)
        if (isReadOnly) {
            tvTitle.text = activity.getString(R.string.import_ndef)
        }

        val btnZoom = findViewById<ImageButton>(R.id.btn_zoom)
        btnZoom.setOnClickListener { toggleFullScreen() }

        val btnAdd = findViewById<ImageButton>(R.id.btn_add_ndef_record)
        val btnCancel = findViewById<Button>(R.id.btn_cancel_ndef_editor)
        val btnOk = findViewById<Button>(R.id.btn_ndef_ok)
        val rvRecords = findViewById<RecyclerView>(R.id.rv_ndef_records)

        if (isReadOnly) {
            btnAdd.visibility = View.GONE
            btnCancel.visibility = View.GONE
            btnOk.text = activity.getString(android.R.string.ok)
        } else if (initialBytes != null && initialBytes.isNotEmpty()) {
            // This is edit mode (initial bytes were provided)
            btnOk.text = activity.getString(R.string.build_ndef_save_changes)
        }

        recordAdapter = NdefRecordAdapter(activity, builtRecords, {
            markModified()
            refreshRecordStatus()
        }, { index ->
            deleteRecord(index)
        }, { index ->
            lastPickedIndex = index
            DialogHelper.requestPickFile(activity)
        }, { index, filename ->
            lastPickedIndex = index
            val payload = builtRecords[index].payload
            if (payload != null) {
                DialogHelper.requestSaveFile(activity, filename)
            }
        })
        recordAdapter.isReadOnly = isReadOnly

        rvRecords.layoutManager = LinearLayoutManager(activity)
        rvRecords.adapter = recordAdapter
        rvRecords.addItemDecoration(SpacingItemDecoration(0, 3))

        if (!isReadOnly) {
            val itemTouchHelper = ItemTouchHelper(object :
                ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                
                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.apply {
                            animate().scaleX(1.03f).scaleY(1.03f).alpha(0.9f).setDuration(150).start()
                            elevation = 10f
                        }
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.apply {
                        animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start()
                        elevation = 0f
                    }
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    recordAdapter.onItemMove(
                        viewHolder.bindingAdapterPosition,
                        target.bindingAdapterPosition
                    )
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            })
            itemTouchHelper.attachToRecyclerView(rvRecords)
        }

        btnAdd.setOnClickListener { addRecord() }
        btnCancel.setOnClickListener { handleExitRequest() }
        btnOk.setOnClickListener { generateAndFinish() }
    }

    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        val btnZoom = findViewById<ImageButton>(R.id.btn_zoom)
        val layoutRoot = findViewById<ViewGroup>(R.id.layout_root)
        val layoutContent = findViewById<View>(R.id.layout_content_container)
        val rvRecords = findViewById<View>(R.id.rv_ndef_records)

        TransitionManager.beginDelayedTransition(layoutRoot)

        val density = activity.resources.displayMetrics.density
        val margin = (32 * density).toInt()

        if (isFullScreen) {
            btnZoom.setImageResource(R.drawable.baseline_collapse_all_24)

            layoutRoot.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(0, margin, 0, 0)
            }
            layoutContent.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            rvRecords.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        } else {
            btnZoom.setImageResource(R.drawable.baseline_expand_all_24)

            layoutRoot.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(0, margin, 0, 0)
            }
            layoutContent.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rvRecords.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun refreshRecordStatus() {
        val tvStatus = findViewById<TextView>(R.id.tv_ndef_record_count)
        val tvEmpty = findViewById<TextView>(R.id.tv_ndef_empty)

        val currentRecords = recordAdapter.getRecords()
        val bytesSize = try {
            if (currentRecords.isEmpty()) 0
            else ndefEditorHelper.buildNdefBytes(currentRecords.map { it.record }).size
        } catch (_: Exception) {
            -1
        }

        tvStatus.text = if (bytesSize >= 0) {
            val capacity = when {
                bytesSize <= 144 -> "NTAG213"
                bytesSize <= 504 -> "NTAG215/216"
                bytesSize <= 888 -> "NTAG216"
                else -> "> NTAG216"
            }
            activity.getString(R.string.build_ndef_record_status_with_capacity, currentRecords.size, bytesSize, capacity)
        } else {
            activity.getString(R.string.build_ndef_record_status, currentRecords.size, 0)
        }

        val isEmpty = currentRecords.isEmpty()
        tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.rv_ndef_records).visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun addRecord() {
        markModified()
        val defaultType = activity.getString(R.string.build_ndef_type_website)
        val newRecord = BuiltRecord(
            type = defaultType,
            value = "",
            record = ndefEditorHelper.buildNdefRecord(defaultType, "", null, null)
        )
        recordAdapter.addRecord(newRecord)
        recordAdapter.setExpanded(recordAdapter.itemCount - 1)
        findViewById<RecyclerView>(R.id.rv_ndef_records).scrollToPosition(recordAdapter.itemCount - 1)
        refreshRecordStatus()
    }

    private fun deleteRecord(index: Int) {
        val recordsBeforeDelete = recordAdapter.getRecords()
        if (index !in recordsBeforeDelete.indices) return

        if (recordsBeforeDelete[index].value.isEmpty()) {
            performDelete(index)
            return
        }
        
        DialogHelper.showConfirmBottomSheet(
            activity, 
            activity.getString(R.string.dialog_title_confirm_delete_record), 
            activity.getString(R.string.confirm_delete_ndef_record_hint)
        ) {
            performDelete(index)
        }
    }

    private fun performDelete(index: Int) {
        recordAdapter.removeRecord(index)
        markModified()
        refreshRecordStatus()
    }

    private fun generateAndFinish() {
        if (isReadOnly) {
            dismiss()
            return
        }

        val currentRecords = recordAdapter.getRecords()
        if (currentRecords.isEmpty()) {
            Toast.makeText(activity, R.string.build_ndef_error_empty_records, Toast.LENGTH_SHORT).show()
            return
        }

        for (record in currentRecords) {
            if (record.value.isEmpty()) {
                Toast.makeText(activity, R.string.build_ndef_error_empty_value, Toast.LENGTH_SHORT).show()
                return
            }
        }

        try {
            val bytes = ndefEditorHelper.buildNdefBytes(currentRecords.map { it.record })
            onResult(bytes)
            dismiss()
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.build_ndef_error_build_failed, e.localizedMessage), Toast.LENGTH_SHORT).show()
        }
    }

    fun handleFilePickResult(uri: Uri) {
        try {
            activity.contentResolver.openInputStream(uri)?.use { inputStream ->
                lastPickedIndex?.let { index ->
                    recordAdapter.updatePayload(index, inputStream.readBytes())
                }
            }
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.error_read_file, e.localizedMessage), Toast.LENGTH_SHORT).show()
        }
    }

    fun handleFileSaveResult(uri: Uri) {
        try {
            val index = lastPickedIndex ?: return
            val currentRecords = recordAdapter.getRecords()
            val payload = currentRecords[index].payload ?: return
            activity.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                outputStream.write(payload)
            }
            Toast.makeText(activity, R.string.toast_save_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.error_save_file, e.localizedMessage), Toast.LENGTH_SHORT).show()
        }
    }
}