package mba.vm.onhit.ui

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import mba.vm.onhit.Constant
import mba.vm.onhit.Constant.Companion.MAX_OF_BROADCAST_SIZE
import mba.vm.onhit.Constant.Companion.REQUEST_CROP_BACKGROUND
import mba.vm.onhit.Constant.Companion.REQUEST_SELECT_BACKGROUND
import mba.vm.onhit.R
import mba.vm.onhit.core.recorder.TagRecorderStateHelper
import mba.vm.onhit.databinding.ActivityMainBinding
import mba.vm.onhit.helper.DialogHelper
import mba.vm.onhit.ui.broadcast.ResponseBroadcastReceiver
import mba.vm.onhit.ui.config.ConfigManager
import mba.vm.onhit.ui.handler.NdefEditor
import mba.vm.onhit.ui.handler.NfcHandler
import mba.vm.onhit.ui.manager.BackgroundManager
import mba.vm.onhit.ui.manager.ImportController
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
    private lateinit var ndefEditor: NdefEditor
    private lateinit var backgroundManager: BackgroundManager
    private lateinit var importController: ImportController
    private val executor = Executors.newSingleThreadExecutor()
    private var isRefreshing = false
    private val responseBroadcastReceiver: ResponseBroadcastReceiver = ResponseBroadcastReceiver()
    private var recorderState: String? = null
    private var activePopupMenu: PopupMenu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        responseBroadcastReceiver.onStateReceived = { state ->
            if (recorderState != null && recorderState != state) {
                Toast.makeText(this, getString(R.string.recorder_state, TagRecorderStateHelper.toRecorderStateText(this, state)), Toast.LENGTH_SHORT).show()
            }
            recorderState = state
            activePopupMenu?.menu?.findItem(4)?.title = getString(R.string.recorder_state, TagRecorderStateHelper.toRecorderStateText(this, state))
        }
        ContextCompat.registerReceiver(
            this,
            responseBroadcastReceiver,
            IntentFilter().apply {
                addAction(Constant.BROADCAST_TAG_RECORDER_STATE_RESPONSE)
                addAction(Constant.BROADCAST_TAG_RECORDER_RESPONSE)
            }
            , ContextCompat.RECEIVER_EXPORTED)
        setContentView(binding.root)
        backgroundManager = BackgroundManager(this, binding)
        importController = ImportController(this, binding) { refreshCurrentDir() }
        ndefEditor = NdefEditor(this, ::saveBuiltNdef)
        backgroundManager.applyCustomBackground()
        handleIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupBackNavigation()
        nfcHandler = NfcHandler(this).apply {
            onNdefRead = { data ->
                ndefEditor.showNdefPreviewDialog(data) {
                    showNdefSaveDialog(data)
                }
            }
        }
        adapter = FileAdapter(this, emptyList(), ::onFileClick, ::showItemPopupMenu)
        binding.rvFiles.adapter = adapter
        setupListeners()
        if (!restoreLastDirectory()) {
            Toast.makeText(this, R.string.toast_no_valid_storage, Toast.LENGTH_LONG).show()
            requestSelectDirectory()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val className = intent?.component?.className ?: return
        val appId = packageName
        when (className) {
            "$appId.ImportHandler" -> {
                val isInternal = intent.getBooleanExtra("is_internal", false)
                val uri = if (intent.action == Intent.ACTION_SEND) {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.data
                }
                uri?.let { importController.importFile(it, isInternal) }
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
                sendEmulateBroadcast(bytes, "ndef")
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_send_broadcast_failed, e.message), Toast.LENGTH_SHORT).show()
        } finally {
            finishAndRemoveTask()
            exitProcess(0)
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
        if (binding.etSearch.isVisible) {
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
            if (importController.isImportMode()) {
                importController.performImportSave(currentDir)
            } else {
                DialogHelper.showSettingsSheet(
                    this,
                    { requestSelectDirectory() },
                    { backgroundManager.requestSelectBackground() },
                    {
                        ConfigManager.setBackgroundUri(this, null)
                        backgroundManager.applyCustomBackground()
                    }
                )
            }
        }

        binding.btnAdd.setOnClickListener { view ->
            showAddPopupMenu(view)
        }

        binding.btnSearch.setOnClickListener {
            if (binding.etSearch.isGone) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.etSearch.windowInsetsController?.show(WindowInsets.Type.ime())
        } else {
            val imm = binding.etSearch.context.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(binding.etSearch, 0)
        }
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
            if (!importController.isImportMode()) {
                simulateTag(fileData, "ndef")
            }
        } else if (fileData.isMfcData) {
            if (!importController.isImportMode()) {
                simulateTag(fileData, "mfc")
            }
        } else if (fileData.isTraceFile) {
            if (!importController.isImportMode()) {
                simulateTag(fileData, "trace")
            }
        } else {
            if (!importController.isImportMode()) {
                Toast.makeText(this, R.string.toast_not_ndef_file, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun simulateTag(fileData: FileData, tagType: String) {
        val file = fileData.documentFile ?: return
        try {
            contentResolver.openInputStream(file.uri)?.use { input ->
                val bytes = input.readBytes()
                sendEmulateBroadcast(bytes, tagType)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_send_broadcast_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmulateBroadcast(data: ByteArray, tagType: String) {
        val uid = ConfigManager.getUid(this)
        val intent = Intent(Constant.BROADCAST_TAG_EMULATOR_REQUEST).apply {
            putExtra("uid", uid)
            putExtra("data", data)
            putExtra("tagType", tagType)
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
    }

    private fun showAddPopupMenu(view: View) {
        sendBroadcast(Intent(Constant.BROADCAST_TAG_RECORDER_STATE_REQUEST))
        val popup = PopupMenu(this, view)
        activePopupMenu = popup
        popup.setOnDismissListener { activePopupMenu = null }

        popup.menu.add(0, 1, 0, R.string.menu_add_folder)
        popup.menu.add(0, 3, 1, R.string.menu_build_ndef)
        if (importController.isImportMode()) {
            popup.menu.add(0, 5, 4, R.string.menu_cancel_import)
        }
        if (nfcHandler.isEnabled() &&
            !importController.isImportMode()) {
            popup.menu.add(1, 2, 2, R.string.import_ndef)
            popup.menu.add(1, 4, 3, getString(R.string.recorder_state, TagRecorderStateHelper.toRecorderStateText(this, recorderState)))
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> DialogHelper.showInputBottomSheet(this, getString(R.string.menu_add_folder)) { name ->
                    currentDir?.createDirectory(name)
                    refreshCurrentDir()
                }
                2 -> nfcHandler.startRead()
                3 -> ndefEditor.showBuildNdefDialog()
                4 -> {
                    sendBroadcast(Intent(Constant.BROADCAST_TOGGLE_TAG_RECORDER_REQUEST))
                }
                5 -> DialogHelper.showConfirmBottomSheet(
                    this,
                    getString(R.string.dialog_title_confirm_cancel_import),
                    getString(R.string.confirm_cancel_import_hint)
                ) {
                    importController.resetImportMode()
                }
            }
            true
        }
        popup.show()
    }

    private fun saveBuiltNdef(bytes: ByteArray, fileToEdit: DocumentFile? = null) {
        if (fileToEdit != null) {
            contentResolver.openOutputStream(fileToEdit.uri)?.use {
                it.write(bytes)
            }
            Toast.makeText(this, R.string.toast_write_success, Toast.LENGTH_SHORT).show()
            refreshCurrentDir()
        } else {
            val fileName = String.format(Locale.getDefault(), "%s_built.ndef",
                SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date()))
            val tempFile = java.io.File(filesDir, fileName)
            tempFile.writeBytes(bytes)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                tempFile
            )
            importController.importFile(uri, true)
        }
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
        if (importController.isImportMode()) return
        if (fileData.isParent) return
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, R.string.menu_rename)
        popup.menu.add(0, 2, 1, R.string.menu_delete)
        if (fileData.isNdef && nfcHandler.isEnabled() && !importController.isImportMode()) popup.menu.add(0, 3, 2, R.string.menu_write_to_tag)
        if (fileData.isNdef) popup.menu.add(0, 4, 3, R.string.menu_edit_ndef)
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
                4 -> {
                    val file = fileData.documentFile
                    if (file != null) {
                        try {
                            contentResolver.openInputStream(file.uri)?.use { input ->
                                ndefEditor.showBuildNdefDialog(input.readBytes(), file)
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
        when (requestCode) {
            1001 if resultCode == RESULT_OK -> {
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
            REQUEST_SELECT_BACKGROUND if resultCode == RESULT_OK -> {
                data?.data?.let { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                    }
                    backgroundManager.startCropBackground(uri)
                }

            }
            REQUEST_CROP_BACKGROUND if resultCode == RESULT_OK -> {
                backgroundManager.handleCropResult()
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
