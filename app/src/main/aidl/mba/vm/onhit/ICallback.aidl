package mba.vm.onhit;

import android.os.SharedMemory;

interface ICallback {
    void toggleRecorder();
    void requestRecorderState();
    void restartNfcService();
    void emulateTag(in byte[] uid, in SharedMemory data, int size, byte tagType);
}