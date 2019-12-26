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

import java.util.ArrayList;

/**
 * patch via bsdiff
 * Created by zhangshaowen on 16/3/16.
 */
public class ShareBsDiffPatchInfo {
    public String name;
    public String md5;
    public String rawCrc;
    public String patchMd5;

    public String path;

    public ShareBsDiffPatchInfo(String name, String md5, String path, String raw, String patch) {
        // TODO Auto-generated constructor stub
        this.name = name;
        this.md5 = md5;
        this.rawCrc = raw;
        this.patchMd5 = patch;
        this.path = path;
    }

    public static void parseDiffPatchInfo(String meta, ArrayList<ShareBsDiffPatchInfo> diffList) {
        if (meta == null || meta.length() == 0) {
            return;
        }
        String[] lines = meta.split("\n");
        for (final String line : lines) {
            if (line == null || line.length() <= 0) {
                continue;
            }
            final String[] kv = line.split(",", 5);
            if (kv == null || kv.length < 5) {
                continue;
            }
            // key
            final String name = kv[0].trim();
            final String path = kv[1].trim();
            final String md5 = kv[2].trim();
            final String rawCrc = kv[3].trim();
            final String patchMd5 = kv[4].trim();

            ShareBsDiffPatchInfo dexInfo = new ShareBsDiffPatchInfo(name, md5, path, rawCrc, patchMd5);
            diffList.add(dexInfo);
        }

    }

    public static boolean checkDiffPatchInfo(ShareBsDiffPatchInfo info) {
        if (info == null) {
            return false;
        }
        String name = info.name;
        String md5 = info.md5;
        if (name == null || name.length() <= 0 || md5 == null || md5.length() != ShareConstants.MD5_LENGTH) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(name);
        sb.append(",");
        sb.append(path);
        sb.append(",");
        sb.append(md5);
        sb.append(",");
        sb.append(rawCrc);
        sb.append(",");
        sb.append(patchMd5);
        return sb.toString();
    }
}