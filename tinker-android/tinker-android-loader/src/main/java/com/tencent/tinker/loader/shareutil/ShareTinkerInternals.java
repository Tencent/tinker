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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.text.TextUtils;

import com.tencent.tinker.loader.TinkerRuntimeException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by zhangshaowen on 16/3/10.
 */
public class ShareTinkerInternals {
    private static final String  TAG                   = "Tinker.TinkerInternals";
    private static final boolean VM_IS_ART             = isVmArt(System.getProperty("java.vm.version"));
    private static final boolean VM_IS_JIT             = isVmJitInternal();
    private static final String  PATCH_PROCESS_NAME    = ":patch";

    private static       Boolean isPatchProcess        = null;
    private static       Boolean isARKHotRunning       = null;
    /**
     * or you may just hardcode them in your app
     */
    private static final String[]  processName           = {null};
    private static       String    tinkerID              = null;
    private static       String    currentInstructionSet = null;

    public static boolean isVmArt() {
        return VM_IS_ART || Build.VERSION.SDK_INT >= 21;
    }

    public static boolean isVmJit() {
        return VM_IS_JIT && Build.VERSION.SDK_INT < 24;
    }

    public static boolean isArkHotRuning() {
        if (isARKHotRunning != null) {
            return isARKHotRunning;
        }
        isARKHotRunning = false;
        Class<?> arkApplicationInfo = null;
        try {
            arkApplicationInfo = ClassLoader.getSystemClassLoader()
                .getParent().loadClass("com.huawei.ark.app.ArkApplicationInfo");
            Method isRunningInArkHot = null;
            isRunningInArkHot = arkApplicationInfo.getDeclaredMethod("isRunningInArk");
            isRunningInArkHot.setAccessible(true);
            isARKHotRunning = (Boolean) isRunningInArkHot.invoke(null);
        } catch (ClassNotFoundException e) {
            ShareTinkerLog.i(TAG, "class not found exception");
        } catch (NoSuchMethodException e) {
            ShareTinkerLog.i(TAG, "no such method exception");
        } catch (SecurityException e) {
            ShareTinkerLog.i(TAG, "security exception");
        } catch (IllegalAccessException e) {
            ShareTinkerLog.i(TAG, "illegal access exception");
        } catch (InvocationTargetException e) {
            ShareTinkerLog.i(TAG, "invocation target exception");
        } catch (IllegalArgumentException e) {
            ShareTinkerLog.i(TAG, "illegal argument exception");
        }
        return isARKHotRunning;
    }

    public static boolean isAfterAndroidO() {
        return Build.VERSION.SDK_INT > 25;
    }

    public static String getCurrentInstructionSet() {
        if (currentInstructionSet != null) {
            return currentInstructionSet;
        }

        try {
            Class<?> clazz = Class.forName("dalvik.system.VMRuntime");
            Method currentGet = clazz.getDeclaredMethod("getCurrentInstructionSet");
            currentGet.setAccessible(true);
            currentInstructionSet = (String) currentGet.invoke(null);
        } catch (Throwable ignored) {
            switch (Build.CPU_ABI) {
                case "armeabi":
                case "armeabi-v7a":
                    currentInstructionSet = "arm";
                    break;
                case "arm64-v8a":
                    currentInstructionSet = "arm64";
                    break;
                case "x86":
                    currentInstructionSet = "x86";
                    break;
                case "x86_64":
                    currentInstructionSet = "x86_64";
                    break;
                case "mips":
                    currentInstructionSet = "mips";
                    break;
                case "mips64":
                    currentInstructionSet = "mips64";
                    break;
                default:
                    throw new IllegalStateException("Unsupported abi: " + Build.CPU_ABI);
            }
        }
        ShareTinkerLog.d(TAG, "getCurrentInstructionSet:" + currentInstructionSet);
        return currentInstructionSet;
    }

    public static boolean is32BitEnv() {
        final String currISA = getCurrentInstructionSet();
        return "arm".equals(currISA) || "x86".equals(currISA) || "mips".equals(currISA);
    }

    public static boolean isSystemOTA(String lastFingerPrint) {
        String currentFingerprint = Build.FINGERPRINT;
        if (lastFingerPrint == null
            || lastFingerPrint.equals("")
            || currentFingerprint == null
            || currentFingerprint.equals("")) {
            ShareTinkerLog.d(TAG, "fingerprint empty:" + lastFingerPrint + ",current:" + currentFingerprint);
            return false;
        } else {
            if (lastFingerPrint.equals(currentFingerprint)) {
                ShareTinkerLog.d(TAG, "same fingerprint:" + currentFingerprint);
                return false;
            } else {
                ShareTinkerLog.d(TAG, "system OTA,fingerprint not equal:" + lastFingerPrint + "," + currentFingerprint);
                return true;
            }
        }
    }

    public static ShareDexDiffPatchInfo changeTestDexToClassN(ShareDexDiffPatchInfo rawDexInfo, int index) {
        if (rawDexInfo.rawName.startsWith(ShareConstants.TEST_DEX_NAME)) {
            String newName;
            if (index != 1) {
                newName = "classes" + index + ".dex";
            } else {
                newName = "classes.dex";
            }
            return new ShareDexDiffPatchInfo(newName, rawDexInfo.path, rawDexInfo.destMd5InDvm, rawDexInfo.destMd5InArt,
                rawDexInfo.dexDiffMd5, rawDexInfo.oldDexCrC, rawDexInfo.newOrPatchedDexCrC, rawDexInfo.dexMode);
        }

        return null;
    }

    public static boolean isNullOrNil(final String object) {
        if ((object == null) || (object.length() <= 0)) {
            return true;
        }
        return false;
    }

    /**
     * thinker package check
     *
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
            ShareTinkerLog.e(TAG, "tinkerId in patch is not matched with the one in base pack, base: %s, patch: %s.",
                    oldTinkerId, patchTinkerId);
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
     *
     * @param patchFile
     * @return
     */
    public static Properties fastGetPatchPackageMeta(File patchFile) {
        if (patchFile == null || !patchFile.isFile() || patchFile.length() == 0) {
            ShareTinkerLog.e(TAG, "patchFile is illegal");
            return null;
        }
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(patchFile);
            ZipEntry packageEntry = zipFile.getEntry(ShareConstants.PACKAGE_META_FILE);
            if (packageEntry == null) {
                ShareTinkerLog.e(TAG, "patch meta entry not found");
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
            ShareTinkerLog.e(TAG, "fastGetPatchPackageMeta exception:" + e.getMessage());
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
            ShareTinkerLog.e(TAG, "getManifestTinkerID exception:" + e.getMessage());
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

    public static boolean isTinkerEnabledForArkHot(int flag) {
        return (flag & ShareConstants.TINKER_ARKHOT_MASK) != 0;
    }

    public static String getTypeString(int type) {
        switch (type) {
            case ShareConstants.TYPE_DEX:
                return "dex";
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
     *
     * @param context
     */
    public static void setTinkerDisableWithSharedPreferences(Context context) {
        SharedPreferences sp = context.getSharedPreferences(ShareConstants.TINKER_SHARE_PREFERENCE_CONFIG, Context.MODE_MULTI_PROCESS);
        String keyName = getTinkerSwitchSPKey(context);
        sp.edit().putBoolean(keyName, false).commit();
    }

    /**
     * can't load or receive any patch!
     *
     * @param context
     * @return
     */
    public static boolean isTinkerEnableWithSharedPreferences(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences sp = context.getSharedPreferences(ShareConstants.TINKER_SHARE_PREFERENCE_CONFIG, Context.MODE_MULTI_PROCESS);
        String keyName = getTinkerSwitchSPKey(context);
        return sp.getBoolean(keyName, true);
    }

    private static String getTinkerSwitchSPKey(Context context) {
        String tmpTinkerId = getManifestTinkerID(context);
        if (isNullOrNil(tmpTinkerId)) {
            tmpTinkerId = "@@";
        }
        return ShareConstants.TINKER_ENABLE_CONFIG_PREFIX + ShareConstants.TINKER_VERSION + "_" + tmpTinkerId;
    }

    private static final String SAFEMODE_COUNT_REC_PREFIX = "safemode_count_rec_";

    public static int getSafeModeCount(Context context) {
        final String processName = ShareTinkerInternals.getProcessName(context);
        final String recFileName = SAFEMODE_COUNT_REC_PREFIX + processName;
        final File safeModeRecFile = new File(SharePatchFileUtil.getPatchDirectory(context), recFileName);
        DataInputStream dis = null;
        try {
            dis = new DataInputStream(new FileInputStream(safeModeRecFile));
            final String expectedKey = ShareConstants.TINKER_SAFE_MODE_COUNT_PREFIX + ShareConstants.TINKER_VERSION;
            final String actualKey = dis.readUTF();
            if (!expectedKey.equals(actualKey)) {
                ShareTinkerLog.w(TAG, "getSafeModeCount: key is not equal, expt: %s, actul: %s, return 0 instead.", expectedKey, actualKey);
                return 0;
            }
            final int count = dis.readInt();
            ShareTinkerLog.i(TAG, "getSafeModeCount: count: %s", count);
            return count;
        } catch (Throwable ignored) {
            ShareTinkerLog.w(TAG, "getSafeModeCount: recFileName:" + recFileName + " failed, return 0 instead.");
            return 0;
        } finally {
            SharePatchFileUtil.closeQuietly(dis);
        }
    }

    public static void setSafeModeCount(Context context, int count) {
        final String processName = ShareTinkerInternals.getProcessName(context);
        final String recFileName = SAFEMODE_COUNT_REC_PREFIX + processName;
        final File safeModeRecFile = new File(SharePatchFileUtil.getPatchDirectory(context), recFileName);
        if (!safeModeRecFile.exists()) {
            safeModeRecFile.getParentFile().mkdirs();
        }
        DataOutputStream dos = null;
        try {
            dos = new DataOutputStream(new FileOutputStream(safeModeRecFile));
            final String key = ShareConstants.TINKER_SAFE_MODE_COUNT_PREFIX + ShareConstants.TINKER_VERSION;
            dos.writeUTF(key);
            dos.writeInt(count);
            ShareTinkerLog.i(TAG, "setSafeModeCount: count: %s", count);
        } catch (Throwable ignored) {
            ShareTinkerLog.w(TAG, "setSafeModeCount: recFileName:" + recFileName + " failed, return 0 instead.");
        } finally {
            SharePatchFileUtil.closeQuietly(dos);
        }
    }

    public static boolean isTinkerEnabled(int flag) {
        return (flag != ShareConstants.TINKER_DISABLE);
    }

    public static boolean isTinkerEnabledAll(int flag) {
        return (flag == ShareConstants.TINKER_ENABLE_ALL);
    }

    public static boolean isInMainProcess(Context context) {
        String mainProcessName = null;
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        if (applicationInfo != null) {
            mainProcessName = applicationInfo.processName;
        }
        if (isNullOrNil(mainProcessName)) {
            mainProcessName = context.getPackageName();
        }
        String processName = getProcessName(context);
        if (processName == null || processName.length() == 0) {
            processName = "";
        }

        return mainProcessName.equals(processName);
    }

    public static boolean isInPatchProcess(Context context) {
        if (isPatchProcess != null) {
            return isPatchProcess;
        }

        isPatchProcess = getProcessName(context).endsWith(PATCH_PROCESS_NAME);
        return isPatchProcess;
    }

    public static String getCurrentOatMode(Context context, String current) {
        if (current.equals(ShareConstants.CHANING_DEX_OPTIMIZE_PATH)) {
            if (isInMainProcess(context)) {
                current = ShareConstants.DEFAULT_DEX_OPTIMIZE_PATH;
            } else {
                current = ShareConstants.INTERPRET_DEX_OPTIMIZE_PATH;
            }
        }
        return current;
    }

    public static void killAllOtherProcess(Context context) {
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return;
        }
        List<ActivityManager.RunningAppProcessInfo> appProcessList = am
            .getRunningAppProcesses();

        if (appProcessList == null) {
            return;
        }
        // NOTE: getRunningAppProcess() ONLY GIVE YOU THE PROCESS OF YOUR OWN PACKAGE IN ANDROID M
        // BUT THAT'S ENOUGH HERE
        for (ActivityManager.RunningAppProcessInfo ai : appProcessList) {
            // KILL OTHER PROCESS OF MINE
            if (ai.uid == android.os.Process.myUid() && ai.pid != android.os.Process.myPid()) {
                android.os.Process.killProcess(ai.pid);
            }
        }

    }

    public static void killProcessExceptMain(Context context) {
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return;
        }
        List<ActivityManager.RunningAppProcessInfo> appProcessList = am.getRunningAppProcesses();
        if (appProcessList != null) {
            // NOTE: getRunningAppProcess() ONLY GIVE YOU THE PROCESS OF YOUR OWN PACKAGE IN ANDROID M
            // BUT THAT'S ENOUGH HERE
            for (ActivityManager.RunningAppProcessInfo ai : appProcessList) {
                if (ai.uid != android.os.Process.myUid()) {
                    continue;
                }
                if (ai.processName.equals(context.getPackageName())) {
                    continue;
                }
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
    public static String getProcessName(Context context) {
        if (processName[0] == null) {
            synchronized (processName) {
                if (processName[0] == null) {
                    processName[0] = getProcessNameInternal(context);
                }
            }
        }
        return (processName[0] != null ? processName[0] : "");
    }

    @SuppressLint("NewApi")
    private static String getProcessNameInternal(final Context context) {
        if (isNewerOrEqualThanVersion(28, true)) {
            final String result = Application.getProcessName();
            if (!TextUtils.isEmpty(result)) {
                return result;
            }
        }

        // The 'currentProcess' method only exists on api 18 and newer systems.
        if (isNewerOrEqualThanVersion(18, true)) {
            try {
                final Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");
                final Method currentProcessMethod = ShareReflectUtil.findMethod(activityThreadClazz, "currentProcessName");
                currentProcessMethod.setAccessible(true);
                final String result = (String) currentProcessMethod.invoke(null);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Throwable thr) {
                ShareTinkerLog.e(TAG, "getProcessNameInternal reflect activity thread exception:" + thr.getMessage());
            }
        }

        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/self/cmdline"), StandardCharsets.US_ASCII));
            String result = br.readLine();
            if (result != null) {
                result = result.trim();
                if (!result.isEmpty()) {
                    return result;
                }
            }
        } catch (Throwable thr) {
            ShareTinkerLog.e(TAG, "getProcessNameInternal parse cmdline exception:" + thr.getMessage());
        } finally {
            SharePatchFileUtil.closeQuietly(br);
        }

        if (context != null) {
            try {
                final int myPid = android.os.Process.myPid();
                final int myUid = android.os.Process.myUid();
                final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    final List<RunningAppProcessInfo> procInfos = am.getRunningAppProcesses();
                    if (procInfos != null) {
                        for (RunningAppProcessInfo procInfo : procInfos) {
                            if (procInfo.pid == myPid && procInfo.uid == myUid) {
                                return procInfo.processName;
                            }
                        }
                    }
                }
            } catch (Throwable thr) {
                ShareTinkerLog.e(TAG, "getProcessNameInternal getRunningAppProcesses exception:" + thr.getMessage());
            }
        }

        return null;
    }

    /**
     * vm whether it is art
     *
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

    private static boolean isVmJitInternal() {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method mthGet = clazz.getDeclaredMethod("get", String.class);

            String jit = (String) mthGet.invoke(null, "dalvik.vm.usejit");
            String jitProfile = (String) mthGet.invoke(null, "dalvik.vm.usejitprofiles");

            //usejit is true and usejitprofiles is null
            if (!isNullOrNil(jit) && isNullOrNil(jitProfile) && jit.equals("true")) {
                return true;
            }
        } catch (Throwable e) {
            ShareTinkerLog.e(TAG, "isVmJitInternal ex:" + e);
        }
        return false;
    }

    public static boolean isNewerOrEqualThanVersion(int apiLevel, boolean includePreviewVer) {
        if (includePreviewVer && Build.VERSION.SDK_INT >= 23) {
            return Build.VERSION.SDK_INT >= apiLevel
                    || ((Build.VERSION.SDK_INT == apiLevel - 1) && Build.VERSION.PREVIEW_SDK_INT > 0);
        } else {
            return Build.VERSION.SDK_INT >= apiLevel;
        }
    }

    public static boolean isOlderOrEqualThanVersion(int apiLevel, boolean includePreviewVer) {
        if (includePreviewVer && Build.VERSION.SDK_INT >= 23) {
            return Build.VERSION.SDK_INT <= apiLevel
                    || ((Build.VERSION.SDK_INT == apiLevel - 1) && Build.VERSION.PREVIEW_SDK_INT > 0);
        } else {
            return Build.VERSION.SDK_INT <= apiLevel;
        }
    }

    /**
     * @return true if system version is in range [lowApiLevel, hiApiLevel].
     */
    public static boolean isVersionInRange(int lowApiLevel, int hiApiLevel, boolean includePreviewVer) {
        return isNewerOrEqualThanVersion(lowApiLevel, includePreviewVer) &&
                isOlderOrEqualThanVersion(hiApiLevel, includePreviewVer);
    }

    public static String getExceptionCauseString(final Throwable ex) {
        if (ex == null) return "";

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(bos);

        try {
            // print directly
            Throwable t = ex;
            while (true) {
                Throwable cause = t.getCause();
                if (cause == null) {
                    break;
                }
                t = cause;
            }
            t.printStackTrace(ps);
            return toVisualString(bos.toString());
        } finally {
            SharePatchFileUtil.closeQuietly(ps);
        }
    }

    public static String toVisualString(String src) {
        boolean cutFlg = false;
        if (null == src) {
            return null;
        }
        char[] chr = src.toCharArray();
        if (null == chr) {
            return null;
        }
        int i = 0;
        for (; i < chr.length; i++) {
            if (chr[i] > 127) {
                chr[i] = 0;
                cutFlg = true;
                break;
            }
        }

        if (cutFlg) {
            return new String(chr, 0, i);
        } else {
            return src;
        }
    }

    public static void cleanPatch(Context context) {
        if (context == null) {
            throw new TinkerRuntimeException("context is null");
        }
        final File tinkerDir = SharePatchFileUtil.getPatchDirectory(context);
        if (!tinkerDir.exists()) {
            ShareTinkerLog.printErrStackTrace(TAG, new Throwable(),"try to clean patch while there're not any applied patches.");
            return;
        }
        final File patchInfoFile = SharePatchFileUtil.getPatchInfoFile(tinkerDir.getAbsolutePath());
        if (!patchInfoFile.exists()) {
            ShareTinkerLog.printErrStackTrace(TAG, new Throwable(), "try to clean patch while patch info file does not exist.");
            return;
        }
        final File patchInfoLockFile = SharePatchFileUtil.getPatchInfoLockFile(tinkerDir.getAbsolutePath());
        final SharePatchInfo patchInfo = SharePatchInfo.readAndCheckPropertyWithLock(patchInfoFile, patchInfoLockFile);
        if (patchInfo != null) {
            if (!patchInfo.newVersion.equals(patchInfo.oldVersion)) {
                // newVersion hasn't been loaded yet, we can remove it directly now.
                final String patchName = SharePatchFileUtil.getPatchVersionDirectory(patchInfo.newVersion);
                SharePatchFileUtil.deleteDir(new File(tinkerDir, patchName));
                patchInfo.newVersion = patchInfo.oldVersion;
                patchInfo.versionToRemove = "";
            } else {
                patchInfo.versionToRemove = patchInfo.newVersion;
            }
            SharePatchInfo.rewritePatchInfoFileWithLock(patchInfoFile, patchInfo, patchInfoLockFile);
        } else {
            ShareTinkerLog.printErrStackTrace(TAG, new Throwable(), "fail to get patchInfo.");
        }
    }
}
