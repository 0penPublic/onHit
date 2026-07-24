package mba.vm.onhit.core.recorder

import android.content.Intent
import android.os.Bundle
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import io.github.kyuubiran.ezxhelper.core.misc.paramTypes
import io.github.kyuubiran.ezxhelper.core.misc.params
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.core.model.TagTechSpec
import mba.vm.onhit.core.model.TagTechnology
import mba.vm.onhit.core.recorder.trace.TagTrace
import mba.vm.onhit.core.recorder.trace.TagTraceCodec
import mba.vm.onhit.hook.nfc.NfcServiceHook.sendBroadcast
import mba.vm.onhit.utils.HexUtils.encodeHex
import mba.vm.onhit.utils.LogUtils.logI
import kotlin.reflect.KClass

object TagRecorder {
    var state: TagRecorderState = TagRecorderState.IDLE
    var currentRecordTag: Any? = null
    private val hookedClasses = mutableSetOf<KClass<*>>()
    var currentTagTrace: TagTrace? = null

    fun onTagEndpointDispatch(tagEndpoint: Any) {
        if (state == TagRecorderState.IDLE) return
        if (state == TagRecorderState.WAITING) {
            currentRecordTag = tagEndpoint
            val objectHelper = tagEndpoint.objectHelper()
            val uid = objectHelper.invokeMethod(
                "getUid",
                ByteArray::class.java,
                paramTypes(),
                params()
            ) as ByteArray
            val techList: IntArray = objectHelper.invokeMethod(
                "getTechList",
                IntArray::class.java,
                paramTypes(),
                params()
            ) as IntArray
            val techExtras = objectHelper.invokeMethod(
                "getTechExtras",
                Array<Bundle>::class.java,
                paramTypes(),
                params()
            ) as Array<*>
            val technologies = mutableListOf<TagTechSpec>()
            for ((index, value) in techList.withIndex()) {
                technologies.add(
                    TagTechSpec(
                        TagTechnology.fromInt(value),
                    techExtras[index] as Bundle
                ))
            }
            currentTagTrace = TagTrace(
                uid,
                technologies.toTypedArray()
            )
            state = TagRecorderState.RECORDING
            val tagClass = tagEndpoint::class
            if (hookedClasses.add(tagClass)) {
                MethodFinder.fromClass(tagClass.java)
                    .filterByName("transceive")
                    .first()
                    .createHook {
                        after { param ->
                            if (state == TagRecorderState.RECORDING && param.thisObject == currentRecordTag) {
                                val args = param.args
                                val cmd = args[0] as ByteArray
                                val raw = args[1] as Boolean
                                val returnCode = args[2] as IntArray
                                val resp = param.result as? ByteArray?

                                currentTagTrace?.addExchange(
                                    cmd,
                                    raw,
                                    returnCode,
                                    resp
                                )
                                logI("Recorded exchange { " +
                                        "cmd=${encodeHex(cmd)}, " +
                                        "raw=$raw, " +
                                        "returnCode=${returnCode.contentToString()}, " +
                                        "resp=${encodeHex(resp)} }")
                            }
                        }
                    }
            }
        } else if (isRecording() && tagEndpoint !== currentRecordTag) {
            stopRecorder()
        }
    }

    fun startRecorder() {
        if (state == TagRecorderState.IDLE) {
            state = TagRecorderState.WAITING
            logI("TagRecorder waiting for tag...")
        }
    }

    fun toggleRecorder() {
        if (state == TagRecorderState.IDLE) {
            startRecorder()
        } else {
            stopRecorder()
        }
    }

    fun stopRecorder() {
        logI("Stopping recorder. Recorded ${currentTagTrace?.transceiveData?.size ?: 0} exchanges.")
        currentTagTrace?.let {
            sendBroadcast(Intent(Constant.BROADCAST_TAG_RECORDER_RESPONSE).apply {
                `package` = BuildConfig.APPLICATION_ID
                putExtra("data", TagTraceCodec.encode(it))
            })
        }
        state = TagRecorderState.IDLE
        currentRecordTag = null
        currentTagTrace = null
    }

    fun isRecording(): Boolean = state == TagRecorderState.RECORDING

    enum class TagRecorderState {
        IDLE,
        WAITING,
        RECORDING
    }
}