package mba.vm.onhit.ui

import NdefFileAdapter
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
            refreshFileList(uri)
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
        getChosenFolderUri()?.let {
            refreshFileList(it)
        } ?: run {
            Toast.makeText(requireContext(), "Please select a folder to store the NDEF files.", Toast.LENGTH_SHORT).show()
            openDirLauncher.launch(null)
        }
    }

    fun refreshFileList(uri: Uri) {
        val dir = DocumentFile.fromTreeUri(requireContext(), uri) ?: return
        if (!dir.exists() || !dir.isDirectory) {
            Toast.makeText(
                requireContext(),
                "The selected folder is unavailable. Please select another folder.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val fileList = dir.listFiles()
        var files: List<DocumentFile> = fileList.filter { it.isFile }
        if (fileList.isEmpty()) {
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
            files = dir.listFiles().filter { it.isFile }
        }
        val ndefFileArray = files.mapNotNull { file ->
            val name = file.name ?: return@mapNotNull null
            NdefFileItem(
                name = name,
                uri = file.uri,
                size = file.length(),
                lastModified = file.lastModified()
            )
        }
        binding.fileList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = NdefFileAdapter(ndefFileArray) { ndefFileItem ->
                parseNdef(readBytesFromUri(ndefFileItem.uri))?.let {
                    sendNdefBroadcast(it)
                } ?: run {
                    Toast.makeText(requireContext(), "Not a valid NDEF file", Toast.LENGTH_SHORT).show()
                }
            }
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

    fun getChosenFolderUri(): Uri? {
        val sp = requireContext().getSharedPreferences(
            BuildConfig.APPLICATION_ID,
            MODE_PRIVATE
        )
        return sp.getString(
            Constant.SHARED_PREFERENCES_CHOSEN_FOLDER,
            null
        )?.let(Uri::parse)
    }

    data class NdefFileItem(
        val name: String,
        val uri: Uri,
        val size: Long,
        val lastModified: Long
    )
}