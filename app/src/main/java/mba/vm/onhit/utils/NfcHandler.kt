package mba.vm.onhit.utils

import android.app.Activity
import android.app.Dialog
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.widget.Toast
import mba.vm.onhit.R

class NfcHandler(private val activity: Activity) {
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var nfcDialog: Dialog? = null
    private var pendingNdefData: ByteArray? = null
    private var isWritingMode = false
    
    var onNdefRead: ((ByteArray) -> Unit)? = null

    fun isEnabled() = nfcAdapter?.isEnabled == true

    fun startRead() {
        if (!isEnabled()) {
            Toast.makeText(activity, R.string.toast_nfc_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        isWritingMode = false
        nfcDialog = DialogHelper.showNfcDialog(
            activity,
            activity.getString(R.string.read_nfc_tag),
            activity.getString(R.string.saving_ndef_prompt)
        ) { stopDiscovery() }

        nfcAdapter?.enableReaderMode(activity, ::onTagDiscovered,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE, null)
    }

    fun startWrite(data: ByteArray) {
        if (!isEnabled()) {
            Toast.makeText(activity, R.string.toast_nfc_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        pendingNdefData = data
        isWritingMode = true
        nfcDialog = DialogHelper.showNfcDialog(
            activity,
            activity.getString(R.string.write_nfc_tag),
            activity.getString(R.string.writing_ndef_prompt)
        ) {
            stopDiscovery()
            isWritingMode = false
        }

        nfcAdapter?.enableReaderMode(activity, ::onTagDiscovered,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE, null)
    }

    private fun onTagDiscovered(tag: Tag?) {
        activity.runOnUiThread {
            if (isWritingMode) {
                tag?.let { writeNdefToTag(it, pendingNdefData ?: return@runOnUiThread) }
            } else {
                stopDiscovery()
                nfcDialog?.dismiss()
                tag?.let {
                    val ndef = Ndef.get(it)
                    val message = try { ndef?.cachedNdefMessage } catch (_: Exception) { null }
                    if (message != null) {
                        onNdefRead?.invoke(message.toByteArray())
                    } else {
                        Toast.makeText(activity, R.string.toast_tag_not_support_ndef_format, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun writeNdefToTag(tag: Tag, data: ByteArray) {
        try {
            val message = NdefMessage(data)
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    Toast.makeText(activity, "Tag is not writable", Toast.LENGTH_SHORT).show()
                    closeAction()
                    return
                }
                if (ndef.maxSize < data.size) {
                    Toast.makeText(activity, R.string.toast_file_too_large, Toast.LENGTH_SHORT).show()
                    closeAction()
                    return
                }
                ndef.writeNdefMessage(message)
                Toast.makeText(activity, R.string.toast_write_success, Toast.LENGTH_SHORT).show()
                closeAction()
            } else {
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message)
                    Toast.makeText(activity, R.string.toast_write_success, Toast.LENGTH_SHORT).show()
                    closeAction()
                } else {
                    Toast.makeText(activity, R.string.toast_tag_not_support_ndef_format, Toast.LENGTH_SHORT).show()
                    closeAction()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.toast_write_failed, e.message), Toast.LENGTH_SHORT).show()
            closeAction()
        }
    }

    private fun closeAction() {
        isWritingMode = false
        nfcDialog?.dismiss()
        stopDiscovery()
    }

    fun stopDiscovery() {
        nfcAdapter?.disableReaderMode(activity)
    }
}
