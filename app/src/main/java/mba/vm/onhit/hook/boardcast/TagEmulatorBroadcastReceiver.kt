package mba.vm.onhit.hook.boardcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import de.robv.android.xposed.XposedBridge
import mba.vm.onhit.Constant
import mba.vm.onhit.hook.NfcServiceHook

class TagEmulatorBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        XposedBridge.log("TagEmulatorBroadcastReceiver onReceive")
        when (intent.action) {
            Constant.BROADCAST_TAG_EMULATOR_REQUEST -> {
                val uid = intent.getByteArrayExtra("uid")
                val ndef = intent.getParcelableExtra("ndef", NdefMessage::class.java)
                uid?.let {
                    NfcServiceHook.dispatchFakeTag(uid, ndef)
                }
            }
        }
    }
}