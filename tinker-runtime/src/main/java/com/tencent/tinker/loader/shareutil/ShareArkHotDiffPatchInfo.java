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

package com.tencent.tinker.loader.shareutil;

import java.util.ArrayList;

public class ShareArkHotDiffPatchInfo {
    public String path;
    public String name;
    public String patchMd5;

    public ShareArkHotDiffPatchInfo(String path, String name, String md5) {
        this.name = name;
        this.patchMd5 = md5;
        this.path = path;
    }

    public static void parseDiffPatchInfo(String meta, ArrayList<ShareArkHotDiffPatchInfo> diffList) {
        if (meta == null || diffList == null) {
            return;
        }

        String[] lines = meta.split("\n");
        for (final String line : lines) {
            if (line == null || line.length() <= 0) {
                continue;
            }

            final String[] kv = line.split(",", 4);
            if (kv == null || kv.length < 3) {
                continue;
            }

            final String name = kv[0].trim();
            final String path = kv[1].trim();
            final String md5 = kv[2].trim();

            ShareArkHotDiffPatchInfo arkDiffInfo = new ShareArkHotDiffPatchInfo(path, name, md5);
            diffList.add(arkDiffInfo);
        }
    }

    public static boolean checkDiffPatchInfo(ShareArkHotDiffPatchInfo info) {
        if (info == null) {
            return false;
        }
        String name = info.name;
        String md5 = info.patchMd5;
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
        sb.append(patchMd5);

        return sb.toString();
    }
}
