/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.loader.shareutil;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by zhangshaowen on 16/3/10.
 */
public class ShareTinkerInternals {
    private static final String TAG = "Tinker.TinkerInternals";
    private static final boolean VM_IS_ART = isVmArt(System.getProperty("java.vm.version"));
    /**
     * or you may just hardcode them in your app
     */
    private static String processName = null;
    private static String tinkerID = null;

    public static boolean isVmArt() {
        return VM_IS_ART || Build.VERSION.SDK_INT >= 21;
    }

    public static boolean isNullOrNil(final String object) {
        if ((object == null) || (object.length() <= 0)) {
            return true;
        }
        return false;
    }

    /**
     * thinker package check
     * @param context
     * @param tinkerFlag
     * @param patchFile
     * @param securityCheck
     * @return
     */
    public static int checkTinkerPackage(Context context, int tinkerFlag, File patchFile, ShareSecurityCheck securityCheck) {
        int returnCode = checkSignatureAndTinkerID(context, patchFile, securityCheck);
        if (returnCode == ShareConstants.ERROR_PACKAGE_CHECK_OK) {
            returnCode = checkPackageAndTinkerFlag(securityCheck, tinkerFlag);
        }
        return returnCode;
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
            Log.e(TAG, "tinkerId is not equal, base is " + oldTinkerId +", but patch is " + patchTinkerId);
            return ShareConstants.ERROR_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL;
        }
        return ShareConstants.ERROR_PACKAGE_CHECK_OK;
    }


    public static int checkPackageAndTinkerFlag(ShareSecurityCheck securityCheck, int tinkerFlag) {
        if (isTinkerEnabledAll(tinkerFlag)) {
            return ShareConstants.ERROR_PACKAGE_CHECK_OK;
        }
        HashMap<String, String> metaContentMap = securityCheck.getMetaContentMap();
        //check dex
        boolean dexEnable = isTinkerEnabledForDex(tinkerFlag);
        if (!dexEnable && metaContentMap.containsKey(ShareConstants.DEX_META_FILE)) {
            return ShareConstants.ERROR_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT;
        }
        //check native library
        boolean nativeEnable = isTinkerEnabledForNativeLib(tinkerFlag);
        if (!nativeEnable && metaContentMap.containsKey(ShareConstants.SO_META_FILE)) {
            return ShareConstants.ERROR_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT;
        }
        //check resource
        boolean resEnable = isTinkerEnabledForResource(tinkerFlag);
        if (!resEnable && metaContentMap.containsKey(ShareConstants.RES_META_FILE)) {
            return ShareConstants.ERROR_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT;
        }

        return ShareConstants.ERROR_PACKAGE_CHECK_OK;
    }

    /**
     * not like {@cod ShareSecurityCheck.getPackagePropertiesIfPresent}
     * we don't check Signatures or other files, we just get the package meta's properties directly
     * @param patchFile
     * @return
     */
    public static Properties fastGetPatchPackageMeta(File patchFile) {
        if (patchFile == null || !patchFile.isFile() || patchFile.length() == 0) {
            Log.e(TAG, "patchFile is illegal");
            return null;
        }
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(patchFile);
            ZipEntry packageEntry = zipFile.getEntry(ShareConstants.PACKAGE_META_FILE);
            if (packageEntry == null) {
                Log.e(TAG, "patch meta entry not found");
                return null;
            }
            InputStream inputStream = null;
            try {
                inputStream = zipFile.getInputStream(packageEntry);
                Properties properties = new Properties();
                properties.load(inputStream);
                return properties;
            } finally {
                SharePatchFileUtil.closeQuietly(inputStream);
            }
        } catch (IOException e) {
            Log.e(TAG, "fastGetPatchPackageMeta exception:" + e.getMessage());
            return null;
        } finally {
            SharePatchFileUtil.closeZip(zipFile);
        }
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
            Log.e(TAG, "getManifestTinkerID exception:" + e.getMessage());
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

    public static boolean isTinkerEnabledForResource(int flag) {
        //FIXME:res flag depends dex flag
        return (flag & ShareConstants.TINKER_RESOURCE_MASK) != 0;
    }

    public static String getTypeString(int type) {
        switch (type) {
            case ShareConstants.TYPE_DEX:
                return "dex";
            case ShareConstants.TYPE_DEX_FOR_ART:
                return "dex_art";
            case ShareConstants.TYPE_DEX_OPT:
                return "dex_opt";
            case ShareConstants.TYPE_LIBRARY:
                return "lib";
            case ShareConstants.TYPE_PATCH_FILE:
                return "patch_file";
            case ShareConstants.TYPE_PATCH_INFO:
                return "patch_info";
            case ShareConstants.TYPE_RESOURCE:
                return "resource";
            default:
                return "unknown";
        }
    }

    /**
     * you can set Tinker disable in runtime at some times!
     * @param context
     */
    public static void setTinkerDisableWithSharedPreferences(Context context) {
        SharedPreferences sp = context.getSharedPreferences(ShareConstants.TINKER_SHARE_PREFERENCE_CONFIG, Context.MODE_MULTI_PROCESS);
        sp.edit().putBoolean(ShareConstants.TINKER_ENABLE_CONFIG, false).commit();
    }

    /**
     * can't load or receive any patch!
     * @param context
     * @return
     */
    public static boolean isTinkerEnableWithSharedPreferences(Context context) {
        SharedPreferences sp = context.getSharedPreferences(ShareConstants.TINKER_SHARE_PREFERENCE_CONFIG, Context.MODE_MULTI_PROCESS);
        return sp.getBoolean(ShareConstants.TINKER_ENABLE_CONFIG, true);
    }

    public static boolean isTinkerEnabled(int flag) {
        return (flag != ShareConstants.TINKER_DISABLE);
    }

    public static boolean isTinkerEnabledAll(int flag) {
        return (flag == ShareConstants.TINKER_ENABLE_ALL);
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
        processName = getProcessNameInternal(context);
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
            Log.e(TAG, "getProcessNameInternal exception:" + e.getMessage());
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
