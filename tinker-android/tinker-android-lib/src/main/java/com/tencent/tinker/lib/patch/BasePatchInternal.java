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

import com.tencent.tinker.commons.util.IOHelper;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by zhangshaowen on 16/4/12.
 */
public class BasePatchInternal {
    protected static final String TAG = "Tinker.BasePatchInternal";

    protected static final String DEX_PATH             = ShareConstants.DEX_PATH;
    protected static final String SO_PATH              = ShareConstants.SO_PATH;
    protected static final String DEX_OPTIMIZE_PATH    = ShareConstants.DEFAULT_DEX_OPTIMIZE_PATH;
    protected static final int    MAX_EXTRACT_ATTEMPTS = ShareConstants.MAX_EXTRACT_ATTEMPTS;
    protected static final String DEX_META_FILE        = ShareConstants.DEX_META_FILE;
    protected static final String SO_META_FILE         = ShareConstants.SO_META_FILE;
    protected static final String RES_META_FILE        = ShareConstants.RES_META_FILE;
    protected static final String ARKHOT_META_FILE = ShareConstants.ARKHOT_META_FILE;

    protected static final int TYPE_DEX         = ShareConstants.TYPE_DEX;
    protected static final int TYPE_LIBRARY     = ShareConstants.TYPE_LIBRARY;
    protected static final int TYPE_RESOURCE    = ShareConstants.TYPE_RESOURCE;
    protected static final int TYPE_CLASS_N_DEX = ShareConstants.TYPE_CLASS_N_DEX;
    protected static final int TYPE_ARKHOT_SO = ShareConstants.TYPE_ARKHOT_SO;


    public static boolean extract(ZipFile zipFile, ZipEntry entryFile, File extractTo, String targetMd5, boolean isDex) throws IOException {
        int numAttempts = 0;
        boolean isExtractionSuccessful = false;
        while (numAttempts < MAX_EXTRACT_ATTEMPTS && !isExtractionSuccessful) {
            numAttempts++;
            InputStream is = null;
            OutputStream os = null;

            TinkerLog.i(TAG, "try Extracting " + extractTo.getPath());

            try {
                is = new BufferedInputStream(zipFile.getInputStream(entryFile));
                os = new BufferedOutputStream(new FileOutputStream(extractTo));
                byte[] buffer = new byte[ShareConstants.BUFFER_SIZE];
                int length = 0;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
            } finally {
                IOHelper.closeQuietly(os);
                IOHelper.closeQuietly(is);
            }
            if (targetMd5 != null) {
                if (isDex) {
                    isExtractionSuccessful = SharePatchFileUtil.verifyDexFileMd5(extractTo, targetMd5);
                } else {
                    isExtractionSuccessful = SharePatchFileUtil.verifyFileMd5(extractTo, targetMd5);
                }
            } else {
                // treat it as true
                isExtractionSuccessful = true;
            }
            TinkerLog.i(TAG, "isExtractionSuccessful: %b", isExtractionSuccessful);

            if (!isExtractionSuccessful) {
                final boolean succ = extractTo.delete();
                if (!succ || extractTo.exists()) {
                    TinkerLog.e(TAG, "Failed to delete corrupted dex " + extractTo.getPath());
                }
            }
        }

        return isExtractionSuccessful;
    }

    public static int getMetaCorruptedCode(int type) {
        if (type == TYPE_DEX) {
            return ShareConstants.ERROR_PACKAGE_CHECK_DEX_META_CORRUPTED;
        } else if (type == TYPE_LIBRARY) {
            return ShareConstants.ERROR_PACKAGE_CHECK_LIB_META_CORRUPTED;
        } else if (type == TYPE_RESOURCE) {
            return ShareConstants.ERROR_PACKAGE_CHECK_RESOURCE_META_CORRUPTED;
        }
        return ShareConstants.ERROR_PACKAGE_CHECK_OK;
    }
}
