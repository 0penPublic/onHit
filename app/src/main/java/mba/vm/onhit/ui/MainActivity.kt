package mba.vm.onhit.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.Constant.Companion.MAX_OF_BROADCAST_SIZE
import mba.vm.onhit.R
import mba.vm.onhit.core.ConfigManager
import mba.vm.onhit.databinding.ActivityMainBinding
import mba.vm.onhit.helper.DialogHelper
import mba.vm.onhit.ui.handler.NfcHandler
import mba.vm.onhit.ui.model.FileData
import mba.vm.onhit.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.system.exitProcess

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private var allFiles = listOf<FileData>()
    private lateinit var adapter: FileAdapter

    private var currentDir: DocumentFile? = null
    private var rootDir: DocumentFile? = null

    private lateinit var nfcHandler: NfcHandler
    
    private val executor = Executors.newSingleThreadExecutor()
    private var isRefreshing = false

    private var pendingImportUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handleIntent(intent)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupBackNavigation()

        nfcHandler = NfcHandler(this).apply {
            onNdefRead = { data -> showNdefSaveDialog(data) }
        }

        adapter = FileAdapter(this, emptyList(), ::onFileClick, ::showItemPopupMenu)
        binding.rvFiles.adapter = adapter

        setupListeners()
        if (!restoreLastDirectory()) requestSelectDirectory()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val className = intent?.component?.className ?: return
        val appId = BuildConfig.APPLICATION_ID
        when (className) {
            "$appId.ImportHandler" -> {
                val uri = if (intent.action == Intent.ACTION_SEND) {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.data
                }
                uri?.let { importFile(it) }
            }
            "$appId.BroadcastHandler" -> {
                val uri = if (intent.action == Intent.ACTION_SEND) {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.data
                }
                uri?.let { handleBroadcastIntent(it) }
            }
        }
    }

    private fun handleBroadcastIntent(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                sendEmulateBroadcast(bytes)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_send_broadcast_failed, e.message), Toast.LENGTH_SHORT).show()
        } finally {
            finishAndRemoveTask()
            exitProcess(0)
        }
    }

    private fun importFile(uri: Uri) {
        pendingImportUri = uri
        val fileName = FileUtils.getFileName(this, uri) ?: "imported_${System.currentTimeMillis()}.ndef"
        binding.tvAppTitle.text = getString(R.string.title_save_to, fileName)
        binding.fabSettings.setImageResource(R.drawable.baseline_save_24)
        binding.btnSearch.visibility = View.GONE
    }

    private fun performImportSave() {
        val uri = pendingImportUri ?: return
        val dir = currentDir ?: run {
            Toast.makeText(this, R.string.path_not_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = FileUtils.getFileName(this, uri) ?: "imported_${System.currentTimeMillis()}.ndef"
        
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val data = input.readBytes()
                val file = dir.createFile("application/octet-stream", fileName)
                file?.uri?.let { destUri ->
                    contentResolver.openOutputStream(destUri)?.use { output ->
                        output.write(data)
                    }
                    Toast.makeText(this, getString(R.string.toast_import_success, fileName), Toast.LENGTH_SHORT).show()
                    finishAndRemoveTask()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_import_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                if (!handleBackNavigation()) {
                    finish()
                }
            }
        }
    }

    private fun handleBackNavigation(): Boolean {
        if (binding.etSearch.visibility == View.VISIBLE) {
            hideSearch()
            return true
        }
        if (currentDir?.uri != rootDir?.uri) {
            currentDir?.parentFile?.let {
                navigateTo(it)
                return true
            }
        }
        return false
    }

    private fun setupListeners() {
        binding.fabSettings.setOnClickListener {
            if (pendingImportUri != null) {
                performImportSave()
            } else {
                DialogHelper.showSettingsSheet(this) { requestSelectDirectory() }
            }
        }

        binding.btnAdd.setOnClickListener { view ->
            showAddPopupMenu(view)
        }

        binding.btnSearch.setOnClickListener {
            if (binding.etSearch.visibility == View.GONE) {
                showSearch()
            } else {
                hideSearch()
            }
        }

        binding.tvCurrentPath.setOnClickListener {
            val currentPathStr = binding.tvCurrentPath.text.toString()
            DialogHelper.showInputBottomSheet(this, getString(R.string.dialog_title_path), currentPathStr) { inputPath ->
                val targetDir = FileUtils.findDirectoryByPath(rootDir, inputPath)
                if (targetDir != null) {
                    navigateTo(targetDir)
                } else {
                    Toast.makeText(this, R.string.toast_storage_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterFiles(s?.toString() ?: "")
            }
        })

        binding.srlLayout.setOnRefreshListener {
            refreshCurrentDir()
        }
    }

    private fun showSearch() {
        binding.tvAppTitle.visibility = View.GONE
        binding.etSearch.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSearch() {
        binding.etSearch.visibility = View.GONE
        binding.tvAppTitle.visibility = View.VISIBLE
        binding.etSearch.setText("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        filterFiles("")
    }

    private fun filterFiles(query: String) {
        val filtered = if (query.isEmpty()) {
            allFiles
        } else {
            allFiles.filter { it.isParent || it.name.contains(query, ignoreCase = true) }
        }
        adapter.updateList(filtered)
    }

    private fun onFileClick(fileData: FileData) {
        if (isRefreshing) return
        if (fileData.isParent) {
            currentDir?.parentFile?.let { navigateTo(it) }
        } else if (fileData.isDirectory) {
            fileData.documentFile?.let { navigateTo(it) }
        } else if (fileData.isNdef) {
            pendingImportUri ?: run {
                simulateNdefTag(fileData)
            }
        } else {
            pendingImportUri ?: run {
                Toast.makeText(this, R.string.toast_not_ndef_file, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun simulateNdefTag(fileData: FileData) {
        val file = fileData.documentFile ?: return
        try {
            contentResolver.openInputStream(file.uri)?.use { input ->
                val bytes = input.readBytes()
                sendEmulateBroadcast(bytes)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_send_broadcast_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmulateBroadcast(ndefBytes: ByteArray) {
        try {
            val ndef = NdefMessage(ndefBytes)
            val uid = ConfigManager.getUid(this)
            val intent = Intent(Constant.BROADCAST_TAG_EMULATOR_REQUEST).apply {
                putExtra("uid", uid)
                putExtra("ndef", ndef)
            }

            val parcel = Parcel.obtain()
            try {
                intent.writeToParcel(parcel, 0)
                if (parcel.dataSize() > MAX_OF_BROADCAST_SIZE) {
                    Toast.makeText(this, R.string.toast_file_too_large, Toast.LENGTH_SHORT).show()
                    return
                }
            } finally {
                parcel.recycle()
            }

            sendBroadcast(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_send_broadcast_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, R.string.menu_add_folder)
        if (nfcHandler.isEnabled() &&
            pendingImportUri == null) popup.menu.add(0, 2, 2, R.string.import_ndef)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> DialogHelper.showInputBottomSheet(this, getString(R.string.menu_add_folder)) { name ->
                    currentDir?.createDirectory(name)
                    refreshCurrentDir()
                }
                2 -> nfcHandler.startRead()
            }
            true
        }
        popup.show()
    }

    private fun showNdefSaveDialog(data: ByteArray) {
        val defaultName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date()) + ".ndef"
        DialogHelper.showInputBottomSheet(this, getString(R.string.dialog_title_save_ndef), defaultName) { name ->
            val file = currentDir?.createFile("application/octet-stream", name)
            file?.uri?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { it.write(data) }
                refreshCurrentDir()
            }
        }
    }

    private fun showItemPopupMenu(view: View, fileData: FileData) {
        if (pendingImportUri != null) return
        if (fileData.isParent) return
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, R.string.menu_rename)
        popup.menu.add(0, 2, 1, R.string.menu_delete)
        if (fileData.isNdef && nfcHandler.isEnabled() && pendingImportUri == null) popup.menu.add(0, 3, 2, R.string.menu_write_to_tag)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> DialogHelper.showInputBottomSheet(this, getString(R.string.menu_rename), fileData.name) { newName ->
                    if (fileData.documentFile?.renameTo(newName) == true) refreshCurrentDir()
                }
                2 -> DialogHelper.showConfirmBottomSheet(
                    this,
                    getString(R.string.dialog_title_confirm_delete),
                    getString(R.string.delete_file_hint, fileData.name)
                ) {
                    if (fileData.documentFile?.delete() == true) refreshCurrentDir()
                }
                3 -> {
                    val file = fileData.documentFile
                    if (file != null) {
                        try {
                            contentResolver.openInputStream(file.uri)?.use { input ->
                                nfcHandler.startWrite(input.readBytes())
                            }
                        } catch (_: Exception) {
                            Toast.makeText(this, R.string.toast_not_ndef_file, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            true
        }
        popup.show()
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentDir()
    }

    private fun refreshCurrentDir() {
        val dir = currentDir ?: return
        if (!dir.exists() || !dir.canRead()) {
            Toast.makeText(this, R.string.toast_storage_unavailable, Toast.LENGTH_SHORT).show()
            if (dir.uri != rootDir?.uri) {
                rootDir?.let { navigateTo(it) } ?: requestSelectDirectory()
            } else {
                requestSelectDirectory()
            }
            return
        }
        
        if (isRefreshing) return
        isRefreshing = true
        binding.srlLayout.isRefreshing = true
        
        executor.execute {
            val newList = FileUtils.getFileDataList(this, dir, rootDir)
            runOnUiThread {
                allFiles = newList
                filterFiles(binding.etSearch.text.toString())
                isRefreshing = false
                binding.srlLayout.isRefreshing = false
            }
        }
    }

    private fun restoreLastDirectory(): Boolean {
        val uri = ConfigManager.getRootUri(this) ?: return false
        val hasPermission = contentResolver.persistedUriPermissions.any { it.uri == uri }
        val df = if (hasPermission) DocumentFile.fromTreeUri(this, uri) else null
        
        if (df != null && df.exists() && df.canRead()) {
            rootDir = df
            navigateTo(df)
            return true
        }
        
        Toast.makeText(this, R.string.toast_storage_unavailable, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun requestSelectDirectory() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                ConfigManager.setRootUri(this, uri)
                val df = DocumentFile.fromTreeUri(this, uri)
                if (df != null && df.exists() && df.canRead()) {
                    rootDir = df
                    navigateTo(df)
                } else {
                    Toast.makeText(this, R.string.toast_storage_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateTo(dir: DocumentFile) {
        currentDir = dir
        binding.tvCurrentPath.text = FileUtils.getSimplifiedPath(this, rootDir, dir)
        hideSearch()
        refreshCurrentDir()
    }
    
    override fun onPause() {
        super.onPause()
        nfcHandler.stopDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
