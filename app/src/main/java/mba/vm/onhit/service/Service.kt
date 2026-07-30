package mba.vm.onhit.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.os.SharedMemory
import mba.vm.onhit.ICallback
import mba.vm.onhit.IService
import mba.vm.onhit.utils.LogUtils.logE
import mba.vm.onhit.utils.LogUtils.logI

class Service : Service() {

    companion object {
        @Volatile
        private var callback: ICallback? = null
        
        var onStateReceived: ((String?) -> Unit)? = null
        var onDataReceived: ((ByteArray) -> Unit)? = null

        fun isHookConnected(): Boolean = callback != null

        fun toggleRecorder() {
            try {
                callback?.toggleRecorder()
            } catch (e: RemoteException) {
                logE("Failed to call toggleRecorder", e)
            }
        }

        fun requestRecorderState() {
            try {
                callback?.requestRecorderState()
            } catch (e: RemoteException) {
                logE("Failed to call requestRecorderState", e)
            }
        }

        fun restartNfcService() {
            try {
                callback?.restartNfcService()
            } catch (e: RemoteException) {
                logE("Failed to call restartNfcService", e)
            }
        }

        fun emulateTag(uid: ByteArray?, data: ByteArray, tagType: Byte) {
            try {
                val sharedMemory = SharedMemory.create("onHitTagData", data.size)
                val buffer = sharedMemory.mapReadWrite()
                buffer.put(data)
                SharedMemory.unmap(buffer)
                callback?.emulateTag(uid, sharedMemory, data.size, tagType)
                sharedMemory.close()
            } catch (e: Exception) {
                logE("Failed to call emulateTag via SharedMemory", e)
            }
        }

    }

    private val binder = object : IService.Stub() {
        override fun registerCallback(cb: ICallback?) {
            logI("Hook registered callback")
            callback = cb
            requestRecorderState()
        }

        override fun sendRecorderState(state: String?) {
            onStateReceived?.invoke(state)
        }

        override fun sendRecorderData(sharedMemory: SharedMemory?, size: Int) {
            if (sharedMemory == null) return
            try {
                val bytes = ByteArray(size)
                val buffer = sharedMemory.mapReadOnly()
                buffer[bytes]
                SharedMemory.unmap(buffer)
                sharedMemory.close()
                onDataReceived?.invoke(bytes)
            } catch (e: Exception) {
                logE("Failed to read recorder data from SharedMemory", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        logI("OnHitService onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        logI("OnHitService onUnbind")
        callback = null
        return super.onUnbind(intent)
    }
}