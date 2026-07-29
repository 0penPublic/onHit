package mba.vm.onhit.hook.core.recorder

import android.content.Intent
import android.os.Bundle
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import io.github.kyuubiran.ezxhelper.core.misc.paramTypes
import io.github.kyuubiran.ezxhelper.core.misc.params
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.hook.nfc.NfcServiceHook.sendBroadcast
import mba.vm.onhit.model.TagTechSpec
import mba.vm.onhit.model.TagTechnology
import mba.vm.onhit.model.trace.TagTrace
import mba.vm.onhit.model.trace.TagTraceCodec
import mba.vm.onhit.utils.LogUtils.logE
import mba.vm.onhit.utils.LogUtils.logI

object TagRecorder {
    data class Session(
        val tagEndpoint: Any,
        val tagTrace: TagTrace
    ) {
        fun recordTransceive(cmd: ByteArray, raw: Boolean, returnCode: IntArray, resp: ByteArray?) {
            tagTrace.addExchange(cmd, raw, returnCode, resp)
            logI("Recorded exchange { " +
                    "cmd=${cmd.toHexString()}, " +
                    "raw=$raw, " +
                    "returnCode=${returnCode.contentToString()}, " +
                    "resp=${resp?.toHexString() ?: "<no response>"} }")
        }
    }

    @Volatile
    var state: TagRecorderState = TagRecorderState.IDLE
        private set
    private val hookedClassNames = mutableSetOf<String>()

    @Volatile
    private var activeSession: Session? = null

    @Synchronized
    fun onTagEndpointDispatch(tagEndpoint: Any) {
        when (state) {
            TagRecorderState.WAITING -> {
                try {
                    val objectHelper = tagEndpoint.objectHelper()
                    val uid = objectHelper
                        .invokeWithEmptyParams("getUid", ByteArray::class.java)
                        ?: byteArrayOf()

                    // 系统底层 getTechList() 返回的是 int[] (IntArray)，而非 Integer[]
                    val techList = objectHelper
                        .invokeWithEmptyParams("getTechList", IntArray::class.java)
                    val techExtras = objectHelper
                        .invokeWithEmptyParams("getTechExtras", Array<Bundle>::class.java)

                    val techSpec = mutableListOf<TagTechSpec>()

                    if (techList != null && !techExtras.isNullOrEmpty()) {
                        for ((index, value) in techList.withIndex()) {
                            val extra = techExtras.getOrNull(index)
                            techSpec.add(
                                TagTechSpec(
                                    TagTechnology.fromInt(value),
                                    extra ?: Bundle.EMPTY
                                )
                            )
                        }
                    }
                    val trace = TagTrace(uid, techSpec.toTypedArray())
                    activeSession = Session(tagEndpoint, trace)
                    ensureClassHooked(tagEndpoint::class.java)
                    state = TagRecorderState.RECORDING
                    logI("Started recording session for TagEndpoint: ${tagEndpoint.javaClass.name}")
                } catch (e: Throwable) {
                    logE("Failed to process TagEndpoint dispatch", e)
                    stopRecorder()
                }
            }
            TagRecorderState.RECORDING -> {
                // 如果发现刷入了新的 TagEndpoint，停止当前录制
                if (tagEndpoint !== activeSession?.tagEndpoint) {
                    logI("Different TagEndpoint detected, stopping current recording.")
                    stopRecorder()
                }
            }
            else -> Unit
        }
    }

    @Synchronized
    private fun ensureClassHooked(clazz: Class<*>) {
        val className = clazz.name
        if (hookedClassNames.add(className)) {
            runCatching {
                MethodFinder.fromClass(clazz)
                    .filterByName("transceive")
                    // 明确匹配参数类型，避免误操作其他重载方法
                    .filterByParamTypes(ByteArray::class.java, Boolean::class.java, IntArray::class.java)
                    .first()
                    .createHook {
                        after { param ->
                            val currentSession = activeSession ?: return@after

                            if (param.thisObject === currentSession.tagEndpoint) {
                                val args = param.args
                                // 使用安全类型转换，防止签名不匹配导致的 Crash
                                val cmd = args.getOrNull(0) as? ByteArray
                                val raw = args.getOrNull(1) as? Boolean
                                val returnCode = args.getOrNull(2) as? IntArray
                                val resp = param.result as? ByteArray

                                if (cmd != null && raw != null && returnCode != null) {
                                    currentSession.recordTransceive(
                                        cmd = cmd,
                                        raw = raw,
                                        returnCode = returnCode,
                                        resp = resp
                                    )
                                }
                            }
                        }
                    }
            }.onFailure { e ->
                hookedClassNames.remove(className)
                logE("Failed to hook transceive on class $className", e)
            }
        }
    }

    fun <T> ObjectHelper.invokeWithEmptyParams(methodName: String, returnType: Class<T>): T? =
        runCatching {
            returnType.cast(invokeMethod(methodName, returnType, paramTypes(), params()))
        }.getOrNull()

    @Synchronized
    fun startRecorder() {
        if (state == TagRecorderState.IDLE) {
            state = TagRecorderState.WAITING
            logI("TagRecorder waiting for tag...")
        }
    }

    @Synchronized
    fun toggleRecorder() {
        if (state == TagRecorderState.IDLE) {
            startRecorder()
        } else {
            stopRecorder()
        }
    }

    @Synchronized
    fun stopRecorder() {
        activeSession?.let { session ->
            logI("Stopping recorder. Recorded ${session.tagTrace.transceiveData.size} exchanges.")
            runCatching {
                sendBroadcast(Intent(Constant.BROADCAST_TAG_RECORDER_RESPONSE).apply {
                    `package` = BuildConfig.APPLICATION_ID
                    putExtra("data", TagTraceCodec.encode(session.tagTrace))
                })
            }.onFailure { e ->
                logE("Failed to send broadcast on stopRecorder", e)
            }
        }
        state = TagRecorderState.IDLE
        activeSession = null
    }

    enum class TagRecorderState {
        IDLE,
        WAITING,
        RECORDING
    }
}