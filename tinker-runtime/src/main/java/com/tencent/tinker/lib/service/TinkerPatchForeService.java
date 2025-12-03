package com.tencent.tinker.lib.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import com.tencent.tinker.lib.IForeService;

public class TinkerPatchForeService extends Service {
    public TinkerPatchForeService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        // It's unwelcome to restart owner process of this service automatically for users.
        // So return START_NOT_STICKY here to prevent this behavior.
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new IForeService.Stub() {
            @Override
            public void startme() throws RemoteException {
                //占位使用，不做具体操作
            }
        };
    }
}
