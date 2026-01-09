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
import android.text.TextWatcher
import android.text.InputType
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.widget.addTextChangedListener
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import mba.vm.onhit.Constant
import mba.vm.onhit.R
import mba.vm.onhit.databinding.ActivityMainBinding
import java.util.Date


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var ndefFilePicker: FragmentNdefFilePicker
    private var dialog: AlertDialog? = null
    private var hexWatcher: TextWatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                FragmentNdefFilePicker.getChosenFolderUri(this)?.let {
                    if (dialog?.isShowing != true) showNfcDialog()
                } ?: run { ndefFilePicker.loadFileListOrRequestFolder() }
                true
            }
            R.id.search_ndef_record -> {
                Toast.makeText(this, R.string.toast_todo, Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun enableHexGroupedInput(editText: EditText) {
        if (hexWatcher != null) return

        hexWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                var text = s.toString().replace(" ", "").uppercase()
                text = text.filter { it.isDigit() || it in 'A'..'F' }

                val grouped = buildString {
                    text.forEachIndexed { index, c ->
                        if (index > 0 && index % 2 == 0) append(" ")
                        append(c)
                    }
                }

                if (grouped != s.toString()) {
                    editText.setText(grouped)
                    editText.setSelection(grouped.length)
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
        val sp = getSharedPreferences(Constant.SHARED_PREFERENCES_NAME, MODE_PRIVATE)
        val view = LayoutInflater.from(this).inflate(R.layout.dailog_settings, null)
        val checkBoxFixedUid = view.findViewById<MaterialCheckBox>(R.id.checkbox_fixed_uid)
        val layoutUidInput = view.findViewById<TextInputLayout>(R.id.layout_uid_input)
        val editUidInput = view.findViewById<EditText>(R.id.edit_uid_input)
        val itemReselectDir = view.findViewById<View>(R.id.item_reselect_dir)
        val itemGithub = view.findViewById<View>(R.id.item_github)

        var isFixed = sp.getBoolean(Constant.PREF_FIXED_UID, false)
        checkBoxFixedUid.isChecked = isFixed
        
        fun updateInputMode(fixed: Boolean) {
            val value = if (fixed) {
                layoutUidInput.hint = getString(R.string.settings_hint_fixed_uid)
                editUidInput.inputType = InputType.TYPE_CLASS_TEXT
                enableHexGroupedInput(editUidInput)
                sp.getString(Constant.PREF_FIXED_UID_VALUE, "")
            } else {
                layoutUidInput.hint = getString(R.string.settings_hint_random_uid_len)
                editUidInput.inputType = InputType.TYPE_CLASS_NUMBER
                disableHexGroupedInput(editUidInput)
                sp.getString(Constant.PREF_RANDOM_UID_LEN, "4") // Default length 4
            }
            editUidInput.setText(value)
            editUidInput.setSelection(editUidInput.text.length)
        }
        
        updateInputMode(isFixed)

        checkBoxFixedUid.setOnCheckedChangeListener { _, isChecked ->
            isFixed = isChecked
            sp.edit().putBoolean(Constant.PREF_FIXED_UID, isChecked).apply()
            updateInputMode(isChecked)
        }

        editUidInput.addTextChangedListener {
            val text = it?.toString() ?: ""
            if (isFixed) {
                sp.edit().putString(Constant.PREF_FIXED_UID_VALUE, text).apply()
            } else {
                sp.edit().putString(Constant.PREF_RANDOM_UID_LEN, text).apply()
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
            .setOnClickListener { _: View? ->
                dialog?.dismiss()
                disableNfcReaderMode()
            }
        dialog?.setOnDismissListener {
            disableNfcReaderMode()
        }
        dialog?.show()
        dialog?.window?.apply {
            setWindowAnimations(R.style.BottomDialogAnimation)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM)
        }
    }

    private fun showFilenameInputDialog(ndefData: ByteArray) {
        val view: View = LayoutInflater.from(this).inflate(R.layout.dialog_save_ndef_with_name, null)
        dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        val input = view.findViewById<EditText>(R.id.ndef_name_input)
        val defaultName = "${DateFormat.format("yyyy-MM-dd_HH-mm-ss", Date())}.ndef"
        input.setText(defaultName)
        view.findViewById<View?>(R.id.btn_dialog_cancel)!!
            .setOnClickListener { _: View ->
                dialog?.dismiss()
            }
        view.findViewById<View>(R.id.btn_dialog_confirm).setOnClickListener {
            val fileName = input.text.toString()
            FragmentNdefFilePicker.getChosenFolderUri(this)?.let { uri ->
                val root = DocumentFile.fromTreeUri(this, uri)
                val dir = if (FragmentNdefFilePicker.getCurrentPath() == "/" || FragmentNdefFilePicker.getCurrentPath().isEmpty()) root else {
                    FragmentNdefFilePicker.getCurrentPath().trim('/').split("/").fold(root) { parent, name ->
                        parent?.findFile(name)?.takeIf { it.isDirectory }
                    }
                }
                dir?.createFile("application/octet-stream", fileName)?.let { file ->
                    contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                        outputStream.write(ndefData)
                        Toast.makeText(this, R.string.toast_saved_successfully, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ndefFilePicker.refreshFileList()
            dialog?.dismiss()
        }
        dialog?.show()
        dialog?.window?.apply {
            setWindowAnimations(R.style.BottomDialogAnimation)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM)
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
            tag?.let {
                processTag(tag)
            }
        },
    NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE
            ,
        options)
    }

    fun disableNfcReaderMode() {
        nfcAdapter.disableReaderMode(this)
    }
}