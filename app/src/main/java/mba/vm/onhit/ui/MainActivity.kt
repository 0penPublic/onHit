package mba.vm.onhit.ui

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import mba.vm.onhit.Constant
import mba.vm.onhit.R
import mba.vm.onhit.core.recorder.TagRecorderStateHelper
import mba.vm.onhit.databinding.ActivityMainBinding
import mba.vm.onhit.ui.dialog.DialogHelper
import mba.vm.onhit.ui.broadcast.ResponseBroadcastReceiver
import mba.vm.onhit.ui.config.ConfigManager
import mba.vm.onhit.ui.manager.BackgroundManager
import mba.vm.onhit.ui.manager.DirectoryManager
import mba.vm.onhit.ui.adapter.FileAdapter
import mba.vm.onhit.ui.controller.ImportController
import mba.vm.onhit.ui.decorator.SpacingItemDecoration
import mba.vm.onhit.ui.manager.MainIntentHandler
import mba.vm.onhit.ui.manager.SystemSaveManager
import mba.vm.onhit.ui.manager.TagEmulatorManager
import mba.vm.onhit.ui.dialog.TagTraceDialog
import mba.vm.onhit.ui.model.FileData
import mba.vm.onhit.ui.nfc.NdefEditor
import mba.vm.onhit.ui.dialog.NdefEditorDialog
import mba.vm.onhit.ui.nfc.NfcHandler
import mba.vm.onhit.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FileAdapter

    private lateinit var directoryManager: DirectoryManager
    private lateinit var tagEmulatorManager: TagEmulatorManager
    private lateinit var systemSaveManager: SystemSaveManager
    private lateinit var intentHandler: MainIntentHandler

    private lateinit var nfcHandler: NfcHandler
    private lateinit var ndefEditor: NdefEditor
    private lateinit var backgroundManager: BackgroundManager
    private lateinit var importController: ImportController

    private var activeNdefDialog: NdefEditorDialog? = null
    private var fileToEdit: DocumentFile? = null
    private val responseBroadcastReceiver = ResponseBroadcastReceiver()
    private var recorderState: String? = null
    private var activePopupMenu: PopupMenu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupManagers()
        setupUI()
        setupNfcAndNdef()
        setupBroadcastReceiver()

        backgroundManager.applyCustomBackground()
        intentHandler.handleIntent(intent)

        if (!directoryManager.restoreLastDirectory()) {
            Toast.makeText(this, R.string.toast_no_valid_storage, Toast.LENGTH_LONG).show()
            directoryManager.requestSelectDirectory()
        }
    }

    private fun setupManagers() {
        directoryManager = DirectoryManager(
            activity = this,
            onDataChanged = { files, path ->
                adapter.updateList(files)
                binding.tvCurrentPath.text = path
            },
            onRefreshStateChanged = { isRefreshing ->
                binding.srlLayout.isRefreshing = isRefreshing
            }
        )
        tagEmulatorManager = TagEmulatorManager(this)
        systemSaveManager = SystemSaveManager(this)
        importController = ImportController(this, binding) { directoryManager.refreshCurrentDir() }
        intentHandler = MainIntentHandler(this, importController, tagEmulatorManager)
        backgroundManager = BackgroundManager(this, binding)
    }

    private fun setupUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupBackNavigation()

        adapter = FileAdapter(this, emptyList(), ::onFileClick, ::showItemPopupMenu)
        binding.rvFiles.adapter = adapter
        binding.rvFiles.addItemDecoration(SpacingItemDecoration(6, 3))

        setupListeners()
    }

    private fun setupNfcAndNdef() {
        ndefEditor = NdefEditor(this)

        nfcHandler = NfcHandler(this).apply {
            onNdefRead = { data ->
                val dialog = DialogHelper.showNdefEditorDialog(
                    activity = this@MainActivity,
                    isReadOnly = true,
                    initialBytes = data,
                    onResult = { }
                )
                dialog.setOnDismissListener {
                    showNdefSaveDialog(data)
                }
            }
        }
    }

    private fun saveNdefBytes(bytes: ByteArray) {
        if (fileToEdit != null) {
            contentResolver.openOutputStream(fileToEdit!!.uri, "rwt")?.use { outputStream ->
                outputStream.write(bytes)
            }
            Toast.makeText(this, R.string.toast_save_success, Toast.LENGTH_SHORT).show()
            directoryManager.refreshCurrentDir()
        } else {
            showNdefSaveDialog(bytes)
        }
    }

    private fun launchNdefEditor(initialBytes: ByteArray? = null, docFile: DocumentFile? = null) {
        fileToEdit = docFile
        activeNdefDialog = DialogHelper.showNdefEditorDialog(
            activity = this,
            isReadOnly = false,
            initialBytes = initialBytes,
            onResult = { bytes ->
                saveNdefBytes(bytes)
                fileToEdit = null
            }
        )
        activeNdefDialog?.setOnDismissListener { activeNdefDialog = null }
    }

    private fun setupBroadcastReceiver() {
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
            },
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentHandler.handleIntent(intent)
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
        return directoryManager.navigateUp()
    }

    private fun setupListeners() {
        binding.fabSettings.setOnClickListener {
            if (importController.isImportMode()) {
                importController.performImportSave(directoryManager.currentDir)
            } else {
                DialogHelper.showSettingsSheet(
                    this,
                    { directoryManager.requestSelectDirectory() },
                    { backgroundManager.requestSelectBackground() },
                    {
                        ConfigManager.setBackgroundUri(this, null)
                        backgroundManager.applyCustomBackground()
                    }
                )
            }
        }

        binding.btnAdd.setOnClickListener { view -> showAddPopupMenu(view) }

        binding.btnSearch.setOnClickListener {
            if (binding.etSearch.isGone) showSearch() else hideSearch()
        }

        binding.tvCurrentPath.setOnClickListener {
            val currentPathStr = binding.tvCurrentPath.text.toString()
            DialogHelper.showInputBottomSheet(this, getString(R.string.dialog_title_path), currentPathStr) { inputPath ->
                val targetDir = FileUtils.findDirectoryByPath(directoryManager.rootDir, inputPath)
                if (targetDir != null) {
                    directoryManager.navigateTo(targetDir)
                } else {
                    Toast.makeText(this, R.string.toast_storage_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                directoryManager.filterFiles(s?.toString() ?: "", binding.tvCurrentPath.text.toString())
            }
        })

        binding.srlLayout.setOnRefreshListener {
            directoryManager.refreshCurrentDir()
        }
    }

    private fun showSearch() {
        binding.tvAppTitle.visibility = View.GONE
        binding.etSearch.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.etSearch.windowInsetsController?.show(WindowInsets.Type.ime())
        } else {
            val imm = this.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(binding.etSearch, 0)
        }
    }

    private fun hideSearch() {
        binding.etSearch.visibility = View.GONE
        binding.tvAppTitle.visibility = View.VISIBLE
        binding.etSearch.setText("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        directoryManager.filterFiles("", binding.tvCurrentPath.text.toString())
    }

    private fun onFileClick(fileData: FileData) {
        if (tagEmulatorManager.onFileClick(fileData, importController.isImportMode())) {
            // Handled by emulator
        } else if (fileData.isParent) {
            directoryManager.currentDir?.parentFile?.let { directoryManager.navigateTo(it) }
        } else if (fileData.isDirectory) {
            fileData.documentFile?.let { directoryManager.navigateTo(it) }
        }
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
        if (nfcHandler.isEnabled() && !importController.isImportMode()) {
            popup.menu.add(1, 2, 2, R.string.import_ndef)
            popup.menu.add(1, 4, 3, getString(R.string.recorder_state, TagRecorderStateHelper.toRecorderStateText(this, recorderState)))
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> DialogHelper.showInputBottomSheet(this, getString(R.string.menu_add_folder)) { name ->
                    directoryManager.currentDir?.createDirectory(name)
                    directoryManager.refreshCurrentDir()
                }
                2 -> nfcHandler.startRead()
                3 -> launchNdefEditor()
                4 -> sendBroadcast(Intent(Constant.BROADCAST_TOGGLE_TAG_RECORDER_REQUEST))
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

    private fun showNdefSaveDialog(data: ByteArray) {
        val defaultName = SimpleDateFormat(
            "yyyy-MM-dd_HH-mm-ss",
            Locale.getDefault()
        ).format(Date()) + ".ndef"
        DialogHelper.showInputBottomSheet(this, getString(R.string.dialog_title_save_ndef), defaultName) { name ->
            val file = directoryManager.currentDir?.createFile("application/octet-stream", name)
            file?.uri?.let { uri ->
                contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                    outputStream.write(data)
                }
                directoryManager.refreshCurrentDir()
            }
        }
    }

    private fun showItemPopupMenu(view: View, fileData: FileData) {
        if (importController.isImportMode() || fileData.isParent) return
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, R.string.menu_rename)
        popup.menu.add(0, 2, 1, R.string.menu_delete)
        if (fileData.isNdef && nfcHandler.isEnabled() && !importController.isImportMode()) popup.menu.add(0, 3, 2, R.string.menu_write_to_tag)
        if (fileData.isNdef) popup.menu.add(0, 4, 3, R.string.menu_edit_ndef)
        if (fileData.isTraceFile) popup.menu.add(0, 5, 4, R.string.menu_view_trace)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> DialogHelper.showInputBottomSheet(this, getString(R.string.menu_rename), fileData.name) { newName ->
                    if (fileData.documentFile?.renameTo(newName) == true) directoryManager.refreshCurrentDir()
                }
                2 -> DialogHelper.showConfirmBottomSheet(
                    this,
                    getString(R.string.dialog_title_confirm_delete),
                    getString(R.string.delete_file_hint, fileData.name)
                ) {
                    if (fileData.documentFile?.delete() == true) directoryManager.refreshCurrentDir()
                }
                3 -> {
                    fileData.documentFile?.let { file ->
                        try {
                            contentResolver.openInputStream(file.uri)?.use { nfcHandler.startWrite(it.readBytes()) }
                        } catch (_: Exception) {
                            Toast.makeText(this, R.string.toast_not_ndef_file, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                4 -> {
                    fileData.documentFile?.let { file ->
                        try {
                            contentResolver.openInputStream(file.uri)?.use {
                                launchNdefEditor(it.readBytes(), file)
                            }
                        } catch (_: Exception) {
                            Toast.makeText(this, R.string.toast_not_ndef_file, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                5 -> {
                    fileData.documentFile?.let { file ->
                        try {
                            contentResolver.openInputStream(file.uri)?.use {
                                TagTraceDialog(this, it.readBytes()).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, getString(R.string.trace_error_open, e.message), Toast.LENGTH_SHORT).show()
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
        directoryManager.refreshCurrentDir()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) {
            if (requestCode == Constant.REQUEST_EDIT_NDEF) fileToEdit = null
            return
        }
        when (requestCode) {
            Constant.REQUEST_SELECT_DIRECTORY -> directoryManager.handleDirectoryResult(data)
            Constant.REQUEST_SELECT_BACKGROUND -> {
                data?.data?.let { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {}
                    backgroundManager.startCropBackground(uri)
                }
            }
            Constant.REQUEST_CROP_BACKGROUND -> backgroundManager.handleCropResult()
            Constant.REQUEST_SAVE_FILE -> {
                data?.data?.let { uri ->
                    activeNdefDialog?.handleFileSaveResult(uri)
                }
                if (systemSaveManager.handleSaveResult(resultCode, data)) {
                    directoryManager.refreshCurrentDir()
                }
            }
            Constant.REQUEST_SELECT_NDEF_FILE -> {
                data?.data?.let { uri ->
                    activeNdefDialog?.handleFilePickResult(uri)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcHandler.stopDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        directoryManager.destroy()
    }
}