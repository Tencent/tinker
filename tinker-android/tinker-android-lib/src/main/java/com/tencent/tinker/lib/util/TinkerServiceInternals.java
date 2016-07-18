/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.lib.util;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.util.Log;

import com.tencent.tinker.lib.service.TinkerPatchService;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.util.List;

/**
 * Created by shwenzhang on 16/3/10.
 */
public class TinkerServiceInternals extends ShareTinkerInternals {
    private static final String TAG = "TinkerServiceInternals";

    /**
     * or you may just hardcode them in your app
     */
    private static String patchServiceProcessName = null;

    public static void killTinkerPatchServiceProcess(Context context) {
        String serverProcessName = getTinkerPatchServiceName(context);
        if (serverProcessName == null) {
            return;
        }

        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningAppProcessInfo appProcess : am.getRunningAppProcesses()) {
            String processName = appProcess.processName; // 进程名
            if (processName.equals(serverProcessName)) {
                android.os.Process.killProcess(appProcess.pid);
            }
        }

    }

    public static boolean isTinkerPatchServiceRunning(Context context) {
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String serverName = getTinkerPatchServiceName(context);
        if (serverName == null) {
            return false;
        }
        try {
            // 通过调用ActivityManager的getRunningAppProcesses()方法获得系统里所有正在运行的进程
            List<ActivityManager.RunningAppProcessInfo> appProcessList = am
                .getRunningAppProcesses();

            for (ActivityManager.RunningAppProcessInfo appProcess : appProcessList) {
                String processName = appProcess.processName; // 进程名
                if (processName.equals(serverName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "isTinkerPatchServiceRunning Exception: " + e.toString());
            return false;
        } catch (Error e) {
            Log.e(TAG, "isTinkerPatchServiceRunning Error: " + e.toString());
            return false;
        }

        return false;
    }


    public static String getTinkerPatchServiceName(final Context context) {
        if (patchServiceProcessName != null) {
            return patchServiceProcessName;
        }
        //may be null, and you may like to hardcode instead
        String serviceName = TinkerServiceInternals.getServiceProcessName(context, TinkerPatchService.class);
        if (serviceName == null) {
            return null;
        }
        patchServiceProcessName = serviceName;
        return patchServiceProcessName;
    }

    /**
     * add service cache
     *
     * @param context
     * @return boolean
     */
    public static boolean isInTinkerPatchServiceProcess(Context context) {
        String process = getProcessName(context);

        String service = TinkerServiceInternals.getTinkerPatchServiceName(context);
        if (service == null || service.length() == 0) {
            return false;
        }
        return process.equals(service);
    }

    private static String getServiceProcessName(Context context, Class<? extends Service> serviceClass) {
        PackageManager packageManager = context.getPackageManager();

        ComponentName component = new ComponentName(context, serviceClass);
        ServiceInfo serviceInfo;
        try {
            serviceInfo = packageManager.getServiceInfo(component, 0);
        } catch (PackageManager.NameNotFoundException ignored) {
            // Service is disabled.
            return null;
        }

        return serviceInfo.processName;
    }

}
