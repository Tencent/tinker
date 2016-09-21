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

import android.util.Log;

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
    public static final int    MAX_EXTRACT_ATTEMPTS = ShareConstants.MAX_EXTRACT_ATTEMPTS;
    public static final String OLD_VERSION          = ShareConstants.OLD_VERSION;
    public static final String NEW_VERSION          = ShareConstants.NEW_VERSION;
    private static final String TAG = "PatchInfo";
    public String oldVersion;
    public String newVersion;

    public SharePatchInfo(String oldVer, String newVew) {
        // TODO Auto-generated constructor stub
        this.oldVersion = oldVer;
        this.newVersion = newVew;
    }

    public static SharePatchInfo readAndCheckPropertyWithLock(File pathInfoFile, File lockFile) {
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
                Log.i(TAG, "releaseInfoLock error", e);
            }
        }

        return patchInfo;
    }

    public static boolean rewritePatchInfoFileWithLock(File pathInfoFile, SharePatchInfo info, File lockFile) {
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
                Log.i(TAG, "releaseInfoLock error", e);
            }

        }
        return rewriteSuccess;
    }

    private static SharePatchInfo readAndCheckProperty(File pathInfoFile) {
        boolean isReadPatchSuccessful = false;
        int numAttempts = 0;
        String oldVer = null;
        String newVer = null;

        while (numAttempts < MAX_EXTRACT_ATTEMPTS && !isReadPatchSuccessful) {
            numAttempts++;
            Properties properties = new Properties();
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(pathInfoFile);
                properties.load(inputStream);
                oldVer = properties.getProperty(OLD_VERSION);
                newVer = properties.getProperty(NEW_VERSION);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                SharePatchFileUtil.closeQuietly(inputStream);
            }

            if (oldVer == null || newVer == null) {
                continue;
            }
            //oldver may be "" or 32 md5
            if ((!oldVer.equals("") && !SharePatchFileUtil.checkIfMd5Valid(oldVer)) || !SharePatchFileUtil.checkIfMd5Valid(newVer)) {
                Log.w(TAG, "path info file  corrupted:" + pathInfoFile.getAbsolutePath());
                continue;
            } else {
                isReadPatchSuccessful = true;
            }
        }

        if (isReadPatchSuccessful) {
            return new SharePatchInfo(oldVer, newVer);
        }

        return null;
    }

    private static boolean rewritePatchInfoFile(File pathInfoFile, SharePatchInfo info) {
        if (pathInfoFile == null || info == null) {
            return false;
        }
        Log.i(TAG, "rewritePatchInfoFile file path:"
            + pathInfoFile.getAbsolutePath()
            + " , oldVer:"
            + info.oldVersion
            + ", newVer:"
            + info.newVersion);

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
            FileOutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(pathInfoFile, false);
                String comment = "from old version:" + info.oldVersion + " to new version:" + info.newVersion;
                newProperties.store(outputStream, comment);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                SharePatchFileUtil.closeQuietly(outputStream);
            }

            SharePatchInfo tempInfo = readAndCheckProperty(pathInfoFile);

            isWritePatchSuccessful = tempInfo != null && tempInfo.oldVersion.equals(info.oldVersion) && tempInfo.newVersion.equals(info.newVersion);
            if (!isWritePatchSuccessful) {
                pathInfoFile.delete();
            }
        }
        if (isWritePatchSuccessful) {
            return true;
        }

        return false;
    }


}