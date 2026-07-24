package mba.vm.onhit.core.recorder

import android.content.Context
import mba.vm.onhit.R

object TagRecorderStateHelper {
    fun TagRecorder.TagRecorderState.getText(context: Context): String {
        return when (this) {
            TagRecorder.TagRecorderState.IDLE -> context.getString(R.string.recorder_state_idle)
            TagRecorder.TagRecorderState.WAITING -> context.getString(R.string.recorder_state_waiting)
            TagRecorder.TagRecorderState.RECORDING -> context.getString(R.string.recorder_state_recording)
        }
    }

    fun toRecorderStateText(context: Context, state: String?): String {
        return try {
            if (state == null) context.getString(R.string.unknown)
            else TagRecorder.TagRecorderState.valueOf(state).getText(context)
        } catch (_: Exception) {
            state ?: context.getString(R.string.unknown)
        }
    }
}
