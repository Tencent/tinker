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

package com.tencent.tinker.lib.patch;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;

import com.tencent.tinker.commons.dexpatcher.DexPatchApplier;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareDexDiffPatchInfo;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

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

import dalvik.system.DexFile;

/**
 * Created by shwenzhang on 16/4/12.
 */
public class DexDiffPatchInternal extends BasePatchInternal {
    protected static final String TAG = "DexDiffPatchInternal";

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
//        manager.getRecoverReporter().onRecoverPatchTypeResult(tempPatchPath, result, cost, ShareConstants.TYPE_DEX, isUpgradePatch);
        return result;
    }

    private static boolean patchDexExtractViaDexDiff(Context context, String patchVersionDirectory, String meta, File patchFile, boolean isUpgradePatch) {
        String dir = patchVersionDirectory + "/" + DEX_PATH + "/";

        if (!extractDexDiffInternals(context, dir, meta, patchFile, TYPE_DEX, isUpgradePatch)) {
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
                    TinkerLog.i(TAG, "try dex optimize file, path:" + file.getPath());
                    DexFile.loadDex(file.getAbsolutePath(), outputPathName, 0);
                } catch (Exception e) {
                    TinkerLog.e(TAG, "dex optimize or load failed, path:" + file.getPath());
                    //delete file
                    SharePatchFileUtil.safeDeleteFile(file);
                    manager.getPatchReporter().onPatchDexOptFail(patchFile, file, optimizeDexDirectory, file.getName(), isUpgradePatch);
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

        try {
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            if (applicationInfo == null) {
                // Looks like running on a test Context, so just return without patching.
                TinkerLog.w(TAG, "applicationInfo == null!!!!");
                return false;
            }
            String apkPath = applicationInfo.sourceDir;
            final ZipFile apk = new ZipFile(apkPath);
            final ZipFile patch = new ZipFile(patchFile);

            for (ShareDexDiffPatchInfo info : patchList) {
                final String infoPath = info.path;
                String patchRealPath;
                if (infoPath.equals("")) {
                    patchRealPath = info.rawName;
                } else {
                    patchRealPath = info.path + "/" + info.rawName;
                }
                final String fileMd5 = info.md5;
                if (!SharePatchFileUtil.checkIfMd5Valid(fileMd5)) {
                    TinkerLog.w(TAG, "meta file md5 mismatch, type:%s, name: %s, md5: %s", ShareTinkerInternals.getTypeString(type), info.rawName, info.md5);
                    manager.getPatchReporter().onPatchPackageCheckFail(patchFile, isUpgradePatch, BasePatchInternal.getMetaCorruptedCode(type));
                    return false;
                }

                File extractedFile = new File(dir + info.realName);

                //check file whether already exist
                if (extractedFile.exists()) {
                    if (SharePatchFileUtil.verifyDexFileMd5(extractedFile, fileMd5)) {
                        //it is ok, just continue
                        continue;
                    } else {
                        TinkerLog.w(TAG, "have a mismatch corrupted dex " + extractedFile.getPath());
                        extractedFile.delete();
                    }
                } else {
                    extractedFile.getParentFile().mkdirs();
                }

                String patchFileMd5 = info.patchMd5;
                String rawApkMd5 = info.rawMd5;

                ZipEntry patchFileEntry = patch.getEntry(patchRealPath);
                ZipEntry rawApkFileEntry = apk.getEntry(patchRealPath);

                if (rawApkMd5.equals("0")) {
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
                } else if (patchFileMd5.equals("0")) {
                    //skip copy for dalvik vm
                    if (!ShareTinkerInternals.isVmArt()) {
                        return true;
                    }

                    if (rawApkFileEntry == null) {
                        TinkerLog.w(TAG, "apk entry is null. path:" + patchRealPath);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }

                    //because of art problem, we need to copy the others' classesN.dex also
                    if (!extractDexFile(apk, rawApkFileEntry, extractedFile, info)) {
                        TinkerLog.w(TAG, "Failed to extract raw apk file " + extractedFile.getPath());
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }
                } else {
                    if (patchFileEntry == null) {
                        TinkerLog.w(TAG, "patch entry is null. path:" + patchRealPath);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }

                    if (!SharePatchFileUtil.checkIfMd5Valid(patchFileMd5)) {
                        TinkerLog.w(TAG, "meta file md5 mismatch, type:%s, name: %s, md5: %s", ShareTinkerInternals.getTypeString(type), info.rawName, patchFileMd5);
                        manager.getPatchReporter().onPatchPackageCheckFail(patchFile, isUpgradePatch, BasePatchInternal.getMetaCorruptedCode(type));
                        return false;
                    }

                    if (rawApkFileEntry == null) {
                        TinkerLog.w(TAG, "apk entry is null. path:" + patchRealPath);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        return false;
                    }

                    //we don't check base md5 for faster!
                    if (!SharePatchFileUtil.checkIfMd5Valid(rawApkMd5)) {
                        TinkerLog.w(TAG, "meta file md5 mismatch, type:%s, name: %s, md5: %s", ShareTinkerInternals.getTypeString(type), info.rawName, rawApkMd5);
                        manager.getPatchReporter().onPatchPackageCheckFail(patchFile, isUpgradePatch, BasePatchInternal.getMetaCorruptedCode(type));
                        return false;
                    }
                    final boolean isRawDexFile = SharePatchFileUtil.isRawDexFile(info.rawName);
                    //if it is not the dex file or we are using jar mode, we should repack the output dex to jar
                    if (!isRawDexFile || info.isJarMode) {
                        FileOutputStream fos = new FileOutputStream(extractedFile);
                        ZipOutputStream zos = new ZipOutputStream(new
                            BufferedOutputStream(fos));
                        try {
                            zos.putNextEntry(new ZipEntry(ShareConstants.DEX_IN_JAR));

                            InputStream inputStream = apk.getInputStream(rawApkFileEntry);
                            //it is not a raw dex file, we do not want to any temp files
                            if (!isRawDexFile) {
                                ZipInputStream zis = new ZipInputStream(inputStream);
                                ZipEntry entry;
                                while ((entry = zis.getNextEntry()) != null) {
                                    if (ShareConstants.DEX_IN_JAR.equals(entry.getName())) break;
                                }
                                if (entry == null) {
                                    throw new TinkerRuntimeException("can't recognize zip dex format file:" + extractedFile.getAbsolutePath());
                                }
                                inputStream = zis;
                            }
                            new DexPatchApplier(inputStream, patch.getInputStream(patchFileEntry)).executeAndSaveTo(zos);
                            zos.closeEntry();
                        } finally {
                            SharePatchFileUtil.closeQuietly(zos);
                        }

                    } else {
                        new DexPatchApplier(apk.getInputStream(rawApkFileEntry), patch.getInputStream(patchFileEntry)).executeAndSaveTo(extractedFile);
                    }

                    if (!SharePatchFileUtil.verifyDexFileMd5(extractedFile, fileMd5)) {
                        TinkerLog.w(TAG, "Failed to recover diff file " + extractedFile.getPath());
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.rawName, type, isUpgradePatch);
                        SharePatchFileUtil.safeDeleteFile(extractedFile);
                        return false;
                    }
                }
            }

        } catch (Exception e) {
//            e.printStackTrace();
            throw new TinkerRuntimeException("patch " + ShareTinkerInternals.getTypeString(type) + " extract failed (" + e.getMessage() + ").", e);
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
            ZipOutputStream zos = new ZipOutputStream(new
                BufferedOutputStream(fos));

            InputStream in = zipFile.getInputStream(entryFile);
            BufferedInputStream bis = new BufferedInputStream(in);

            TinkerLog.i(TAG, "try Extracting " + extractTo.getPath());
            try {

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
        final String fileMd5 = dexInfo.md5;
        final String rawName = dexInfo.rawName;
        final boolean isJarMode = dexInfo.isJarMode;
        //it is raw dex and we use jar mode, so we need to zip it!
        if (SharePatchFileUtil.isRawDexFile(rawName) && isJarMode) {
            return extractDexToJar(zipFile, entryFile, extractTo, fileMd5);
        }
        return extract(zipFile, entryFile, extractTo, fileMd5, true);
    }

}
