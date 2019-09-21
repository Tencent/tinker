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

import com.tencent.tinker.bsdiff.BSPatch;
import com.tencent.tinker.commons.util.IOHelper;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareResPatchInfo;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;
import com.tencent.tinker.ziputils.ziputil.TinkerZipEntry;
import com.tencent.tinker.ziputils.ziputil.TinkerZipFile;
import com.tencent.tinker.ziputils.ziputil.TinkerZipOutputStream;
import com.tencent.tinker.ziputils.ziputil.TinkerZipUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by zhangshaowen on 2016/8/8.
 */
public class ResDiffPatchInternal extends BasePatchInternal {

    protected static final String TAG = "Tinker.ResDiffPatchInternal";

    protected static boolean tryRecoverResourceFiles(Tinker manager, ShareSecurityCheck checker, Context context,
                                                String patchVersionDirectory, File patchFile) {

        if (!manager.isEnabledForResource()) {
            TinkerLog.w(TAG, "patch recover, resource is not enabled");
            return true;
        }
        String resourceMeta = checker.getMetaContentMap().get(RES_META_FILE);

        if (resourceMeta == null || resourceMeta.length() == 0) {
            TinkerLog.w(TAG, "patch recover, resource is not contained");
            return true;
        }

        long begin = SystemClock.elapsedRealtime();
        boolean result = patchResourceExtractViaResourceDiff(context, patchVersionDirectory, resourceMeta, patchFile);
        long cost = SystemClock.elapsedRealtime() - begin;
        TinkerLog.i(TAG, "recover resource result:%b, cost:%d", result, cost);
        return result;
    }

    private static boolean patchResourceExtractViaResourceDiff(Context context, String patchVersionDirectory,
                                                               String meta, File patchFile) {
        String dir = patchVersionDirectory + "/" + ShareConstants.RES_PATH + "/";

        if (!extractResourceDiffInternals(context, dir, meta, patchFile, TYPE_RESOURCE)) {
            TinkerLog.w(TAG, "patch recover, extractDiffInternals fail");
            return false;
        }
        return true;
    }

    private static boolean extractResourceDiffInternals(Context context, String dir, String meta, File patchFile, int type) {
        ShareResPatchInfo resPatchInfo = new ShareResPatchInfo();
        ShareResPatchInfo.parseAllResPatchInfo(meta, resPatchInfo);
        TinkerLog.i(TAG, "res dir: %s, meta: %s", dir, resPatchInfo.toString());
        Tinker manager = Tinker.with(context);

        if (!SharePatchFileUtil.checkIfMd5Valid(resPatchInfo.resArscMd5)) {
            TinkerLog.w(TAG, "resource meta file md5 mismatch, type:%s, md5: %s", ShareTinkerInternals.getTypeString(type), resPatchInfo.resArscMd5);
            manager.getPatchReporter().onPatchPackageCheckFail(patchFile, BasePatchInternal.getMetaCorruptedCode(type));
            return false;
        }
        File directory = new File(dir);

        File tempResFileDirectory = new File(directory, "res_temp");

        File resOutput = new File(directory, ShareConstants.RES_NAME);
        //check result file whether already exist
        if (resOutput.exists()) {
            if (SharePatchFileUtil.checkResourceArscMd5(resOutput, resPatchInfo.resArscMd5)) {
                //it is ok, just continue
                TinkerLog.w(TAG, "resource file %s is already exist, and md5 match, just return true", resOutput.getPath());
                return true;
            } else {
                TinkerLog.w(TAG, "have a mismatch corrupted resource " + resOutput.getPath());
                resOutput.delete();
            }
        } else {
            resOutput.getParentFile().mkdirs();
        }

        try {
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            if (applicationInfo == null) {
                //Looks like running on a test Context, so just return without patching.
                TinkerLog.w(TAG, "applicationInfo == null!!!!");
                return false;
            }
            String apkPath = applicationInfo.sourceDir;


            if (!checkAndExtractResourceLargeFile(context, apkPath, directory, tempResFileDirectory, patchFile, resPatchInfo, type)) {
                return false;
            }

            TinkerZipOutputStream out = null;
            TinkerZipFile oldApk = null;
            TinkerZipFile newApk = null;
            int totalEntryCount = 0;
            try {
                out = new TinkerZipOutputStream(new BufferedOutputStream(new FileOutputStream(resOutput)));
                oldApk = new TinkerZipFile(apkPath);
                newApk = new TinkerZipFile(patchFile);
                final Enumeration<? extends TinkerZipEntry> entries = oldApk.entries();
                while (entries.hasMoreElements()) {
                    TinkerZipEntry zipEntry = entries.nextElement();
                    if (zipEntry == null) {
                        throw new TinkerRuntimeException("zipEntry is null when get from oldApk");
                    }
                    String name = zipEntry.getName();
                    if (name.contains("../")) {
                        continue;
                    }
                    if (ShareResPatchInfo.checkFileInPattern(resPatchInfo.patterns, name)) {
                        //won't contain in add set.
                        if (!resPatchInfo.deleteRes.contains(name)
                            && !resPatchInfo.modRes.contains(name)
                            && !resPatchInfo.largeModRes.contains(name)
                            && !name.equals(ShareConstants.RES_MANIFEST)) {
                            TinkerZipUtil.extractTinkerEntry(oldApk, zipEntry, out);
                            totalEntryCount++;
                        }
                    }
                }

                //process manifest
                TinkerZipEntry manifestZipEntry = oldApk.getEntry(ShareConstants.RES_MANIFEST);
                if (manifestZipEntry == null) {
                    TinkerLog.w(TAG, "manifest patch entry is null. path:" + ShareConstants.RES_MANIFEST);
                    manager.getPatchReporter().onPatchTypeExtractFail(patchFile, resOutput, ShareConstants.RES_MANIFEST, type);
                    return false;
                }
                TinkerZipUtil.extractTinkerEntry(oldApk, manifestZipEntry, out);
                totalEntryCount++;

                for (String name : resPatchInfo.largeModRes) {
                    TinkerZipEntry largeZipEntry = oldApk.getEntry(name);
                    if (largeZipEntry == null) {
                        TinkerLog.w(TAG, "large patch entry is null. path:" + name);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, resOutput, name, type);
                        return false;
                    }
                    ShareResPatchInfo.LargeModeInfo largeModeInfo = resPatchInfo.largeModMap.get(name);
                    TinkerZipUtil.extractLargeModifyFile(largeZipEntry, largeModeInfo.file, largeModeInfo.crc, out);
                    totalEntryCount++;
                }

                for (String name : resPatchInfo.addRes) {
                    TinkerZipEntry addZipEntry = newApk.getEntry(name);
                    if (addZipEntry == null) {
                        TinkerLog.w(TAG, "add patch entry is null. path:" + name);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, resOutput, name, type);
                        return false;
                    }
                    if (resPatchInfo.storeRes.containsKey(name)) {
                        File storeFile = resPatchInfo.storeRes.get(name);
                        TinkerZipUtil.extractLargeModifyFile(addZipEntry, storeFile, addZipEntry.getCrc(), out);
                    } else {
                        TinkerZipUtil.extractTinkerEntry(newApk, addZipEntry, out);
                    }
                    totalEntryCount++;
                }

                for (String name : resPatchInfo.modRes) {
                    TinkerZipEntry modZipEntry = newApk.getEntry(name);
                    if (modZipEntry == null) {
                        TinkerLog.w(TAG, "mod patch entry is null. path:" + name);
                        manager.getPatchReporter().onPatchTypeExtractFail(patchFile, resOutput, name, type);
                        return false;
                    }
                    if (resPatchInfo.storeRes.containsKey(name)) {
                        File storeFile = resPatchInfo.storeRes.get(name);
                        TinkerZipUtil.extractLargeModifyFile(modZipEntry, storeFile, modZipEntry.getCrc(), out);
                    } else {
                        TinkerZipUtil.extractTinkerEntry(newApk, modZipEntry, out);
                    }
                    totalEntryCount++;
                }
                // set comment back
                out.setComment(oldApk.getComment());
            } finally {
                IOHelper.closeQuietly(out);
                IOHelper.closeQuietly(oldApk);
                IOHelper.closeQuietly(newApk);

                //delete temp files
                SharePatchFileUtil.deleteDir(tempResFileDirectory);
            }
            boolean result = SharePatchFileUtil.checkResourceArscMd5(resOutput, resPatchInfo.resArscMd5);

            if (!result) {
                TinkerLog.i(TAG, "check final new resource file fail path:%s, entry count:%d, size:%d", resOutput.getAbsolutePath(), totalEntryCount, resOutput.length());
                SharePatchFileUtil.safeDeleteFile(resOutput);
                manager.getPatchReporter().onPatchTypeExtractFail(patchFile, resOutput, ShareConstants.RES_NAME, type);
                return false;
            }

            TinkerLog.i(TAG, "final new resource file:%s, entry count:%d, size:%d", resOutput.getAbsolutePath(), totalEntryCount, resOutput.length());
        } catch (Throwable e) {
            throw new TinkerRuntimeException("patch " + ShareTinkerInternals.getTypeString(type) +  " extract failed (" + e.getMessage() + ").", e);
        }
        return true;
    }

    private static boolean checkAndExtractResourceLargeFile(Context context, String apkPath, File directory, File tempFileDirtory,
                                                            File patchFile, ShareResPatchInfo resPatchInfo, int type) {
        long start = System.currentTimeMillis();
        Tinker manager = Tinker.with(context);
        ZipFile apkFile = null;
        ZipFile patchZipFile = null;
        try {
            //recover resources.arsc first
            apkFile = new ZipFile(apkPath);
            ZipEntry arscEntry = apkFile.getEntry(ShareConstants.RES_ARSC);
            File arscFile = new File(directory, ShareConstants.RES_ARSC);
            if (arscEntry == null) {
                TinkerLog.w(TAG, "resources apk entry is null. path:" + ShareConstants.RES_ARSC);
                manager.getPatchReporter().onPatchTypeExtractFail(patchFile, arscFile, ShareConstants.RES_ARSC, type);
                return false;
            }
            //use base resources.arsc crc to identify base.apk
            String baseArscCrc = String.valueOf(arscEntry.getCrc());
            if (!baseArscCrc.equals(resPatchInfo.arscBaseCrc)) {
                TinkerLog.e(TAG, "resources.arsc's crc is not equal, expect crc: %s, got crc: %s", resPatchInfo.arscBaseCrc, baseArscCrc);
                manager.getPatchReporter().onPatchTypeExtractFail(patchFile, arscFile, ShareConstants.RES_ARSC, type);
                return false;
            }

            //resource arsc is not changed, just return true
            if (resPatchInfo.largeModRes.isEmpty() && resPatchInfo.storeRes.isEmpty()) {
                TinkerLog.i(TAG, "no large modify or store resources, just return");
                return true;
            }
            patchZipFile = new ZipFile(patchFile);

            for (String name : resPatchInfo.storeRes.keySet()) {
                long storeStart = System.currentTimeMillis();
                File destCopy = new File(tempFileDirtory, name);
                SharePatchFileUtil.ensureFileDirectory(destCopy);

                ZipEntry patchEntry = patchZipFile.getEntry(name);
                if (patchEntry == null) {
                    TinkerLog.w(TAG, "store patch entry is null. path:" + name);
                    manager.getPatchReporter().onPatchTypeExtractFail(patchFile, destCopy, name, type);
                    return false;
                }
                extract(patchZipFile, patchEntry, destCopy, null, false);
                //fast check, only check size
                if (patchEntry.getSize() != destCopy.length()) {
                    TinkerLog.w(TAG, "resource meta file size mismatch, type:%s, name: %s, patch size: %d, file size; %d", ShareTinkerInternals.getTypeString(type), name, patchEntry.getSize(), destCopy.length());
                    manager.getPatchReporter().onPatchPackageCheckFail(patchFile, BasePatchInternal.getMetaCorruptedCode(type));
                    return false;
                }
                resPatchInfo.storeRes.put(name, destCopy);

                TinkerLog.w(TAG, "success recover store file:%s, file size:%d, use time:%d", destCopy.getPath(), destCopy.length(), (System.currentTimeMillis() - storeStart));
            }
            for (String name : resPatchInfo.largeModRes) {
                long largeStart = System.currentTimeMillis();
                ShareResPatchInfo.LargeModeInfo largeModeInfo = resPatchInfo.largeModMap.get(name);

                if (largeModeInfo == null) {
                    TinkerLog.w(TAG, "resource not found largeModeInfo, type:%s, name: %s", ShareTinkerInternals.getTypeString(type), name);
                    manager.getPatchReporter().onPatchPackageCheckFail(patchFile, BasePatchInternal.getMetaCorruptedCode(type));
                    return false;
                }

                largeModeInfo.file = new File(tempFileDirtory, name);
                SharePatchFileUtil.ensureFileDirectory(largeModeInfo.file);

                // we do not check the intermediate files' md5 to save time, use check whether it is 32 length
                if (!SharePatchFileUtil.checkIfMd5Valid(largeModeInfo.md5)) {
                    TinkerLog.w(TAG, "resource meta file md5 mismatch, type:%s, name: %s, md5: %s", ShareTinkerInternals.getTypeString(type), name, largeModeInfo.md5);
                    manager.getPatchReporter().onPatchPackageCheckFail(patchFile, BasePatchInternal.getMetaCorruptedCode(type));
                    return false;
                }
                ZipEntry patchEntry = patchZipFile.getEntry(name);
                if (patchEntry == null) {
                    TinkerLog.w(TAG, "large mod patch entry is null. path:" + name);
                    manager.getPatchReporter().onPatchTypeExtractFail(patchFile, largeModeInfo.file, name, type);
                    return false;
                }

                ZipEntry baseEntry = apkFile.getEntry(name);
                if (baseEntry == null) {
                    TinkerLog.w(TAG, "resources apk entry is null. path:" + name);
                    manager.getPatchReporter().onPatchTypeExtractFail(patchFile, largeModeInfo.file, name, type);
                    return false;
                }
                InputStream oldStream = null;
                InputStream newStream = null;
                try {
                    oldStream = apkFile.getInputStream(baseEntry);
                    newStream = patchZipFile.getInputStream(patchEntry);
                    BSPatch.patchFast(oldStream, newStream, largeModeInfo.file);
                } finally {
                    IOHelper.closeQuietly(oldStream);
                    IOHelper.closeQuietly(newStream);
                }
                // go go go bsdiff get the
                if (!SharePatchFileUtil.verifyFileMd5(largeModeInfo.file, largeModeInfo.md5)) {
                    TinkerLog.w(TAG, "Failed to recover large modify file:%s", largeModeInfo.file.getPath());
                    SharePatchFileUtil.safeDeleteFile(largeModeInfo.file);
                    manager.getPatchReporter().onPatchTypeExtractFail(patchFile, largeModeInfo.file, name, type);
                    return false;
                }
                TinkerLog.w(TAG, "success recover large modify file:%s, file size:%d, use time:%d", largeModeInfo.file.getPath(), largeModeInfo.file.length(), (System.currentTimeMillis() - largeStart));
            }
            TinkerLog.w(TAG, "success recover all large modify and store resources use time:%d", (System.currentTimeMillis() - start));
        } catch (Throwable e) {
            throw new TinkerRuntimeException("patch " + ShareTinkerInternals.getTypeString(type) +  " extract failed (" + e.getMessage() + ").", e);
        } finally {
            SharePatchFileUtil.closeZip(apkFile);
            SharePatchFileUtil.closeZip(patchZipFile);
        }
        return true;
    }
}
