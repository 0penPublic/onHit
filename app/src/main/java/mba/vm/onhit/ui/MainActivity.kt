package mba.vm.onhit.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import mba.vm.onhit.Constant
import mba.vm.onhit.R
import mba.vm.onhit.core.NdefFileManager
import mba.vm.onhit.core.SettingsManager
import mba.vm.onhit.databinding.ActivityMainBinding
import mba.vm.onhit.utils.HexUtils
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var ndefFilePicker: FragmentNdefFilePicker
    private lateinit var settingsManager: SettingsManager
    private lateinit var ndefFileManager: NdefFileManager
    private var dialog: AlertDialog? = null
    private var hexWatcher: TextWatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager(this)
        ndefFileManager = NdefFileManager(this, settingsManager)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        ndefFilePicker = (navHostFragment.childFragmentManager.primaryNavigationFragment as? FragmentNdefFilePicker)!!
        
        binding.settingsFab.setOnClickListener {
            showSettingsDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_bar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.add_ndef_record -> {
                val actionView = findViewById<View>(R.id.add_ndef_record)
                showAddPopupMenu(actionView)
                true
            }
            R.id.search_ndef_record -> {
                Toast.makeText(this, R.string.toast_todo, Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAddPopupMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.menu_add_folder)
        popup.menu.add(0, 1, 1, R.string.menu_add_from_tag)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> showCreateDirDialog()
                1 -> showNfcDialog()
            }
            true
        }
        popup.show()
    }

    private fun showCreateDirDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_generic_input, null)
        val title = view.findViewById<TextView>(R.id.tv_dialog_title)
        val input = view.findViewById<EditText>(R.id.et_dialog_input)
        val btnCancel = view.findViewById<View>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<View>(R.id.btn_dialog_confirm)

        title.text = getString(R.string.menu_add_folder)
        input.hint = getString(R.string.hint_folder_name)

        val localDialog = AlertDialog.Builder(this).setView(view).create()
        
        btnCancel.setOnClickListener { localDialog.dismiss() }
        btnConfirm.setOnClickListener {
            val dirName = input.text.toString().trim()
            if (dirName.isNotEmpty()) {
                val success = ndefFileManager.createDirectory(FragmentNdefFilePicker.getCurrentPath(), dirName)
                if (success) {
                    ndefFilePicker.refreshFileList()
                    localDialog.dismiss()
                } else {
                    Toast.makeText(this, R.string.toast_create_dir_failed, Toast.LENGTH_SHORT).show()
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

    private fun enableHexGroupedInput(editText: EditText) {
        if (hexWatcher != null) return

        hexWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                val hexOnly = HexUtils.filterHex(input).uppercase()
                
                val builder = StringBuilder()
                hexOnly.forEachIndexed { index, c ->
                    if (index > 0 && index % 2 == 0) builder.append(" ")
                    builder.append(c)
                }
                val result = builder.toString()

                if (result != input) {
                    editText.setText(result)
                    editText.setSelection(result.length)
                }
            }
        }
        editText.addTextChangedListener(hexWatcher)
    }

    private fun disableHexGroupedInput(editText: EditText) {
        hexWatcher?.let { editText.removeTextChangedListener(it) }
        hexWatcher = null
    }

    private fun showSettingsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dailog_settings, null)
        val checkBoxFixedUid = view.findViewById<MaterialCheckBox>(R.id.checkbox_fixed_uid)
        val layoutUidInput = view.findViewById<TextInputLayout>(R.id.layout_uid_input)
        val editUidInput = view.findViewById<EditText>(R.id.edit_uid_input)
        val itemReselectDir = view.findViewById<View>(R.id.item_reselect_dir)
        val itemGithub = view.findViewById<View>(R.id.item_github)

        checkBoxFixedUid.isChecked = settingsManager.isFixedUid
        
        fun updateInputMode(fixed: Boolean) {
            if (fixed) {
                layoutUidInput.hint = getString(R.string.settings_hint_fixed_uid)
                editUidInput.inputType = InputType.TYPE_CLASS_TEXT
                enableHexGroupedInput(editUidInput)
                editUidInput.setText(settingsManager.fixedUidValue)
            } else {
                layoutUidInput.hint = getString(R.string.settings_hint_random_uid_len)
                editUidInput.inputType = InputType.TYPE_CLASS_NUMBER
                disableHexGroupedInput(editUidInput)
                editUidInput.setText(settingsManager.randomUidLen)
            }
            editUidInput.setSelection(editUidInput.text.length)
        }
        
        updateInputMode(settingsManager.isFixedUid)

        checkBoxFixedUid.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isFixedUid = isChecked
            updateInputMode(isChecked)
        }

        editUidInput.addTextChangedListener {
            val text = it?.toString() ?: ""
            if (settingsManager.isFixedUid) {
                settingsManager.fixedUidValue = text
            } else {
                settingsManager.randomUidLen = text
            }
        }

        itemReselectDir.setOnClickListener {
            dialog?.dismiss()
            ndefFilePicker.launchOpenDirOnce()
        }

        itemGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Constant.GITHUB_URL))
            startActivity(intent)
        }

        dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        
        dialog?.show()
        dialog?.window?.apply {
            setWindowAnimations(R.style.BottomDialogAnimation)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
    }

    private fun showNfcDialog() {
        enableNfcReaderMode()
        val view: View = LayoutInflater.from(this).inflate(R.layout.dialog_add_ndef_record, null)
        dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        
        view.findViewById<View?>(R.id.btn_dialog_cancel)!!
            .setOnClickListener {
                dialog?.dismiss()
            }
        dialog?.setOnDismissListener {
            disableNfcReaderMode()
        }
        dialog?.show()
        dialog?.window?.apply {
            setWindowAnimations(R.style.BottomDialogAnimation)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
    }

    private fun showFilenameInputDialog(ndefData: ByteArray) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_generic_input, null)
        val title = view.findViewById<TextView>(R.id.tv_dialog_title)
        val input = view.findViewById<EditText>(R.id.et_dialog_input)
        val btnCancel = view.findViewById<View>(R.id.btn_dialog_cancel)
        val btnConfirm = view.findViewById<View>(R.id.btn_dialog_confirm)

        title.text = getString(R.string.ndef_filename_input_hint)
        val defaultName = "${DateFormat.format("yyyy-MM-dd_HH-mm-ss", Date())}.ndef"
        input.setText(defaultName)

        val localDialog = AlertDialog.Builder(this).setView(view).create()
        
        btnCancel.setOnClickListener { localDialog.dismiss() }
        btnConfirm.setOnClickListener {
            val fileName = input.text.toString().trim()
            if (fileName.isNotEmpty()) {
                saveNdefFile(fileName, ndefData)
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

    private fun saveNdefFile(fileName: String, ndefData: ByteArray) {
        val success = ndefFileManager.saveNdefFile(FragmentNdefFilePicker.getCurrentPath(), fileName, ndefData)
        if (success) {
            Toast.makeText(this, R.string.toast_saved_successfully, Toast.LENGTH_SHORT).show()
            ndefFilePicker.refreshFileList()
        } else {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun processTag(tag: Tag) {
        runOnUiThread {
            disableNfcReaderMode()
            dialog?.dismiss()
            val ndef = Ndef.get(tag)
            ndef?.let {
                showFilenameInputDialog(ndef.cachedNdefMessage.toByteArray())
            } ?: run {
                Toast.makeText(this, R.string.toast_tag_not_support_ndef_format, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun enableNfcReaderMode() {
        val options = Bundle()
        nfcAdapter.enableReaderMode(this, { tag ->
            tag?.let { processTag(tag) }
        }, NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE,
            options)
    }

    fun disableNfcReaderMode() {
        nfcAdapter.disableReaderMode(this)
    }
}
