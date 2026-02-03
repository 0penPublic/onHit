package mba.vm.onhit.hook.boardcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import androidx.core.content.IntentCompat
import mba.vm.onhit.Constant
import mba.vm.onhit.hook.NfcDispatchManagerHook.log
import mba.vm.onhit.hook.NfcServiceHook

class NfcServiceHookBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        log("${this.javaClass.name} onReceive: ${intent.action}")
        when (intent.action) {
            Constant.BROADCAST_TAG_EMULATOR_REQUEST -> {
                val uid = intent.getByteArrayExtra("uid")
                val ndef = IntentCompat.getParcelableExtra(intent, "ndef", NdefMessage::class.java)
                uid?.let {
                    NfcServiceHook.dispatchFakeTag(uid, ndef)
                }
            }
        }
    }
}