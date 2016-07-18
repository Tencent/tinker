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

import com.tencent.tinker.loader.TinkerRuntimeException;

import java.util.ArrayList;

/**
 * Created by shwenzhang on 16/4/11.
 */
public class ShareDexDiffPatchInfo {
    public final String rawName;
    public final String md5;
    public final String rawMd5;
    public final String patchMd5;

    public final String path;

    public final String dexMode;

    public final boolean isJarMode;

    /**
     * if it is jar mode, and the name is end of .dex, we should repackage it with zip, with renaming name.dex.jar
     */
    public final String realName;


    public ShareDexDiffPatchInfo(String name, String md5, String path, String raw, String patch, String dexMode) {
        // TODO Auto-generated constructor stub
        this.rawName = name;
        this.md5 = md5;
        this.rawMd5 = raw;
        this.patchMd5 = patch;
        this.path = path;
        this.dexMode = dexMode;
        if (dexMode.equals(ShareConstants.DEXMODE_JAR)) {
            this.isJarMode = true;
            if (SharePatchFileUtil.isRawDexFile(name)) {
                realName = name + ShareConstants.JAR_SUFFIX;
            } else {
                realName = name;
            }
        } else if (dexMode.equals(ShareConstants.DEXMODE_RAW)) {
            this.isJarMode = false;
            this.realName = name;
        } else {
            throw new TinkerRuntimeException("can't recognize dex mode:" + dexMode);
        }
    }

    public static void parseDexDiffPatchInfo(String meta, ArrayList<ShareDexDiffPatchInfo> dexList) {
        if (meta == null || meta.length() == 0) {
            return;
        }
        String[] lines = meta.split("\n");
        for (final String line : lines) {
            if (line == null || line.length() <= 0) {
                continue;
            }
            final String[] kv = line.split(",", 6);
            if (kv == null || kv.length < 6) {
                continue;
            }
            // key
            final String name = kv[0].trim();
            final String path = kv[1].trim();
            final String md5 = kv[2].trim();
            final String rawmd5 = kv[3].trim();
            final String patchmd5 = kv[4].trim();
            final String dexmode = kv[5].trim();

            ShareDexDiffPatchInfo dexinfo = new ShareDexDiffPatchInfo(name, md5, path, rawmd5, patchmd5, dexmode);
            dexList.add(dexinfo);
        }

    }

    public static boolean checkDexDiffPatchInfo(ShareDexDiffPatchInfo info) {
        if (info == null) {
            return false;
        }
        String name = info.rawName;
        String md5 = info.md5;
        if (name == null || name.length() <= 0 || md5 == null || md5.length() != ShareConstants.MD5_LENGTH) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(rawName);
        sb.append(",");
        sb.append(path);
        sb.append(",");
        sb.append(md5);
        sb.append(",");
        sb.append(rawMd5);
        sb.append(",");
        sb.append(patchMd5);
        sb.append(",");
        sb.append(dexMode);
        return sb.toString();
    }
}
