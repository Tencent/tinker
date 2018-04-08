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

import com.tencent.tinker.loader.TinkerRuntimeException;

import java.util.ArrayList;

/**
 * Created by zhangshaowen on 16/4/11.
 */
public class ShareDexDiffPatchInfo {
    public final String rawName;
    public final String destMd5InDvm;
    public final String destMd5InArt;
    public final String oldDexCrC;
    public final String newOrPatchedDexCrC;

    public final String dexDiffMd5;

    public final String path;

    public final String dexMode;

    public final boolean isJarMode;

    /**
     * if it is jar mode, and the name is end of .dex, we should repackage it with zip, with renaming name.dex.jar
     */
    public final String realName;


    public ShareDexDiffPatchInfo(String name, String path, String destMd5InDvm, String destMd5InArt,
                                 String dexDiffMd5, String oldDexCrc, String newOrPatchedDexCrC, String dexMode) {
        // TODO Auto-generated constructor stub
        this.rawName = name;
        this.path = path;
        this.destMd5InDvm = destMd5InDvm;
        this.destMd5InArt = destMd5InArt;
        this.dexDiffMd5 = dexDiffMd5;
        this.oldDexCrC = oldDexCrc;
        this.newOrPatchedDexCrC = newOrPatchedDexCrC;
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
            final String[] kv = line.split(",", 8);
            if (kv == null || kv.length < 8) {
                continue;
            }

            // key
            final String name = kv[0].trim();
            final String path = kv[1].trim();
            final String destMd5InDvm = kv[2].trim();
            final String destMd5InArt = kv[3].trim();
            final String dexDiffMd5 = kv[4].trim();
            final String oldDexCrc = kv[5].trim();
            final String newDexCrc = kv[6].trim();

            final String dexMode = kv[7].trim();

            ShareDexDiffPatchInfo dexInfo = new ShareDexDiffPatchInfo(name, path, destMd5InDvm, destMd5InArt,
                dexDiffMd5, oldDexCrc, newDexCrc, dexMode);
            dexList.add(dexInfo);
        }

    }

    public static boolean checkDexDiffPatchInfo(ShareDexDiffPatchInfo info) {
        if (info == null) {
            return false;
        }
        String name = info.rawName;
        String md5 = (ShareTinkerInternals.isVmArt() ? info.destMd5InArt : info.destMd5InDvm);
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
        sb.append(destMd5InDvm);
        sb.append(",");
        sb.append(destMd5InArt);
        sb.append(",");
        sb.append(oldDexCrC);
        sb.append(",");
        sb.append(newOrPatchedDexCrC);
        sb.append(",");
        sb.append(dexDiffMd5);
        sb.append(",");
        sb.append(dexMode);
        return sb.toString();
    }
}
