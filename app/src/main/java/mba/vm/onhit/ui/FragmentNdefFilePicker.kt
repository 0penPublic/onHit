package mba.vm.onhit.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import mba.vm.onhit.Constant
import mba.vm.onhit.Constant.Companion.PREF_FIXED_UID
import mba.vm.onhit.R
import mba.vm.onhit.databinding.FragmentNdefFilePickerBinding
import mba.vm.onhit.ui.adapter.NdefFileAdapter
import kotlin.random.Random


class FragmentNdefFilePicker : Fragment() {

    private var _binding: FragmentNdefFilePickerBinding? = null
    private val binding get() = _binding!!

    private var isDirLauncherOpened: Boolean = false

    private val openDirLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            isDirLauncherOpened = false
            uri ?: return@registerForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            
            requireContext().getSharedPreferences(Constant.SHARED_PREFERENCES_NAME, MODE_PRIVATE).edit {
                putString(Constant.SHARED_PREFERENCES_CHOSEN_FOLDER, uri.toString())
            }
            loadFileListOrRequestFolder()
        }

    companion object {
        private var currentPath: String = "/"

        fun getChosenFolderUri(context: Context): Uri? {
            val sp = context.getSharedPreferences(Constant.SHARED_PREFERENCES_NAME, MODE_PRIVATE)
            return sp.getString(Constant.SHARED_PREFERENCES_CHOSEN_FOLDER, null)?.let(Uri::parse)
        }
        
        fun getCurrentPath(): String = currentPath
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
        binding.swipeRefresh.setOnRefreshListener {
            refreshFileList()
            binding.swipeRefresh.isRefreshing = false
        }
        loadFileListOrRequestFolder()
    }

    private fun setupRecyclerView() {
        binding.fileList.layoutManager = LinearLayoutManager(requireContext())
    }

    fun loadFileListOrRequestFolder() {
        if (getChosenFolderUri(requireContext()) != null) {
            refreshFileList()
        } else {
            Toast.makeText(requireContext(), R.string.toast_select_folder_saving_ndef_files, Toast.LENGTH_SHORT).show()
            launchOpenDirOnce()
        }
    }

    fun refreshFileList() {
        val uri = getChosenFolderUri(requireContext()) ?: return
        val root = DocumentFile.fromTreeUri(requireContext(), uri) ?: return
        
        val dir = if (currentPath == "/") {
            root
        } else {
            currentPath.trim('/').split("/").fold(root) { parent, name ->
                parent.findFile(name)?.takeIf { it.isDirectory } ?: run {
                    currentPath = "/" // Fallback
                    return refreshFileList()
                }
            }
        }

        if (!dir.exists() || !dir.isDirectory) {
            handleDirectoryMissing()
            return
        }

        val fileList = dir.listFiles()
            .sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })

        if (fileList.isEmpty() && currentPath == "/") {
            createDefaultTestFile(dir)
        }

        val ndefFileArray = buildList {
            if (currentPath != "/") {
                add(NdefFileItem("..", Uri.EMPTY, 0L, 0L, true))
            }
            fileList.mapTo(this) { file ->
                NdefFileItem(
                    name = file.name ?: getString(android.R.string.unknownName),
                    uri = if (file.isDirectory) Uri.EMPTY else file.uri,
                    size = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = file.isDirectory
                )
            }
        }
        
        binding.fileList.adapter = NdefFileAdapter(ndefFileArray, ::onItemClick, ::onItemLongClick)
    }

    private fun handleDirectoryMissing() {
        if (currentPath != "/") {
            currentPath = currentPath.substringBeforeLast("/", "/")
            refreshFileList()
        } else {
            Toast.makeText(requireContext(), R.string.toast_selected_folder_unavailable, Toast.LENGTH_SHORT).show()
            launchOpenDirOnce()
        }
    }

    private fun createDefaultTestFile(dir: DocumentFile) {
        val file = dir.createFile("application/octet-stream", "onHit_TestNDEF.ndef") ?: return
        val message = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", getString(R.string.ndef_text_example))))
        requireContext().contentResolver.openOutputStream(file.uri)?.use { 
            it.write(message.toByteArray()) 
        }
        refreshFileList()
    }

    private fun onItemLongClick(item: NdefFileItem) {
        if (item.isDirectory) return
        
        AlertDialog.Builder(requireContext())
            .setTitle(android.R.string.dialog_alert_title)
            .setMessage(getString(R.string.delete_file_hint, item.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                DocumentFile.fromSingleUri(requireContext(), item.uri)?.delete()?.let { success ->
                    val msg = if (success) R.string.toast_deleted_successfully else R.string.toast_delete_failed
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    refreshFileList()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onItemClick(item: NdefFileItem) {
        if (item.isDirectory) {
            currentPath = if (item.name == "..") {
                currentPath.substringBeforeLast("/", "/")
            } else {
                if (currentPath == "/") "/${item.name}" else "$currentPath/${item.name}"
            }
            refreshFileList()
        } else {
            val bytes = requireContext().contentResolver.openInputStream(item.uri)?.use { it.readBytes() }
            runCatching { NdefMessage(bytes) }.getOrNull()?.let {
                sendNdefBroadcast(it)
            } ?: Toast.makeText(requireContext(), R.string.toast_not_valid_ndef_file, Toast.LENGTH_SHORT).show()
        }
    }

    fun launchOpenDirOnce() {
        if (isDirLauncherOpened) return
        isDirLauncherOpened = true
        openDirLauncher.launch(null)
    }

    override fun onResume() {
        super.onResume()
        if (getChosenFolderUri(requireContext()) != null) {
            refreshFileList()
        }
    }

    private fun sendNdefBroadcast(ndef: NdefMessage) {
        val intent = Intent(Constant.BROADCAST_TAG_EMULATOR_REQUEST).apply {
            putExtra("uid", getUid())
            putExtra("ndef", ndef)
        }
        requireContext().sendBroadcast(intent)
    }

    private fun decodeHex(hex: String): ByteArray {
        val s = hex.replace(" ", "")
        if (s.length % 2 != 0) return byteArrayOf()
        return ByteArray(s.length / 2) {
            s.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun getUid(): ByteArray {
        val sp = requireContext().getSharedPreferences(Constant.SHARED_PREFERENCES_NAME, MODE_PRIVATE)
        val isFixed = sp.getBoolean(PREF_FIXED_UID, false)
        return if (isFixed) {
            val uidStr = sp.getString(Constant.PREF_FIXED_UID_VALUE, "") ?: ""
            decodeHex(uidStr)
        } else {
            val lenStr = sp.getString(Constant.PREF_RANDOM_UID_LEN, "4") ?: "4"
            val len = lenStr.toIntOrNull() ?: 4
            Random.nextBytes(if (len > 0) len else 4)
        }
    }
}