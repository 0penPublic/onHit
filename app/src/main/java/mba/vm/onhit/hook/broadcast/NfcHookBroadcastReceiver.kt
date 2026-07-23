package mba.vm.onhit.hook.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.core.recorder.TagRecorder
import mba.vm.onhit.core.recorder.TagRecorder.startRecorder
import mba.vm.onhit.core.recorder.TagRecorder.stopRecorder
import mba.vm.onhit.core.tag.BaseFakeTag
import mba.vm.onhit.hook.nfc.NfcServiceHook.dispatchFakeTag
import mba.vm.onhit.utils.HexUtils.encodeHex
import mba.vm.onhit.utils.LogUtils.logE
import mba.vm.onhit.utils.LogUtils.logI

class NfcHookBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        logI("onReceive: ${intent.action}")
        when (intent.action) {
            Constant.BROADCAST_TAG_EMULATOR_REQUEST -> {
                val uid = intent.getByteArrayExtra("uid")
                val data = intent.getByteArrayExtra("data")
                val tagType = intent.getStringExtra("tagType") ?: "ndef"
                try {
                    logI("Emulator { uid=${encodeHex(uid)}, tagType=$tagType }}")
                    if (uid != null && data != null) {
                        BaseFakeTag.create(tagType)?.let { tag ->
                            tag.init(uid, data)
                            dispatchFakeTag(tag)
                        }
                    }
                } catch (e: Exception) {
                    logE("Failed to dispatchFakeTag: $e")
                }
            }

            Constant.BROADCAST_TAG_RECORDER_STATE_REQUEST -> {
                val responseIntent = Intent(Constant.BROADCAST_TAG_RECORDER_STATE_RESPONSE).apply {
                    `package` = BuildConfig.APPLICATION_ID
                    putExtra("state", TagRecorder.state.toString())
                }
                context.sendBroadcast(responseIntent)
            }

            Constant.BROADCAST_START_TAG_RECORDER_REQUEST -> {
                logI("Starting Tag Recorder")
                startRecorder()
            }

            Constant.BROADCAST_STOP_TAG_RECORDER_REQUEST -> {
                logI("Stopping Tag Recorder")
                stopRecorder()
            }
        }
    }
}