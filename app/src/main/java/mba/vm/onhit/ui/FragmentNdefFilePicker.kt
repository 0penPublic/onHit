package mba.vm.onhit.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
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
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.databinding.FragmentNdefFilePickerBinding
import mba.vm.onhit.ui.adapter.NdefFileAdapter
import java.security.SecureRandom


class FragmentNdefFilePicker : Fragment() {

    private var _binding: FragmentNdefFilePickerBinding? = null
    private val binding get() = _binding!!

    private val openDirLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            val sp: SharedPreferences =
                requireContext().getSharedPreferences(BuildConfig.APPLICATION_ID, MODE_PRIVATE)
            sp.edit { putString(Constant.SHARED_PREFERENCES_CHOSEN_FOLDER, uri.toString()) }
            loadFileListOrRequestFolder()
        }

    companion object {
        var currentPath: String = "/"

        fun getChosenFolderUri(context: Context): Uri? {
            val sp = context.getSharedPreferences(
                BuildConfig.APPLICATION_ID,
                MODE_PRIVATE
            )
            return sp.getString(
                Constant.SHARED_PREFERENCES_CHOSEN_FOLDER,
                null
            )?.let(Uri::parse)
        }
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
        binding.swipeRefresh.setOnRefreshListener {
            loadFileListOrRequestFolder()
            binding.swipeRefresh.isRefreshing = false
        }
        loadFileListOrRequestFolder()
    }

    fun loadFileListOrRequestFolder() {
        getChosenFolderUri(requireContext())?.let {
            refreshFileList()
        } ?: run {
            Toast.makeText(requireContext(), "Please select a folder to store the NDEF files.", Toast.LENGTH_SHORT).show()
            openDirLauncher.launch(null)
        }
    }

    fun refreshFileList() {
        val uri = getChosenFolderUri(requireContext()) ?: run { return }
        val root = DocumentFile.fromTreeUri(requireContext(), uri) ?: return
        val dir = if (currentPath == "/") {
            root
        } else {
            currentPath.trim('/').split("/").fold(root) { parent, name ->
                parent.findFile(name)?.takeIf { it.isDirectory } ?: return
            }
        }
        if (!dir.exists() || !dir.isDirectory) {
            if (currentPath != "/") {
                currentPath = currentPath.substringBeforeLast("/", "/")
                refreshFileList()
                return
            }
            Toast.makeText(
                requireContext(),
                "The selected folder is unavailable. Please select another folder.",
                Toast.LENGTH_SHORT
            ).show()
            openDirLauncher.launch(null)
            return
        }
        val fileList: List<DocumentFile> = dir.listFiles()
            .sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })
        if (fileList.isEmpty() && currentPath == "/") {
            val file = dir.createFile(
                "application/octet-stream",
                "onHit_TestNDEF.ndef"
            ) ?: return
            val message = NdefMessage(
                arrayOf(
                    NdefRecord.createTextRecord(
                        "en",
                        "Hello, There is onHit"
                    )
                )
            )
            requireContext()
                .contentResolver
                .openOutputStream(file.uri)
                ?.use { it.write(message.toByteArray()) }
        }
        val ndefFileArray = buildList {
            if (currentPath != "/") {
                add(
                    NdefFileItem(
                        name = "..",
                        uri = Uri.EMPTY,
                        size = 0L,
                        lastModified = 0L,
                        isDirectory = true
                    )
                )
            }
            fileList.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })
                .mapTo(this) { file ->
                    val name = file.name ?: "Unknown"
                    NdefFileItem(
                        name = name,
                        uri = if (file.isDirectory) Uri.EMPTY else file.uri,
                        size = if (file.isDirectory) 0L else file.length(),
                        lastModified = file.lastModified(),
                        isDirectory = file.isDirectory
                    )
                }
        }
        binding.fileList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = NdefFileAdapter(ndefFileArray, ::ndefFileItemOnClick, ::ndefFileItemLongClick)
        }
    }

    fun ndefFileItemLongClick(ndefFileItem: NdefFileItem) {
        if (!ndefFileItem.isDirectory) {
            AlertDialog.Builder(requireContext())
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage("Are you sure you want to delete the file \"${ndefFileItem.name}\"")
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    val doc = DocumentFile.fromSingleUri(requireContext(), ndefFileItem.uri)
                    doc?.delete()?.let { success ->
                        if (success) {
                            Toast.makeText(requireContext(), "Deleted successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Delete Failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    refreshFileList()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    fun ndefFileItemOnClick(ndefFileItem: NdefFileItem) {
        if (ndefFileItem.isDirectory) {
            currentPath = if (ndefFileItem.name == "..") {
                currentPath.substringBeforeLast("/", "/")
            } else {
                "$currentPath/${ndefFileItem.name}"
            }
            loadFileListOrRequestFolder()
            return
        }
        parseNdef(readBytesFromUri(ndefFileItem.uri))?.let {
            sendNdefBroadcast(it)
        } ?: run {
            Toast.makeText(requireContext(), "Not a valid NDEF file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        getChosenFolderUri(requireContext())?.let {
            refreshFileList()
        }
    }

    fun readBytesFromUri(uri: Uri): ByteArray? {
        return requireContext().contentResolver
            .openInputStream(uri)
            ?.use { it.readBytes() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun randomBytes(size: Int = 4): ByteArray { // emm... Just like a Mifare Classic Card... I guess?
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    private fun sendNdefBroadcast(ndef: NdefMessage) {
        val intent = Intent(Constant.BROADCAST_TAG_EMULATOR_REQUEST).apply {
            putExtra("uid", randomBytes())
            putExtra("ndef", ndef)
        }
        requireContext().sendBroadcast(intent)
    }

    fun parseNdef(bytes: ByteArray?): NdefMessage? = runCatching { NdefMessage(bytes) }.getOrNull()

    data class NdefFileItem(
        val name: String,
        val uri: Uri,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean
    )
}