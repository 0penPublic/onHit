package mba.vm.onhit.ui

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import mba.vm.onhit.Constant
import mba.vm.onhit.Constant.Companion.BROADCAST_START_TAG_RECORDER_REQUEST
import mba.vm.onhit.Constant.Companion.MAX_OF_BROADCAST_SIZE
import mba.vm.onhit.Constant.Companion.REQUEST_CROP_BACKGROUND
import mba.vm.onhit.Constant.Companion.REQUEST_SELECT_BACKGROUND
import mba.vm.onhit.R
import mba.vm.onhit.databinding.ActivityMainBinding
import mba.vm.onhit.helper.DialogHelper
import mba.vm.onhit.ui.broadcast.ResponseBroadcastReceiver
import mba.vm.onhit.ui.config.ConfigManager
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
    private var pendingCroppedBackgroundUri: Uri? = null
    private val responseBroadcastReceiver: ResponseBroadcastReceiver = ResponseBroadcastReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        ContextCompat.registerReceiver(
            this,
            responseBroadcastReceiver,
            IntentFilter().apply {
                addAction(Constant.BROADCAST_TAG_RECORDER_STATE_RESPONSE)
                addAction(Constant.BROADCAST_TAG_RECORDER_RESPONSE)
            }
            , ContextCompat.RECEIVER_EXPORTED)
        setContentView(binding.root)
        applyCustomBackground()
        handleIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupBackNavigation()
        nfcHandler = NfcHandler(this).apply {
            onNdefRead = { data -> showNdefSaveDialog(data) }
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
                sendEmulateBroadcast(bytes, "ndef")
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
        currentDir ?: run {
            Toast.makeText(this, R.string.path_not_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = FileUtils.getFileName(this, uri) ?: "imported_${System.currentTimeMillis()}.ndef"
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val data = input.readBytes()
                DialogHelper.showInputBottomSheet(this, getString(R.string.dialog_title_save_ndef), fileName) { name ->
                    val file = currentDir?.createFile("application/octet-stream", name)
                    file?.uri?.let { uri ->
                        contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    }
                    Toast.makeText(this, getString(R.string.toast_import_success, fileName), Toast.LENGTH_SHORT).show()
                    finishAndRemoveTask()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_import_failed, e.message), Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
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
            if (pendingImportUri != null) {
                performImportSave()
            } else {
                DialogHelper.showSettingsSheet(
                    this,

                    {
                        requestSelectDirectory()
                    },

                    {
                        requestSelectBackground()
                    },

                    {
                        ConfigManager.setBackgroundUri(this, null)
                        applyCustomBackground()
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
            pendingImportUri ?: run {
                simulateTag(fileData, "ndef")
            }
        } else if (fileData.isMfcData) {
            pendingImportUri ?: run {
                simulateTag(fileData, "mfc")
            }
        } else if (fileData.isTraceFile) {
            pendingImportUri ?: run {
                simulateTag(fileData, "trace")
            }
        } else {
            pendingImportUri ?: run {
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
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, R.string.menu_add_folder)
        popup.menu.add(0, 3, 1, R.string.menu_build_ndef)
        if (nfcHandler.isEnabled() &&
            pendingImportUri == null) {
            popup.menu.add(1, 2, 2, R.string.import_ndef)
            popup.menu.add(1, 4, 3, "Start Recording")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> DialogHelper.showInputBottomSheet(this, getString(R.string.menu_add_folder)) { name ->
                    currentDir?.createDirectory(name)
                    refreshCurrentDir()
                }
                2 -> nfcHandler.startRead()
                3 -> showBuildNdefDialog()
                4 -> {
                    sendBroadcast(
                        Intent(BROADCAST_START_TAG_RECORDER_REQUEST)
                    )
                    sendBroadcast(
                        Intent(Constant.BROADCAST_TAG_RECORDER_STATE_REQUEST)
                    )
                }
            }
            true
        }
        popup.show()
    }


    private fun showBuildNdefDialog() {
        val dialog = DialogHelper.createBottomDialog(
            this,
            R.layout.bottom_sheet_build_ndef
        )

        data class BuiltRecord(
            val type: String,
            val value: String,
            val record: NdefRecord
        )

        val builtRecords = mutableListOf<BuiltRecord>()

        val spinner = dialog.findViewById<Spinner>(R.id.spinner_ndef_type)
        val input = dialog.findViewById<EditText>(R.id.et_ndef_value)
        val btnAddRecord = dialog.findViewById<Button>(R.id.btn_add_ndef_record)
        val btnClearRecords = dialog.findViewById<Button>(R.id.btn_clear_ndef_records)
        val tvRecordCount = dialog.findViewById<TextView>(R.id.tv_ndef_record_count)
        val tvRecordList = dialog.findViewById<TextView>(R.id.tv_ndef_record_list)
        val btnOk = dialog.findViewById<Button>(R.id.btn_ndef_ok)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_ndef_cancel)

        val types = listOf(
            getString(R.string.build_ndef_type_website),
            getString(R.string.build_ndef_type_phone),
            getString(R.string.build_ndef_type_text)
        )

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            types
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        fun ntagHint(size: Int): String {
            return when {
                size <= 0 -> ""
                size <= 144 -> getString(R.string.build_ndef_ntag_213)
                size <= 504 -> getString(R.string.build_ndef_ntag_215_216)
                size <= 888 -> getString(R.string.build_ndef_ntag_216)
                else -> getString(R.string.build_ndef_ntag_too_large)
            }
        }

        fun refreshRecordList() {
            val bytesSize = if (builtRecords.isEmpty()) {
                0
            } else {
                buildNdefBytes(builtRecords.map { it.record }).size
            }

            tvRecordCount.text = getString(
                R.string.build_ndef_record_status,
                builtRecords.size,
                bytesSize,
                ntagHint(bytesSize)
            )

            tvRecordList.text = if (builtRecords.isEmpty()) {
                getString(R.string.build_ndef_empty_records)
            } else {
                builtRecords.mapIndexed { index, item ->
                    getString(
                        R.string.build_ndef_record_item,
                        index + 1,
                        item.type,
                        item.value
                    )
                }.joinToString("\n")
            }
        }

        fun addCurrentRecord(): Boolean {
            val type = spinner.selectedItem.toString()
            val value = input.text.toString().trim()

            if (value.isEmpty()) {
                Toast.makeText(
                    this,
                    R.string.build_ndef_error_empty_value,
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }

            return try {
                val record = buildNdefRecord(type, value)
                builtRecords.add(
                    BuiltRecord(
                        type = type,
                        value = value,
                        record = record
                    )
                )
                input.setText("")
                refreshRecordList()
                true
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    getString(
                        R.string.build_ndef_error_add_failed,
                        e.message ?: getString(R.string.unknown_error)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                false
            }
        }

        btnAddRecord.setOnClickListener {
            addCurrentRecord()
        }

        btnClearRecords.setOnClickListener {
            builtRecords.clear()
            refreshRecordList()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnOk.setOnClickListener {
            if (input.text.toString().trim().isNotEmpty()) {
                if (!addCurrentRecord()) return@setOnClickListener
            }

            if (builtRecords.isEmpty()) {
                Toast.makeText(
                    this,
                    R.string.build_ndef_error_empty_records,
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            try {
                val bytes = buildNdefBytes(builtRecords.map { it.record })
                saveBuiltNdef(bytes)
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    getString(
                        R.string.build_ndef_error_build_failed,
                        e.message ?: getString(R.string.unknown_error)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        refreshRecordList()
        dialog.show()
    }

    private fun buildNdefRecord(type: String, value: String): NdefRecord {
        return when (type) {
            getString(R.string.build_ndef_type_website) -> {
                val url = if (
                    value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true)
                ) {
                    value
                } else {
                    "https://$value"
                }
                NdefRecord.createUri(url)
            }

            getString(R.string.build_ndef_type_phone) -> {
                val phone = value.replace(" ", "")
                NdefRecord.createUri("tel:$phone")
            }

            else -> {
                createTextRecord(getCurrentNdefTextLanguage(), value)
            }
        }
    }

    private fun buildNdefBytes(records: List<NdefRecord>): ByteArray {
        return NdefMessage(records.toTypedArray()).toByteArray()
    }

    private fun getCurrentNdefTextLanguage(): String {
        val language = Locale.getDefault().language
        return language.ifBlank { "en" }
    }

    private fun createTextRecord(language: String, text: String): NdefRecord {
        val languageBytes = language.toByteArray(Charsets.US_ASCII)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + languageBytes.size + textBytes.size)

        payload[0] = languageBytes.size.toByte()
        System.arraycopy(languageBytes, 0, payload, 1, languageBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + languageBytes.size, textBytes.size)

        return NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            ByteArray(0),
            payload
        )
    }

    private fun saveBuiltNdef(bytes: ByteArray) {
        val dir = currentDir ?: run {
            Toast.makeText(this, R.string.path_not_selected, Toast.LENGTH_SHORT).show()
            return
        }

        val fileName =
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(Date()) + "_built.ndef"

        val file = dir.createFile("application/octet-stream", fileName)
        val uri = file?.uri ?: run {
            Toast.makeText(
                this,
                R.string.build_ndef_error_create_file_failed,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        contentResolver.openOutputStream(uri)?.use {
            it.write(bytes)
        }

        Toast.makeText(
            this,
            getString(R.string.build_ndef_saved, fileName),
            Toast.LENGTH_SHORT
        ).show()

        refreshCurrentDir()
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

                    startCropBackground(uri)
                }

            }
            REQUEST_CROP_BACKGROUND if resultCode == RESULT_OK -> {
                pendingCroppedBackgroundUri?.let { uri ->
                    ConfigManager.setBackgroundUri(this, uri)
                    applyCustomBackground()
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
    private fun requestSelectBackground() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

        startActivityForResult(intent, REQUEST_SELECT_BACKGROUND)
    }

    private fun startCropBackground(sourceUri: Uri) {
        val outputFile = java.io.File(filesDir, "custom_background_cropped.jpg")

        if (!outputFile.exists()) {
            outputFile.createNewFile()
        }

        val outputUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            outputFile
        )

        pendingCroppedBackgroundUri = outputUri

        val cropIntent = Intent("com.android.camera.action.CROP").apply {
            setDataAndType(sourceUri, "image/*")

            putExtra("crop", "true")
            putExtra("aspectX", 9)
            putExtra("aspectY", 16)
            putExtra("scale", true)
            putExtra("return-data", false)

            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, outputUri)
            putExtra("outputFormat", android.graphics.Bitmap.CompressFormat.JPEG.toString())

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            clipData = android.content.ClipData.newRawUri(
                "cropped_background",
                outputUri
            )
        }

        val resInfoList = packageManager.queryIntentActivities(
            cropIntent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )

        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName

            grantUriPermission(
                packageName,
                sourceUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            grantUriPermission(
                packageName,
                outputUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        try {
            startActivityForResult(cropIntent, REQUEST_CROP_BACKGROUND)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show()

            ConfigManager.setBackgroundUri(this, sourceUri)
            applyCustomBackground()
        }
    }
    private fun applyCustomBackground() {
        val uri = ConfigManager.getBackgroundUri(this)

        if (uri == null) {
            binding.ivCustomBackground.setImageDrawable(null)
            binding.ivCustomBackground.visibility = View.GONE
            binding.vBackgroundScrim.visibility = View.GONE

            binding.topBar.setBackgroundColor(
                android.graphics.Color.TRANSPARENT
            )

            return
        }

        try {
            binding.ivCustomBackground.setImageDrawable(null)
            binding.ivCustomBackground.setImageURI(uri)

            binding.ivCustomBackground.visibility = View.VISIBLE
            binding.vBackgroundScrim.visibility = View.VISIBLE

            binding.topBar.setBackgroundColor(
                getColor(R.color.custom_panel_background)
            )

        } catch (_: Exception) {
            Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show()
            ConfigManager.setBackgroundUri(this, null)
            applyCustomBackground()
        }
    }
}
