package mba.vm.onhit.hook.boardcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import mba.vm.onhit.Constant
import mba.vm.onhit.core.tag.BaseFakeTag.Companion.TAG_TYPE_MAPPING
import mba.vm.onhit.hook.NfcDispatchManagerHook.log
import mba.vm.onhit.hook.NfcServiceHook.dispatchFakeTag

class NfcServiceHookBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        log("${this.javaClass.name} onReceive: ${intent.action}")
        when (intent.action) {
            Constant.BROADCAST_TAG_EMULATOR_REQUEST -> {
                val uid = intent.getByteArrayExtra("uid")
                val data = intent.getByteArrayExtra("data")
                val tagType = intent.getStringExtra("tagType") ?: "ndef"
                try {
                    if (uid != null && data != null) {
                        val tag = TAG_TYPE_MAPPING[tagType]?.init(uid, data)
                        tag?.let {
                            dispatchFakeTag(it)
                        }
                    }
                } catch (e: Exception) {
                    log("Failed to dispatchFakeTag: $e")
                }
            }
        }
    }
}