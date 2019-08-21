/*
 * Copyright (C) 2019. Huawei Technologies Co., Ltd. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the BSD 3-Clause License
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * the BSD 3-Clause License for more details.
 */

package com.tencent.tinker.lib.patch;

import android.content.Context;

import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareArkHotDiffPatchInfo;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ArkHotDiffPatchInternal extends BasePatchInternal {
    private static final String TAG = "Tinker.ArkHotDiffPatchInternal";
    private static ArrayList<ShareArkHotDiffPatchInfo> arkPatchList = new ArrayList<>();

    protected static boolean tryRecoverArkHotLibrary(Tinker manager, ShareSecurityCheck checker, Context context,
                                                     String patchVersionDirectory, File patchFile) {
        String arkHotMeta = checker.getMetaContentMap().get(ARKHOT_META_FILE);
        if (arkHotMeta == null) {
            return true;
        }

        patchArkHotLibraryExtract(context, patchVersionDirectory, arkHotMeta, patchFile);

        return true;
    }

    private static boolean extractArkHotLibrary(Context context, String dir, File patchFile, int type) {
        Tinker manager = Tinker.with(context);
        ZipFile patch = null;
        try {
            patch = new ZipFile(patchFile);

            for (ShareArkHotDiffPatchInfo info : arkPatchList) {
                final String path = info.path;
                final String patchRealPath;
                if (path.equals("")) {
                    patchRealPath = info.name;
                } else {
                    patchRealPath = path + "/" + info.name;
                }

                final String md5 = info.patchMd5;
                if (!SharePatchFileUtil.checkIfMd5Valid(md5)) {
                    manager.getPatchReporter().onPatchPackageCheckFail(patchFile,
                            BasePatchInternal.getMetaCorruptedCode(type));
                    return false;
                }

                File extractedFile = new File(dir + info.name);
                if (extractedFile.exists()) {
                    if (md5.equals(SharePatchFileUtil.getMD5(extractedFile))) {
                        continue;
                    } else {
                        extractedFile.delete();
                    }
                } else {
                    extractedFile.getParentFile().mkdirs();
                }

                ZipEntry patchFileEntry = patch.getEntry(patchRealPath);
                if (!extract(patch, patchFileEntry, extractedFile, md5, false)) {
                    manager.getPatchReporter().onPatchTypeExtractFail(patchFile, extractedFile, info.name, type);
                    return false;
                }
            }
        } catch (IOException e) {
            throw new TinkerRuntimeException("patch " + ShareTinkerInternals.getTypeString(type)
                    + " extract failed (" + e.getMessage() + ").", e);
        } finally {
            SharePatchFileUtil.closeZip(patch);
        }

        return true;
    }

    private static boolean patchArkHotLibraryExtract(Context context, String patchVersionDirectory,
                                                     String meta, File patchFile) {
        String dir = patchVersionDirectory + "/" + ShareConstants.ARKHOTFIX_PATH + "/";

        arkPatchList.clear();
        ShareArkHotDiffPatchInfo.parseDiffPatchInfo(meta, arkPatchList);

        if (!extractArkHotLibrary(context, dir, patchFile, TYPE_ARKHOT_SO)) {
            return false;
        }

        return true;
    }
}
