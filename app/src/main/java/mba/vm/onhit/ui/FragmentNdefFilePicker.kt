package mba.vm.onhit.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mba.vm.onhit.Constant
import mba.vm.onhit.Constant.Companion.MEGABYTES_TO_BYTES
import mba.vm.onhit.R
import mba.vm.onhit.core.NdefFileManager
import mba.vm.onhit.core.SettingsManager
import mba.vm.onhit.databinding.FragmentNdefFilePickerBinding
import mba.vm.onhit.ui.adapter.NdefFileAdapter

class FragmentNdefFilePicker : Fragment() {

    private var _binding: FragmentNdefFilePickerBinding? = null
    private val binding get() = _binding!!

    private var isDirLauncherOpened: Boolean = false
    private lateinit var fileAdapter: NdefFileAdapter
    private var refreshJob: Job? = null
    private var isFirstLoad = true
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var ndefFileManager: NdefFileManager

    private val openDirLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            isDirLauncherOpened = false
            uri ?: return@registerForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            
            settingsManager.chosenFolderUri = uri
            loadFileListOrRequestFolder()
        }

    companion object {
        private var currentPath: String = "/"
        fun getCurrentPath(): String = currentPath

        fun isValidName(name: String): Boolean {
            if (name.isBlank()) return false
            val forbidden = arrayOf(".", "..", "/", "\\")
            return !forbidden.contains(name) && !name.contains("/") && !name.contains("\\")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(requireContext())
        ndefFileManager = NdefFileManager(requireContext(), settingsManager)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNdefFilePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupPathBar()
        binding.swipeRefresh.setOnRefreshListener {
            refreshFileList {
                binding.swipeRefresh.isRefreshing = false
            }
        }
        loadFileListOrRequestFolder()
    }

    private fun setupRecyclerView() {
        fileAdapter = NdefFileAdapter(emptyList(), ::onItemClick, ::onItemLongClick)
        binding.fileList.layoutManager = LinearLayoutManager(requireContext())
        binding.fileList.adapter = fileAdapter
    }

    private fun setupPathBar() {
        binding.pathCard.setOnClickListener {
            showPathEditDialog()
        }
        updatePathDisplay()
    }

    private fun updatePathDisplay() {
        binding.textPath.text = currentPath
    }

    private fun showPathEditDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generic_input, null)
        val title = view.findViewById<TextView>(R.id.tv_dialog_title)
        val input = view.findViewById<EditText>(R.id.et_dialog_input)
        val btnCancel = view.findViewById<View>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<View>(R.id.btn_dialog_confirm)

        title.text = getString(R.string.dialog_title_edit_path)
        input.setText(currentPath)
        input.setSelection(currentPath.length)

        val localDialog = AlertDialog.Builder(requireContext()).setView(view).create()
        
        btnCancel.setOnClickListener { localDialog.dismiss() }
        btnConfirm.setOnClickListener {
            val newPath = input.text.toString().trim()
            validateAndSetPath(newPath)
            localDialog.dismiss()
        }

        localDialog.show()
        localDialog.window?.apply {
            setWindowAnimations(R.style.BottomDialogAnimation)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
    }

    private fun validateAndSetPath(path: String) {
        val normalizedPath = if (path.isEmpty() || !path.startsWith("/")) "/$path" else path
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val dir = ndefFileManager.getDirectory(normalizedPath)
            withContext(Dispatchers.Main) {
                if (dir != null && dir.exists() && dir.isDirectory) {
                    currentPath = normalizedPath
                    updatePathDisplay()
                    refreshFileList()
                } else {
                    Toast.makeText(requireContext(), R.string.toast_invalid_path, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun loadFileListOrRequestFolder() {
        if (settingsManager.chosenFolderUri != null) {
            refreshFileList()
        } else {
            Toast.makeText(requireContext(), R.string.toast_select_folder_saving_ndef_files, Toast.LENGTH_SHORT).show()
            launchOpenDirOnce()
        }
    }

    fun refreshFileList(onComplete: (() -> Unit)? = null) {
        if (settingsManager.chosenFolderUri == null) {
            onComplete?.invoke()
            return
        }
        
        refreshJob?.cancel()
        refreshJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val fileList = ndefFileManager.listFiles(currentPath)
            
            withContext(Dispatchers.Main) {
                updatePathDisplay()
                if (fileList != null) {
                    fileAdapter.updateData(fileList)
                    binding.emptyView.visibility = if (fileList.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    handleDirectoryMissing()
                }
                onComplete?.invoke()
            }
        }
    }

    private fun handleDirectoryMissing() {
        if (currentPath != "/") {
            currentPath = "/"
            refreshFileList()
        } else {
            Toast.makeText(requireContext(), R.string.toast_selected_folder_unavailable, Toast.LENGTH_SHORT).show()
            launchOpenDirOnce()
        }
    }

    private fun onItemLongClick(item: NdefFileItem) {
        val holder = binding.fileList.findViewHolderForAdapterPosition(fileAdapter.getPosition(item))
        val anchor = holder?.itemView ?: return
        
        if (item.name == "..") return

        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 0, 0, R.string.menu_rename)
        popup.menu.add(0, 1, 1, R.string.menu_delete)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> showRenameDialog(item)
                1 -> showDeleteConfirmDialog(item)
            }
            true
        }
        popup.show()
    }

    private fun showRenameDialog(item: NdefFileItem) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generic_input, null)
        val title = view.findViewById<TextView>(R.id.tv_dialog_title)
        val input = view.findViewById<EditText>(R.id.et_dialog_input)
        val btnCancel = view.findViewById<View>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<View>(R.id.btn_dialog_confirm)

        title.text = getString(R.string.dialog_title_rename)
        input.setText(item.name)
        input.setSelection(item.name.length)

        val localDialog = AlertDialog.Builder(requireContext()).setView(view).create()
        
        btnCancel.setOnClickListener { localDialog.dismiss() }
        btnConfirm.setOnClickListener {
            val newName = input.text.toString().trim()
            if (!isValidName(newName)) {
                Toast.makeText(requireContext(), R.string.toast_invalid_name, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newName != item.name) {
                val success = ndefFileManager.rename(item.uri, newName)
                if (success) {
                    refreshFileList()
                    localDialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), R.string.toast_rename_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                localDialog.dismiss()
            }
        }

        localDialog.show()
        localDialog.window?.apply {
            setWindowAnimations(R.style.BottomDialogAnimation)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
    }

    private fun showDeleteConfirmDialog(item: NdefFileItem) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_generic_message, null)
        val title = view.findViewById<TextView>(R.id.tv_dialog_title)
        val message = view.findViewById<TextView>(R.id.tv_dialog_message)
        val btnCancel = view.findViewById<View>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<View>(R.id.btn_dialog_confirm)

        title.text = getString(android.R.string.dialog_alert_title)
        message.text = getString(R.string.delete_file_hint, item.name)

        val localDialog = AlertDialog.Builder(requireContext()).setView(view).create()
        
        (btnCancel as? TextView)?.text = getString(android.R.string.ok)
        (btnConfirm as? TextView)?.text = getString(android.R.string.cancel)

        btnConfirm.setOnClickListener { localDialog.dismiss() }
        btnCancel.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val success = ndefFileManager.deleteFile(item.uri)
                withContext(Dispatchers.Main) {
                    val msg = if (success) R.string.toast_deleted_successfully
                             else R.string.toast_delete_failed
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    refreshFileList()
                    localDialog.dismiss()
                }
            }
        }

        localDialog.show()
        localDialog.window?.apply {
            setWindowAnimations(R.style.BottomDialogAnimation)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
    }

    private fun onItemClick(item: NdefFileItem) {
        if (item.isDirectory) {
            currentPath = if (item.name == "..") {
                val parts = currentPath.split("/").filter { it.isNotEmpty() }
                if (parts.size <= 1) "/" else "/" + parts.dropLast(1).joinToString("/")
            } else {
                if (currentPath == "/") "/${item.name}" else "$currentPath/${item.name}"
            }
            refreshFileList()
        } else {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val ndef = ndefFileManager.readNdefMessage(item.uri)
                withContext(Dispatchers.Main) {
                    if (ndef != null) {
                        sendNdefBroadcast(ndef)
                    } else {
                        Toast.makeText(requireContext(), R.string.toast_not_valid_ndef_file, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun launchOpenDirOnce() {
        if (isDirLauncherOpened) return
        isDirLauncherOpened = true
        openDirLauncher.launch(null)
    }

    override fun onResume() {
        super.onResume()
        if (isFirstLoad) {
            isFirstLoad = false
            return
        }
        if (settingsManager.chosenFolderUri != null) {
            refreshFileList()
        }
    }

    private fun sendNdefBroadcast(ndef: android.nfc.NdefMessage) {
        val intent = Intent(Constant.BROADCAST_TAG_EMULATOR_REQUEST).apply {
            putExtra("uid", settingsManager.getUid())
            putExtra("ndef", ndef)
        }

        val size = getIntentSerializedSize(intent)
        if (size > MEGABYTES_TO_BYTES) {
            Toast.makeText(requireContext(), R.string.toast_ndef_too_large, Toast.LENGTH_LONG).show()
            return
        }

        try {
            requireContext().sendBroadcast(intent)
        } catch (e: Exception) {
            val msg = getString(R.string.toast_send_broadcast_failed, e.message ?: "Unknown")
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            Log.e("sendNdefBroadcast", msg, e)
        }
    }

    fun getIntentSerializedSize(intent: Intent): Int {
        val parcel: Parcel = Parcel.obtain()
        return try {
            intent.writeToParcel(parcel, 0)
            parcel.marshall().size
        } catch (_: Exception) {
            0
        } finally {
            parcel.recycle()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        refreshJob?.cancel()
        _binding = null
    }
}
