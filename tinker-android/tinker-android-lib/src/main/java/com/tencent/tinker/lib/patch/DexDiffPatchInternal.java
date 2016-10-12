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

package com.tencent.tinker.lib.patch;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;

import com.tencent.tinker.commons.dexpatcher.DexPatchApplier;
import com.tencent.tinker.commons.dexpatcher.struct.SmallPatchedDexItemFile;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareDexDiffPatchInfo;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import dalvik.system.DexFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by zhangshaowen on 16/4/12.
 */
public class DexDiffPatchInternal extends BasePatchInternal {
    protected static final String TAG = "Tinker.DexDiffPatchInternal";

    protected static boolean tryRecoverDexFiles(Tinker manager, ShareSecurityCheck checker, Context context,
                                                String patchVersionDirectory, File patchFile, boolean isUpgradePatch) {

        if (!manager.isEnabledForDex()) {
            TinkerLog.w(TAG, "patch recover, dex is not enabled");
            return true;
        }
        String dexMeta = checker.getMetaContentMap().get(DEX_META_FILE);

        if (dexMeta == null) {
            TinkerLog.w(TAG, "patch recover, dex is not contained");
            return true;
        }

        long begin = SystemClock.elapsedRealtime();
        boolean result = patchDexExtractViaDexDiff(context, patchVersionDirectory, dexMeta, patchFile, isUpgradePatch);
        long cost = SystemClock.elapsedRealtime() - begin;
        TinkerLog.i(TAG, "recover dex result:%b, cost:%d, isUpgradePatch:%b", result, cost, isUpgradePatch);
        return result;
    }

    private static boolean patchDexExtractViaDexDiff(Context context, String patchVersionDirectory, String meta, File patchFile, boolean isUpgradePatch) {
        String dir = patchVersionDirectory + "/" + DEX_PATH + "/";

        int dexType = ShareTinkerInternals.isVmArt() ? TYPE_DEX_FOR_ART : TYPE_DEX;
        if (!extractDexDiffInternals(context, dir, meta, patchFile, dexType, isUpgradePatch)) {
            TinkerLog.w(TAG, "patch recover, extractDiffInternals fail");
            return false;
        }

        Tinker manager = Tinker.with(context);

        File dexFiles = new File(dir);
        File[] files = dexFiles.listFiles();

        if (files != null) {
            String optimizeDexDirectory = patchVersionDirectory + "/" + DEX_OPTIMIZE_PATH + "/";
            File optimizeDexDirectoryFile = new File(optimizeDexDirectory);

            if (!optimizeDexDirectoryFile.exists()) {
                optimizeDexDirectoryFile.mkdirs();
            }

            for (File file : files) {
                try {
                    String outputPathName = SharePatchFileUtil.optimizedPathFor(file, optimizeDexDirectoryFile);
                    long start = System.currentTimeMillis();
                    DexFile.loadDex(file.getAbsolutePath(), outputPathName, 0);
                    TinkerLog.i(TAG, "success dex optimize file, path: %s, use time: %d", file.getPath(), (System.currentTimeMillis() - start));
                } catch (Throwable e) {
                    TinkerLog.e(TAG, "dex optimize or load failed, path:" + file.getPath());
                    //delete file
                    SharePatchFileUtil.safeDeleteFile(file);
                    manager.getPatchReporter().onPatchDexOptFail(patchFile, file, optimizeDexDirectory, file.getName(), e, isUpgradePatch);
                    return false;
                }
            }
        }

        return true;
    }


    private static boolean extractDexDiffInternals(Context context, String dir, String meta, File patchFile, int type, boolean isUpgradePatch) {
        //parse
        ArrayList<ShareDexDiffPatchInfo> patchList = new ArrayList<>();

        ShareDexDiffPatchInfo.parseDexDiffPatchInfo(meta, patchList);

        if (patchList.isEmpty()) {
            TinkerLog.w(TAG, "extract patch list is empty! type:%s:", ShareTinkerInternals.getTypeString(type));
            return true;
        }

        File directory = new File(dir);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        //I think it is better to extract the raw files from apk
        Tinker manager = Tinker.with(context);
        ZipFile apk = null;
        ZipFile patch = null;
        try {
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            if (applicationInfo == null) {
                // Looks like running on a test Context, so just return without patching.
                TinkerLog.w(TAG, "applicationInfo == null!!!!");
                return false;
            }
            String apkPath = applicationInfo.sourceDir;
            apk = new ZipFile(apkPath);
            patch = new ZipFile(patchFile);

            SmallPatchedDexItemFile smallPatchInfoFile = null;

            if (ShareTinkerInternals.isVmArt()) {
                File extractedFile = new File(dir + ShareConstants.DEX_SMALLPATCH_INFO_FILE);
                ZipEntry smallPatchInfoEntry = patch.getEntry(ShareConstants.DEX_SMALLPATCH_INFO_FILE);
                if (smallPatchInfoEntry != null) {
                    InputStream smallPatchInfoIs = null;
                    try {
                        smallPatchInfoIs = patch.getInputStream(smallPatchInfoEntry);
                        smallPatchInfoFile = new SmallPatchedDexItemFile(smallPatchInfoIs);
                    } catch (Throwable e) {
                        TinkerLog.w(TAG, "failed to read small patched info. reason: " + e.getMessage());
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, ShareConstants.DEX_SMALLPATCH_INFO_FILE, type, isUpgradePatch);
                        return false;
                    } finally {
                        SharePatchFileUtil.closeQuietly(smallPatchInfoIs);
                    }
                } else {
                    TinkerLog.w(TAG, "small patch info is not exists, it's ok now.");
                }
            }

            for (ShareDexDiffPatchInfo info : patchList) {
                long start = System.currentTimeMillis();

                final String infoPath = info.path;
                String patchRealPath;
                if (infoPath.equals("")) {
                    patchRealPath = info.rawName;
                } else {
                    patchRealPath = info.path + "/" + info.rawName;
                }

                String dexDiffMd5 = info.dexDiffMd5;
                String oldDexCrc = info.oldDexCrC;

                String extractedFileMd5 = ShareTinkerInternals.isVmArt() ? info.destMd5InArt : info.destMd5InDvm;

                if (!SharePatchFileUtil.checkIfMd5Valid(extractedFileMd5)) {
                    TinkerLog.w(TAG, "meta file md5 invalid, type:%s, name: %s, md5: %s", ShareTinkerInternals.getTypeString(type), info.rawName, extractedFileMd5);
                    manager.getPatchReporter().onPatchPackageCheckFail(patchFile, isUpgradePatch, BasePatchInternal.getMetaCorruptedCode(type));
                    return false;
                }

                File extractedFile = new File(dir + info.realName);

                //check file whether already exist
                if (extractedFile.exists()) {
                    if (SharePatchFileUtil.verifyDexFileMd5(extractedFile, extractedFileMd5)) {
                        //it is ok, just continue
                        TinkerLog.w(TAG, "dex file %s is already exist, and md5 match, just continue", extractedFile.getPath());
                        continue;
                    } else {
                        TinkerLog.w(TAG, "have a mismatch corrupted dex " + extractedFile.getPath());
                        extractedFile.delete();
                    }
                } else {
                    extractedFile.getParentFile().mkdirs();
                }

                ZipEntry patchFileEntry = patch.getEntry(patchRealPath);
                ZipEntry rawApkFileEntry = apk.getEntry(patchRealPath);

                if (oldDexCrc.equals("0")) {
                    if (patchFileEntry == null) {
                        TinkerLog.w(TAG, "patch entry is null. path:" + patchRealPath);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }

                    //it is a new file, but maybe we need to repack the dex file
                    if (!extractDexFile(patch, patchFileEntry, extractedFile, info)) {
                        TinkerLog.w(TAG, "Failed to extract raw patch file " + extractedFile.getPath());
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }
                } else if (dexDiffMd5.equals("0")) {
                    // skip process old dex for dalvik vm
                    if (!ShareTinkerInternals.isVmArt()) {
                        continue;
                    }

                    if (rawApkFileEntry == null) {
                        TinkerLog.w(TAG, "apk entry is null. path:" + patchRealPath);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }

                    //check source crc instead of md5 for faster
                    String rawEntryCrc = String.valueOf(rawApkFileEntry.getCrc());
                    if (!rawEntryCrc.equals(oldDexCrc)) {
                        TinkerLog.e(TAG, "apk entry %s crc is not equal, expect crc: %s, got crc: %s", patchRealPath, oldDexCrc, rawEntryCrc);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }

                    InputStream oldDexIs = null;
                    try {
                        oldDexIs = apk.getInputStream(rawApkFileEntry);
                        new DexPatchApplier(oldDexIs, (int) rawApkFileEntry.getSize(), null, smallPatchInfoFile).executeAndSaveTo(extractedFile);
                    } catch (Throwable e) {
                        TinkerLog.w(TAG, "Failed to recover dex file " + extractedFile.getPath());
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        SharePatchFileUtil.safeDeleteFile(extractedFile);
                        return false;
                    } finally {
                        SharePatchFileUtil.closeQuietly(oldDexIs);
                    }

                    if (!SharePatchFileUtil.verifyDexFileMd5(extractedFile, extractedFileMd5)) {
                        TinkerLog.w(TAG, "Failed to recover dex file " + extractedFile.getPath());
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        SharePatchFileUtil.safeDeleteFile(extractedFile);
                        return false;
                    }
                } else {
                    if (patchFileEntry == null) {
                        TinkerLog.w(TAG, "patch entry is null. path:" + patchRealPath);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }

                    if (!SharePatchFileUtil.checkIfMd5Valid(dexDiffMd5)) {
                        TinkerLog.w(TAG, "meta file md5 invalid, type:%s, name: %s, md5: %s", ShareTinkerInternals.getTypeString(type), info.rawName, dexDiffMd5);
                        manager.getPatchReporter().onPatchPackageCheckFail(patchFile, isUpgradePatch, BasePatchInternal.getMetaCorruptedCode(type));
                        return false;
                    }

                    if (rawApkFileEntry == null) {
                        TinkerLog.w(TAG, "apk entry is null. path:" + patchRealPath);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }
                    //check source crc instead of md5 for faster
                    String rawEntryCrc = String.valueOf(rawApkFileEntry.getCrc());
                    if (!rawEntryCrc.equals(oldDexCrc)) {
                        TinkerLog.e(TAG, "apk entry %s crc is not equal, expect crc: %s, got crc: %s", patchRealPath, oldDexCrc, rawEntryCrc);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }

                    final boolean isRawDexFile = SharePatchFileUtil.isRawDexFile(info.rawName);
                    InputStream oldInputStream = apk.getInputStream(rawApkFileEntry);
                    InputStream newInputStream = patch.getInputStream(patchFileEntry);
                    //if it is not the dex file or we are using jar mode, we should repack the output dex to jar
                    try {
                        if (!isRawDexFile || info.isJarMode) {
                            FileOutputStream fos = new FileOutputStream(extractedFile);
                            ZipOutputStream zos = new ZipOutputStream(new
                                BufferedOutputStream(fos));

                            try {
                                zos.putNextEntry(new ZipEntry(ShareConstants.DEX_IN_JAR));
                                //it is not a raw dex file, we do not want to any temp files
                                int oldDexSize;
                                if (!isRawDexFile) {
                                    ZipEntry entry;
                                    ZipInputStream zis = new ZipInputStream(oldInputStream);
                                    while ((entry = zis.getNextEntry()) != null) {
                                        if (ShareConstants.DEX_IN_JAR.equals(entry.getName())) break;
                                    }
                                    if (entry == null) {
                                        throw new TinkerRuntimeException("can't recognize zip dex format file:" + extractedFile.getAbsolutePath());
                                    }
                                    oldInputStream = zis;
                                    oldDexSize = (int) entry.getSize();
                                } else {
                                    oldDexSize = (int) rawApkFileEntry.getSize();
                                }
                                new DexPatchApplier(oldInputStream, oldDexSize, newInputStream, smallPatchInfoFile).executeAndSaveTo(zos);
                                zos.closeEntry();
                            } finally {
                                SharePatchFileUtil.closeQuietly(zos);
                            }

                        } else {
                            new DexPatchApplier(oldInputStream, (int) rawApkFileEntry.getSize(), newInputStream, smallPatchInfoFile).executeAndSaveTo(extractedFile);
                        }
                    } finally {
                        SharePatchFileUtil.closeQuietly(oldInputStream);
                        SharePatchFileUtil.closeQuietly(newInputStream);
                    }

                    if (!SharePatchFileUtil.verifyDexFileMd5(extractedFile, extractedFileMd5)) {
                        TinkerLog.w(TAG, "Failed to recover dex file " + extractedFile.getPath());
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        SharePatchFileUtil.safeDeleteFile(extractedFile);
                        return false;
                    }
                    TinkerLog.w(TAG, "success recover dex file: %s, use time: %d",
                        extractedFile.getPath(), (System.currentTimeMillis() - start));
                }
            }

        } catch (Throwable e) {
//            e.printStackTrace();
            throw new TinkerRuntimeException("patch " + ShareTinkerInternals.getTypeString(type) + " extract failed (" + e.getMessage() + ").", e);
        } finally {
            SharePatchFileUtil.closeZip(apk);
            SharePatchFileUtil.closeZip(patch);
        }
        return true;
    }

    /**
     * repack dex to jar
     *
     * @param zipFile
     * @param entryFile
     * @param extractTo
     * @param targetMd5
     * @return boolean
     * @throws IOException
     */
    private static boolean extractDexToJar(ZipFile zipFile, ZipEntry entryFile, File extractTo, String targetMd5) throws IOException {
        int numAttempts = 0;
        boolean isExtractionSuccessful = false;
        while (numAttempts < MAX_EXTRACT_ATTEMPTS && !isExtractionSuccessful) {
            numAttempts++;

            FileOutputStream fos = new FileOutputStream(extractTo);
            InputStream in = zipFile.getInputStream(entryFile);

            ZipOutputStream zos = null;
            BufferedInputStream bis = null;

            TinkerLog.i(TAG, "try Extracting " + extractTo.getPath());
            try {
                zos = new ZipOutputStream(new
                    BufferedOutputStream(fos));
                bis = new BufferedInputStream(in);

                byte[] buffer = new byte[ShareConstants.BUFFER_SIZE];
                ZipEntry entry = new ZipEntry(ShareConstants.DEX_IN_JAR);
                zos.putNextEntry(entry);
                int length = bis.read(buffer);
                while (length != -1) {
                    zos.write(buffer, 0, length);
                    length = bis.read(buffer);
                }
                zos.closeEntry();
            } finally {
                SharePatchFileUtil.closeQuietly(bis);
                SharePatchFileUtil.closeQuietly(zos);
            }

            isExtractionSuccessful = SharePatchFileUtil.verifyDexFileMd5(extractTo, targetMd5);
            TinkerLog.i(TAG, "isExtractionSuccessful: %b", isExtractionSuccessful);

            if (!isExtractionSuccessful) {
                extractTo.delete();
                if (extractTo.exists()) {
                    TinkerLog.e(TAG, "Failed to delete corrupted dex " + extractTo.getPath());
                }
            }
        }
        return isExtractionSuccessful;
    }

    private static boolean extractDexFile(ZipFile zipFile, ZipEntry entryFile, File extractTo, ShareDexDiffPatchInfo dexInfo) throws IOException {
        final String fileMd5 = ShareTinkerInternals.isVmArt() ? dexInfo.destMd5InArt : dexInfo.destMd5InDvm;
        final String rawName = dexInfo.rawName;
        final boolean isJarMode = dexInfo.isJarMode;
        //it is raw dex and we use jar mode, so we need to zip it!
        if (SharePatchFileUtil.isRawDexFile(rawName) && isJarMode) {
            return extractDexToJar(zipFile, entryFile, extractTo, fileMd5);
        }
        return extract(zipFile, entryFile, extractTo, fileMd5, true);
    }

}
