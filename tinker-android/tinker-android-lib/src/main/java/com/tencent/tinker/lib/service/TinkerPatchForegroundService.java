/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */

package com.tencent.tinker.lib.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.tencent.tinker.lib.util.TinkerLog;

public class TinkerPatchForegroundService extends Service {
    private static final String TAG = "Tinker.PatchFgService";

    private static final int NOTIFICATION_ID = 0x110;
    private static final String NOTIFICATION_CHANNEL_ID = "tinker_patch_service";
    private static final String NOTIFICATION_CHANNEL_NAME = "tinker_patch_service";

    @Override
    public void onCreate() {
        super.onCreate();
        handleForegroundNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleForegroundNotification();
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleForegroundNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel channel = nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID);
                    if (channel == null) {
                        channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                                NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
                        nm.createNotificationChannel(channel);
                    }
                }
                final Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID).build();
                startForeground(NOTIFICATION_ID, notification);
            } else {
                startForeground(NOTIFICATION_ID, new Notification());
            }
        } catch (Throwable thr) {
            TinkerLog.e(TAG, "fail to startForeground: " + thr.getMessage());
            try {
                stopForeground(true);
            } catch (Throwable ignored) {
                // Ignored.
            }
        }
    }
}
