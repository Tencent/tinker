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

import android.os.Build;

import com.tencent.tinker.loader.TinkerRuntimeException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by zhangshaowen on 16/3/16.
 */
public class SharePatchInfo {
    private static final String TAG = "Tinker.PatchInfo";

    public static final int    MAX_EXTRACT_ATTEMPTS        = ShareConstants.MAX_EXTRACT_ATTEMPTS;
    public static final String OLD_VERSION                 = ShareConstants.OLD_VERSION;
    public static final String NEW_VERSION                 = ShareConstants.NEW_VERSION;
    public static final String IS_PROTECTED_APP            = ShareConstants.PKGMETA_KEY_IS_PROTECTED_APP;
    public static final String VERSION_TO_REMOVE           = "version_to_remove";
    public static final String FINGER_PRINT                = "print";
    public static final String OAT_DIR                     = "dir";
    public static final String IS_REMOVE_INTERPRET_OAT_DIR = "is_remove_interpret_oat_dir";
    public static final String DEFAULT_DIR                 = ShareConstants.DEFAULT_DEX_OPTIMIZE_PATH;
    public String oldVersion;
    public String newVersion;
    public boolean isProtectedApp;
    public String versionToRemove;
    public String fingerPrint;
    public String oatDir;
    public boolean isRemoveInterpretOATDir;

    public SharePatchInfo(String oldVer, String newVer, boolean isProtectedApp, String versionToRemove, String finger, String oatDir, boolean isRemoveInterpretOATDir) {
        // TODO Auto-generated constructor stub
        this.oldVersion = oldVer;
        this.newVersion = newVer;
        this.isProtectedApp = isProtectedApp;
        this.versionToRemove = versionToRemove;
        this.fingerPrint = finger;
        this.oatDir = oatDir;
        this.isRemoveInterpretOATDir = isRemoveInterpretOATDir;
    }

    public static SharePatchInfo readAndCheckPropertyWithLock(File pathInfoFile, File lockFile) {
        if (pathInfoFile == null || lockFile == null) {
            return null;
        }
        File lockParentFile = lockFile.getParentFile();
        if (!lockParentFile.exists()) {
            lockParentFile.mkdirs();
        }

        SharePatchInfo patchInfo;
        ShareFileLockHelper fileLock = null;
        try {
            fileLock = ShareFileLockHelper.getFileLock(lockFile);
            patchInfo = readAndCheckProperty(pathInfoFile);
        } catch (Exception e) {
            throw new TinkerRuntimeException("readAndCheckPropertyWithLock fail", e);
        } finally {
            try {
                if (fileLock != null) {
                    fileLock.close();
                }
            } catch (IOException e) {
                ShareTinkerLog.w(TAG, "releaseInfoLock error", e);
            }
        }

        return patchInfo;
    }

    public static boolean rewritePatchInfoFileWithLock(File pathInfoFile, SharePatchInfo info, File lockFile) {
        if (pathInfoFile == null || info == null || lockFile == null) {
            return false;
        }
        File lockParentFile = lockFile.getParentFile();
        if (!lockParentFile.exists()) {
            lockParentFile.mkdirs();
        }
        boolean rewriteSuccess;
        ShareFileLockHelper fileLock = null;
        try {
            fileLock = ShareFileLockHelper.getFileLock(lockFile);
            rewriteSuccess = rewritePatchInfoFile(pathInfoFile, info);
        } catch (Exception e) {
            throw new TinkerRuntimeException("rewritePatchInfoFileWithLock fail", e);
        } finally {
            try {
                if (fileLock != null) {
                    fileLock.close();
                }
            } catch (IOException e) {
                ShareTinkerLog.i(TAG, "releaseInfoLock error", e);
            }

        }
        return rewriteSuccess;
    }

    private static SharePatchInfo readAndCheckProperty(File pathInfoFile) {
        boolean isReadPatchSuccessful = false;
        int numAttempts = 0;
        String oldVer = null;
        String newVer = null;
        String lastFingerPrint = null;
        boolean isProtectedApp = false;
        String versionToRemove = null;
        String oatDir = null;
        boolean isRemoveInterpretOATDir = false;
        while (numAttempts < MAX_EXTRACT_ATTEMPTS && !isReadPatchSuccessful) {
            numAttempts++;
            Properties properties = new Properties();
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(pathInfoFile);
                properties.load(inputStream);
                oldVer = properties.getProperty(OLD_VERSION);
                newVer = properties.getProperty(NEW_VERSION);
                final String isProtectedAppStr = properties.getProperty(IS_PROTECTED_APP);
                isProtectedApp = (isProtectedAppStr != null && !isProtectedAppStr.isEmpty() && !"0".equals(isProtectedAppStr));
                versionToRemove = properties.getProperty(VERSION_TO_REMOVE);
                lastFingerPrint = properties.getProperty(FINGER_PRINT);
                oatDir = properties.getProperty(OAT_DIR);
                final String isRemoveInterpretOATDirStr = properties.getProperty(IS_REMOVE_INTERPRET_OAT_DIR);
                isRemoveInterpretOATDir = (isRemoveInterpretOATDirStr != null && !isRemoveInterpretOATDirStr.isEmpty() && !"0".equals(isRemoveInterpretOATDirStr));
            } catch (IOException e) {
                ShareTinkerLog.w(TAG, "read property failed, e:" + e);
            } finally {
                SharePatchFileUtil.closeQuietly(inputStream);
            }

            if (oldVer == null || newVer == null) {
                continue;
            }
            //oldVer may be "" or 32 md5
            if ((!oldVer.equals("") && !SharePatchFileUtil.checkIfMd5Valid(oldVer))
                || !SharePatchFileUtil.checkIfMd5Valid(newVer)) {
                ShareTinkerLog.w(TAG, "path info file  corrupted:" + pathInfoFile.getAbsolutePath());
                continue;
            } else {
                isReadPatchSuccessful = true;
            }
        }

        if (isReadPatchSuccessful) {
            return new SharePatchInfo(oldVer, newVer, isProtectedApp, versionToRemove, lastFingerPrint, oatDir, isRemoveInterpretOATDir);
        }

        return null;
    }

    private static boolean rewritePatchInfoFile(File pathInfoFile, SharePatchInfo info) {
        if (pathInfoFile == null || info == null) {
            return false;
        }
        // write fingerprint if it is null or nil
        if (ShareTinkerInternals.isNullOrNil(info.fingerPrint)) {
            info.fingerPrint = Build.FINGERPRINT;
        }
        if (ShareTinkerInternals.isNullOrNil(info.oatDir)) {
            info.oatDir = DEFAULT_DIR;
        }
        ShareTinkerLog.i(TAG, "rewritePatchInfoFile file path:"
            + pathInfoFile.getAbsolutePath()
            + " , oldVer:"
            + info.oldVersion
            + ", newVer:"
            + info.newVersion
            + ", isProtectedApp:"
            + (info.isProtectedApp ? 1 : 0)
            + ", versionToRemove:"
            + info.versionToRemove
            + ", fingerprint:"
            + info.fingerPrint
            + ", oatDir:"
            + info.oatDir
            + ", isRemoveInterpretOATDir:"
            + (info.isRemoveInterpretOATDir ? 1 : 0)
            + ", stack: " + android.util.Log.getStackTraceString(new Throwable())
        );

        boolean isWritePatchSuccessful = false;
        int numAttempts = 0;

        File parentFile = pathInfoFile.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }

        while (numAttempts < MAX_EXTRACT_ATTEMPTS && !isWritePatchSuccessful) {
            numAttempts++;

            Properties newProperties = new Properties();
            newProperties.put(OLD_VERSION, info.oldVersion);
            newProperties.put(NEW_VERSION, info.newVersion);
            newProperties.put(IS_PROTECTED_APP, (info.isProtectedApp ? "1" : "0"));
            newProperties.put(VERSION_TO_REMOVE, info.versionToRemove);
            newProperties.put(FINGER_PRINT, info.fingerPrint);
            newProperties.put(OAT_DIR, info.oatDir);
            newProperties.put(IS_REMOVE_INTERPRET_OAT_DIR, (info.isRemoveInterpretOATDir ? "1" : "0"));

            FileOutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(pathInfoFile, false);
                String comment = "from old version:" + info.oldVersion + " to new version:" + info.newVersion;
                newProperties.store(outputStream, comment);
            } catch (Exception e) {
                ShareTinkerLog.w(TAG, "write property failed, e:" + e);
            } finally {
                SharePatchFileUtil.closeQuietly(outputStream);
            }

            SharePatchInfo tempInfo = readAndCheckProperty(pathInfoFile);

            isWritePatchSuccessful = tempInfo != null && tempInfo.oldVersion.equals(info.oldVersion) && tempInfo.newVersion.equals(info.newVersion);
            if (!isWritePatchSuccessful) {
                pathInfoFile.delete();
            }
        }
        return isWritePatchSuccessful;
    }
}
