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

package com.tencent.tinker.loader.shareutil;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shwenzhang on 16/3/10.
 */
public class ShareTinkerInternals {
    private static final String TAG = "TinkerInternals";

    /**
     * or you may just hardcode them in your app
     */
    private static String processName = null;

    private static String tinkerID = null;

    private static final boolean VM_IS_ART = isVmArt(System.getProperty("java.vm.version"));

    public static boolean isVmArt() {
        return VM_IS_ART;
    }

    public static boolean isNullOrNil(final String object) {
        if ((object == null) || (object.length() <= 0)) {
            return true;
        }
        return false;
    }

    /**
     * check patch file signature and TINKER_ID
     *
     * @param context
     * @param patchFile
     * @param securityCheck
     * @return
     */
    public static int checkSignatureAndTinkerID(Context context, File patchFile, ShareSecurityCheck securityCheck) {
        if (!securityCheck.verifyPatchMetaSignature(patchFile)) {
            return ShareConstants.ERROR_PACKAGE_CHECK_SIGNATURE_FAIL;
        }

        String oldTinkerId = getManifestTinkerID(context);
        if (oldTinkerId == null) {
            return ShareConstants.ERROR_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND;
        }

        HashMap<String, String> properties = securityCheck.getPackagePropertiesIfPresent();

        if (properties == null) {
            return ShareConstants.ERROR_PACKAGE_CHECK_PACKAGE_META_NOT_FOUND;
        }

        String patchTinkerId = properties.get(ShareConstants.TINKER_ID);
        if (patchTinkerId == null) {
            return ShareConstants.ERROR_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND;
        }
        if (!oldTinkerId.equals(patchTinkerId)) {
            return ShareConstants.ERROR_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL;
        }
        return ShareConstants.ERROR_PACKAGE_CHECK_OK;
    }

    public static String getManifestTinkerID(Context context) {
        if (tinkerID != null) {
            return tinkerID;
        }
        try {
            ApplicationInfo appInfo = context.getPackageManager()
                .getApplicationInfo(context.getPackageName(),
                    PackageManager.GET_META_DATA);

            Object object = appInfo.metaData.get(ShareConstants.TINKER_ID);
            if (object != null) {
                tinkerID = String.valueOf(object);
            } else {
                tinkerID = null;
            }
        } catch (Exception e) {
//                e.printStackTrace();
            return null;
        }
        return tinkerID;
    }

    public static boolean isTinkerEnabledForDex(int flag) {
        return (flag & ShareConstants.TINKER_DEX_MASK) != 0;
    }

    public static boolean isTinkerEnabledForNativeLib(int flag) {
        return (flag & ShareConstants.TINKER_NATIVE_LIBRARY_MASK) != 0;
    }

    public static String getTypeString(int type) {
        switch (type) {
            case ShareConstants.TYPE_DEX:
                return "dex";
            case ShareConstants.TYPE_LIBRARY:
                return "lib";
            case ShareConstants.TYPE_PATCH_FILE:
                return "patch_file";
            case ShareConstants.TYPE_PATCH_INFO:
                return "patch_info";
            default:
                return "unknown";
        }
    }

    public static boolean isTinkerEnabled(int flag) {
        return (flag != ShareConstants.TINKER_DISABLE);
    }

    public static boolean isInMainProcess(Context context) {
        String pkgName = context.getPackageName();
        String processName = getProcessName(context);
        if (processName == null || processName.length() == 0) {
            processName = "";
        }

        return pkgName.equals(processName);
    }

    public static void killAllOtherProcess(Context context) {
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        // NOTE: getRunningAppProcess() ONLY GIVE YOU THE PROCESS OF YOUR OWN PACKAGE IN ANDROID M
        // BUT THAT'S ENOUGH HERE
        for (ActivityManager.RunningAppProcessInfo ai : am.getRunningAppProcesses()) {
            // KILL OTHER PROCESS OF MINE
            if (ai.uid == android.os.Process.myUid() && ai.pid != android.os.Process.myPid()) {
                android.os.Process.killProcess(ai.pid);
            }
        }

    }

    /**
     * add process name cache
     *
     * @param context
     * @return
     */
    public static String getProcessName(final Context context) {
        if (processName != null) {
            return processName;
        }
        //will not null
        processName = com.tencent.tinker.loader.shareutil.ShareTinkerInternals.getProcessNameInternal(context);
        return processName;
    }


    private static String getProcessNameInternal(final Context context) {
        int myPid = android.os.Process.myPid();

        if (context == null || myPid <= 0) {
            return "";
        }

        ActivityManager.RunningAppProcessInfo myProcess = null;
        ActivityManager activityManager =
            (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        try {
            for (ActivityManager.RunningAppProcessInfo process : activityManager.getRunningAppProcesses()) {
                if (process.pid == myPid) {
                    myProcess = process;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (myProcess != null) {
            return myProcess.processName;
        }

        byte[] b = new byte[128];
        FileInputStream in = null;
        try {
            in = new FileInputStream("/proc/" + myPid + "/cmdline");
            int len = in.read(b);
            if (len > 0) {
                for (int i = 0; i < len; i++) { // lots of '0' in tail , remove them
                    if (b[i] > 128 || b[i] <= 0) {
                        len = i;
                        break;
                    }
                }
                return new String(b, 0, len);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
            }
        }

        return "";
    }

    /**
     * vm whether it is art
     * @return
     */
    private static boolean isVmArt(String versionString) {
        boolean isArt = false;
        if (versionString != null) {
            Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)?").matcher(versionString);
            if (matcher.matches()) {
                try {
                    int major = Integer.parseInt(matcher.group(1));
                    int minor = Integer.parseInt(matcher.group(2));
                    isArt = (major > 2)
                        || ((major == 2)
                        && (minor >= 1));
                } catch (NumberFormatException e) {
                    // let isMultidexCapable be false
                }
            }
        }
        return isArt;
    }
}
