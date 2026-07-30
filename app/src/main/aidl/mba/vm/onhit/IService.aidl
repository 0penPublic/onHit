package mba.vm.onhit;

import mba.vm.onhit.ICallback;
import android.os.SharedMemory;

interface IService {
    void registerCallback(ICallback callback);
    void sendRecorderState(String state);
    void sendRecorderData(in SharedMemory sharedMemory, int size);
}