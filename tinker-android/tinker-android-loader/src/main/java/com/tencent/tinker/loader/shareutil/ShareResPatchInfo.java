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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Created by zhangshaowen on 16/8/9.
 */
public class ShareResPatchInfo {
    public String arscBaseCrc = null;

    public String                resArscMd5 = null;
    public ArrayList<String>     addRes     = new ArrayList<>();
    public ArrayList<String>     deleteRes  = new ArrayList<>();
    public ArrayList<String>     modRes     = new ArrayList<>();
    public HashMap<String, File> storeRes   = new HashMap<>();

    //use linkHashMap instead?
    public ArrayList<String>              largeModRes = new ArrayList<>();
    public HashMap<String, LargeModeInfo> largeModMap = new HashMap<>();

    public HashSet<Pattern> patterns = new HashSet<>();

    public static void parseAllResPatchInfo(String meta, ShareResPatchInfo info) {
        if (meta == null || meta.length() == 0) {
            return;
        }
        String[] lines = meta.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.length() <= 0) {
                continue;
            }
            if (line.startsWith(ShareConstants.RES_TITLE)) {
                final String[] kv = line.split(",", 3);
                info.arscBaseCrc = kv[1];
                info.resArscMd5 = kv[2];
            } else if (line.startsWith(ShareConstants.RES_PATTERN_TITLE)) {
                final String[] kv = line.split(":", 2);
                int size = Integer.parseInt(kv[1]);
                for (; size > 0; size--) {
                    info.patterns.add(convertToPatternString(lines[i + 1]));
                    i++;
                }
            } else if (line.startsWith(ShareConstants.RES_ADD_TITLE)) {
                final String[] kv = line.split(":", 2);
                int size = Integer.parseInt(kv[1]);
                for (; size > 0; size--) {
                    info.addRes.add(lines[i + 1]);
                    i++;
                }
            } else if (line.startsWith(ShareConstants.RES_MOD_TITLE)) {
                final String[] kv = line.split(":", 2);
                int size = Integer.parseInt(kv[1]);
                for (; size > 0; size--) {
                    info.modRes.add(lines[i + 1]);
                    i++;
                }
            } else if (line.startsWith(ShareConstants.RES_LARGE_MOD_TITLE)) {
                final String[] kv = line.split(":", 2);
                int size = Integer.parseInt(kv[1]);
                for (; size > 0; size--) {
                    String nextLine = lines[i + 1];
                    final String[] data = nextLine.split(",", 3);
                    String name = data[0];
                    LargeModeInfo largeModeInfo = new LargeModeInfo();
                    largeModeInfo.md5 = data[1];
                    largeModeInfo.crc = Long.parseLong(data[2]);
                    info.largeModRes.add(name);
                    info.largeModMap.put(name, largeModeInfo);
                    i++;
                }
            } else if (line.startsWith(ShareConstants.RES_DEL_TITLE)) {
                final String[] kv = line.split(":", 2);
                int size = Integer.parseInt(kv[1]);
                for (; size > 0; size--) {
                    info.deleteRes.add(lines[i + 1]);
                    i++;
                }
            } else if (line.startsWith(ShareConstants.RES_STORE_TITLE)) {
                final String[] kv = line.split(":", 2);
                int size = Integer.parseInt(kv[1]);
                for (; size > 0; size--) {
                    info.storeRes.put(lines[i + 1], null);
                    i++;
                }
            }
        }

    }

    public static boolean checkFileInPattern(HashSet<Pattern> patterns, String key) {
        if (!patterns.isEmpty()) {
            for (Iterator<Pattern> it = patterns.iterator(); it.hasNext();) {
                Pattern p = it.next();
                if (p.matcher(key).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean checkResPatchInfo(ShareResPatchInfo info) {
        if (info == null) {
            return false;
        }
        String md5 = info.resArscMd5;
        if (md5 == null || md5.length() != ShareConstants.MD5_LENGTH) {
            return false;
        }
        return true;
    }

    private static Pattern convertToPatternString(String input) {
        //convert \\.
        if (input.contains(".")) {
            input = input.replaceAll("\\.", "\\\\.");
        }
        //convert ？to .
        if (input.contains("?")) {
            input = input.replaceAll("\\?", "\\.");
        }
        //convert * to.*
        if (input.contains("*")) {
            input = input.replace("*", ".*");
        }
        Pattern pattern = Pattern.compile(input);
        return pattern;
    }

    public static void parseResPatchInfoFirstLine(String meta, ShareResPatchInfo info) {
        if (meta == null || meta.length() == 0) {
            return;
        }
        String[] lines = meta.split("\n");
        String firstLine = lines[0];
        if (firstLine == null || firstLine.length() <= 0) {
            throw new TinkerRuntimeException("res meta Corrupted:" + meta);
        }
        final String[] kv = firstLine.split(",", 3);
        info.arscBaseCrc = kv[1];
        info.resArscMd5 = kv[2];
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("resArscMd5:" + resArscMd5 + "\n");
        sb.append("arscBaseCrc:" + arscBaseCrc + "\n");

        for (Pattern pattern : patterns) {
            sb.append("pattern:" + pattern + "\n");
        }
        for (String add : addRes) {
            sb.append("addedSet:" + add + "\n");
        }
        for (String mod : modRes) {
            sb.append("modifiedSet:" + mod + "\n");
        }
        for (String largeMod : largeModRes) {
            sb.append("largeModifiedSet:" + largeMod + "\n");
        }
        for (String del : deleteRes) {
            sb.append("deletedSet:" + del + "\n");
        }
        for (String store : storeRes.keySet()) {
            sb.append("storeSet:" + store + "\n");
        }
        return sb.toString();
    }

    public static class LargeModeInfo {
        public String md5 = null;
        public long crc;
        public File file = null;
    }

}
