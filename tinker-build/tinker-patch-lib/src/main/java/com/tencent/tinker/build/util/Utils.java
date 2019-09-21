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

package com.tencent.tinker.build.util;

import com.tencent.tinker.build.decoder.ResDiffDecoder;
import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.commons.util.IOHelper;
import com.tencent.tinker.ziputils.ziputil.TinkerZipEntry;
import com.tencent.tinker.ziputils.ziputil.TinkerZipFile;
import com.tencent.tinker.ziputils.ziputil.TinkerZipOutputStream;
import com.tencent.tinker.ziputils.ziputil.TinkerZipUtil;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Created by sun on 1/9/16.
 */
public class Utils {
    public static boolean isPresent(String str) {
        return str != null && str.length() > 0;
    }

    public static boolean isBlank(String str) {
        return !isPresent(str);
    }

    public static boolean isPresent(Iterator iterator) {
        return iterator != null && iterator.hasNext();
    }

    public static boolean isBlank(Iterator iterator) {
        return !isPresent(iterator);
    }

    public static String convertToPatternString(String input) {
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
        return input;
    }

    public static boolean isNullOrNil(final String object) {
        return (object == null) || (object.length() <= 0);
    }

    public static boolean isNullOrNil(final Collection<?> collection) {
        return (collection == null || collection.isEmpty());
    }

    public static boolean isStringMatchesPatterns(String str, Collection<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(str).matches()) {
                return true;
            }
        }
        return false;
    }

    public static <T> String collectionToString(Collection<T> collection) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean isFirstElement = true;
        for (T element : collection) {
            if (isFirstElement) {
                isFirstElement = false;
            } else {
                sb.append(',');
            }
            sb.append(element);
        }
        sb.append('}');
        return sb.toString();
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

    public static String genResOutputFile(File output, File newZipFile, Configuration config,
                                    ArrayList<String> addedSet, ArrayList<String> modifiedSet, ArrayList<String> deletedSet,
                                    ArrayList<String> largeModifiedSet, HashMap<String, ResDiffDecoder.LargeModeInfo> largeModifiedMap) throws IOException {
        TinkerZipFile oldApk = null;
        TinkerZipFile newApk = null;
        TinkerZipOutputStream out = null;

        try {
            oldApk = new TinkerZipFile(config.mOldApkFile);
            newApk = new TinkerZipFile(newZipFile);
            out = new TinkerZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)));

            final Enumeration<? extends TinkerZipEntry> entries = oldApk.entries();
            while (entries.hasMoreElements()) {
                TinkerZipEntry zipEntry = entries.nextElement();
                if (zipEntry == null) {
                    throw new TinkerPatchException(
                        String.format("zipEntry is null when get from oldApk")
                    );
                }
                String name = zipEntry.getName();
                if (!TinkerZipUtil.validateZipEntryName(output.getParentFile(), name)) {
                    throw new IOException("Bad ZipEntry name: " + name);
                }
                if (Utils.checkFileInPattern(config.mResFilePattern, name)) {
                    //won't contain in add set.
                    if (!deletedSet.contains(name)
                        && !modifiedSet.contains(name)
                        && !largeModifiedSet.contains(name)
                        && !name.equals(TypedValue.RES_MANIFEST)) {
                        TinkerZipUtil.extractTinkerEntry(oldApk, zipEntry, out);
                    }
                }
            }
            //process manifest
            TinkerZipEntry manifestZipEntry = oldApk.getEntry(TypedValue.RES_MANIFEST);
            if (manifestZipEntry == null) {
                throw new TinkerPatchException(
                    String.format("can't found resource file %s from old apk file %s", TypedValue.RES_MANIFEST, config.mOldApkFile.getAbsolutePath())
                );
            }
            TinkerZipUtil.extractTinkerEntry(oldApk, manifestZipEntry, out);

            for (String name : largeModifiedSet) {
                TinkerZipEntry largeZipEntry = oldApk.getEntry(name);
                if (largeZipEntry == null) {
                    throw new TinkerPatchException(
                        String.format("can't found resource file %s from old apk file %s", name, config.mOldApkFile.getAbsolutePath())
                    );
                }
                ResDiffDecoder.LargeModeInfo largeModeInfo = largeModifiedMap.get(name);
                TinkerZipUtil.extractLargeModifyFile(largeZipEntry, largeModeInfo.path, largeModeInfo.crc, out);
            }

            for (String name : addedSet) {
                TinkerZipEntry addZipEntry = newApk.getEntry(name);
                if (addZipEntry == null) {
                    throw new TinkerPatchException(
                        String.format("can't found add resource file %s from new apk file %s", name, config.mNewApkFile.getAbsolutePath())
                    );
                }
                TinkerZipUtil.extractTinkerEntry(newApk, addZipEntry, out);
            }

            for (String name : modifiedSet) {
                TinkerZipEntry modZipEntry = newApk.getEntry(name);
                if (modZipEntry == null) {
                    throw new TinkerPatchException(
                        String.format("can't found add resource file %s from new apk file %s", name, config.mNewApkFile.getAbsolutePath())
                    );
                }
                TinkerZipUtil.extractTinkerEntry(newApk, modZipEntry, out);
            }
        } finally {
            IOHelper.closeQuietly(out);
            IOHelper.closeQuietly(oldApk);
            IOHelper.closeQuietly(newApk);
        }
        return MD5.getMD5(output);
    }

    public static String getResourceMeta(String baseCrc, String md5) {
        return TypedValue.RES_OUT + "," + baseCrc + "," + md5;
    }

    /**
     * if bsDiff result is too larger, just treat it as newly file
     * @param bsDiffFile
     * @param newFile
     * @return
     */
    public static boolean checkBsDiffFileSize(File bsDiffFile, File newFile) {
        if (!bsDiffFile.exists()) {
            throw new TinkerPatchException("can not find the bsDiff file:" + bsDiffFile.getAbsolutePath());
        }

        //check bsDiffFile file size
        double ratio = bsDiffFile.length() / (double) newFile.length();
        if (ratio > TypedValue.BSDIFF_PATCH_MAX_RATIO) {
            Logger.e("bsDiff patch file:%s, size:%dk, new file:%s, size:%dk. patch file is too large, treat it as newly file to save patch time!",
                bsDiffFile.getName(),
                bsDiffFile.length() / 1024,
                newFile.getName(),
                newFile.length() / 1024
            );
            return false;
        }
        return true;
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
