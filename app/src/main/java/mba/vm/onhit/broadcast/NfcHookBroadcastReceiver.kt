package mba.vm.onhit.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.hook.core.recorder.TagRecorder
import mba.vm.onhit.hook.core.tag.BaseFakeTag
import mba.vm.onhit.hook.core.tag.TagType
import mba.vm.onhit.hook.nfc.NfcServiceHook.dispatchFakeTag
import mba.vm.onhit.utils.LogUtils.logE
import mba.vm.onhit.utils.LogUtils.logI
import kotlin.system.exitProcess

class NfcHookBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        logI("onReceive: ${intent.action}")
        when (intent.action) {
            Constant.BROADCAST_TAG_EMULATOR_REQUEST -> {
                val uid = intent.getByteArrayExtra("uid")
                val data = intent.getByteArrayExtra("data")
                val tagTypeByte = intent.getByteExtra("tagType", TagType.NDEF.value)
                val tagType = TagType.fromByte(tagTypeByte)
                try {
                    logI("Emulator { uid=${uid?.toHexString() ?: "<no provided>"}, tagType=$tagType }}")
                    data?.let {
                        val tag = BaseFakeTag.create(tagType)
                        tag.init(uid, it)
                        dispatchFakeTag(tag)
                    } ?: logE("No data provided")
                } catch (e: Exception) {
                    logE("Failed to dispatchFakeTag: $e", e)
                }
            }

            Constant.BROADCAST_TAG_RECORDER_STATE_REQUEST -> {
                val responseIntent = Intent(Constant.BROADCAST_TAG_RECORDER_STATE_RESPONSE).apply {
                    `package` = BuildConfig.APPLICATION_ID
                    putExtra("state", TagRecorder.state.toString())
                }
                context.sendBroadcast(responseIntent)
            }

            Constant.BROADCAST_TOGGLE_TAG_RECORDER_REQUEST -> {
                logI("Toggling Tag Recorder")
                TagRecorder.toggleRecorder()
            }

            Constant.BROADCAST_RESTART_NFC_SERVICE -> {
                logI("Restarting NFC Service process...")
                exitProcess(0)
            }
        }
    }
}