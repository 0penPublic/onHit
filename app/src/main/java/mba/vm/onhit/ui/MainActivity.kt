package mba.vm.onhit.ui

import android.app.AlertDialog
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
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
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.fragment.NavHostFragment
import mba.vm.onhit.R
import mba.vm.onhit.databinding.ActivityMainBinding
import mba.vm.onhit.ui.FragmentNdefFilePicker.Companion.currentPath
import java.util.Date


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var ndefFilePicker: FragmentNdefFilePicker
    private var dialog: AlertDialog? = null

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
            Toast.makeText(this, R.string.toast_todo, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_bar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.add_ndef_record -> {
                FragmentNdefFilePicker.getChosenFolderUri(this)?.let { _ ->
                    showNfcDialog()
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

    private fun showNfcDialog() {
        enableNfcReaderMode()
        val view: View = LayoutInflater.from(this).inflate(R.layout.dailog_add_ndef_record, null)
        dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        
        view.findViewById<View?>(R.id.btn_dialog_cancel)!!
            .setOnClickListener { _: View? ->
                dialog?.dismiss()
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
                val dir = if (currentPath == "/" || currentPath.isEmpty()) root else {
                    currentPath.trim('/').split("/").fold(root) { parent, name ->
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
            dialog?.dismiss()
            val ndef = Ndef.get(tag)
            ndef?.let {
                ndef.connect()
                ndef.ndefMessage?.let {
                    showFilenameInputDialog(it.toByteArray())
                }
                ndef.close()
                disableNfcReaderMode()
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