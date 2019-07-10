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
    public IBinder onBind(Intent intent) {
        return new IForeService.Stub() {
            @Override
            public void startme() throws RemoteException {
                //占位使用，不做具体操作
            }
        };
    }
}
