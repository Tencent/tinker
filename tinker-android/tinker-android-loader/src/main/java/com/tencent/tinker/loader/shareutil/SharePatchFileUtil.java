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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.tencent.tinker.loader.TinkerRuntimeException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class SharePatchFileUtil {
    private static final String TAG = "Tinker.PatchFileUtil";

    private static char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * data dir, such as /data/data/tinker.sample.android/tinker
     * @param context
     * @return
     */
    public static File getPatchDirectory(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        if (applicationInfo == null) {
            // Looks like running on a test Context, so just return without patching.
            return null;
        }

        return new File(applicationInfo.dataDir, ShareConstants.PATCH_DIRECTORY_NAME);
    }

    public static File getPatchTempDirectory(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        if (applicationInfo == null) {
            // Looks like running on a test Context, so just return without patching.
            return null;
        }

        return new File(applicationInfo.dataDir, ShareConstants.PATCH_TEMP_DIRECTORY_NAME);
    }

    public static File getPatchLastCrashFile(Context context) {
        File tempFile = getPatchTempDirectory(context);
        if (tempFile == null) {
            return null;
        }
        return new File(tempFile, ShareConstants.PATCH_TEMP_LAST_CRASH_NAME);
    }

    public static File getPatchInfoFile(String patchDirectory) {
        return new File(patchDirectory + "/" + ShareConstants.PATCH_INFO_NAME);
    }

    public static File getPatchInfoLockFile(String patchDirectory) {
        return new File(patchDirectory + "/" + ShareConstants.PATCH_INFO_LOCK_NAME);
    }

    public static String getPatchVersionDirectory(String version) {
        if (version == null || version.length() != ShareConstants.MD5_LENGTH) {
            return null;
        }

        return ShareConstants.PATCH_BASE_NAME + version.substring(0, 8);
    }

    public static String getPatchVersionFile(String version) {
        if (version == null || version.length() != ShareConstants.MD5_LENGTH) {
            return null;
        }

        return getPatchVersionDirectory(version) + ShareConstants.PATCH_SUFFIX;
    }

    public static boolean checkIfMd5Valid(final String object) {
        if ((object == null) || (object.length() != ShareConstants.MD5_LENGTH)) {
            return false;
        }
        return true;
    }

    public static String checkTinkerLastUncaughtCrash(Context context) {
        File crashFile = SharePatchFileUtil.getPatchLastCrashFile(context);
        if (!SharePatchFileUtil.isLegalFile(crashFile)) {
            return null;
        }
        StringBuffer buffer = new StringBuffer();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(crashFile)));
            String line;
            while ((line = in.readLine()) != null) {
                buffer.append(line);
                buffer.append("\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "checkTinkerLastUncaughtCrash exception: " + e);
            return null;
        } finally {
            closeQuietly(in);
        }

        return buffer.toString();

    }

    public static final boolean isLegalFile(File file) {
        return file != null && file.exists() && file.canRead() && file.isFile() && file.length() > 0;
    }

    /**
     * get directory size
     *
     * @param directory
     * @return
     */
    public static long getFileOrDirectorySize(File directory) {
        if (directory == null || !directory.exists()) {
            return 0;
        }
        if (directory.isFile()) {
            return directory.length();
        }
        long totalSize = 0;
        File[] fileList = directory.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isDirectory()) {
                    totalSize = totalSize + getFileOrDirectorySize(file);
                } else {
                    totalSize = totalSize + file.length();
                }
            }
        }
        return totalSize;
    }

    public static final boolean safeDeleteFile(File file) {
        if (file == null) {
            return true;
        }

        Log.i(TAG, "safeDeleteFile, try to delete path: " + file.getPath());

        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                Log.e(TAG, "Failed to delete file, try to delete when exit. path: " + file.getPath());
                file.deleteOnExit();
            }
            return deleted;
        }
        return true;
    }

    public static final boolean deleteDir(String dir) {
        if (dir == null) {
            return false;
        }
        return deleteDir(new File(dir));

    }

    public static final boolean deleteDir(File file) {
        if (file == null || (!file.exists())) {
            return false;
        }
        if (file.isFile()) {
            safeDeleteFile(file);
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File subFile : files) {
                    deleteDir(subFile);
                }
                safeDeleteFile(file);
            }
        }
        return true;
    }


    /**
     * Returns whether the file is a valid file.
     */
    public static boolean verifyFileMd5(File file, String md5) {
        if (md5 == null) {
            return false;
        }
        String fileMd5 = getMD5(file);

        if (fileMd5 == null) {
            return false;
        }

        return md5.equals(fileMd5);
    }

    public static boolean isRawDexFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.endsWith(ShareConstants.DEX_SUFFIX);
    }

    /**
     * Returns whether the dex file is a valid file.
     * dex may wrap with jar
     */
    public static boolean verifyDexFileMd5(File file, String md5) {
        return verifyDexFileMd5(file, ShareConstants.DEX_IN_JAR, md5);
    }

    public static boolean verifyDexFileMd5(File file, String entryName, String md5) {
        if (file == null || md5 == null || entryName == null) {
            return false;
        }
        //if it is not the raw dex, we check the stream instead
        String fileMd5;

        if (isRawDexFile(file.getName())) {
            fileMd5 = getMD5(file);
        } else {
            ZipFile dexJar = null;
            try {
                dexJar = new ZipFile(file);
                ZipEntry classesDex = dexJar.getEntry(entryName);
                // no code
                if (null == classesDex) {
                    Log.e(TAG, "There's no entry named: " + ShareConstants.DEX_IN_JAR + " in " + file.getAbsolutePath());
                    return false;
                }
                fileMd5 = getMD5(dexJar.getInputStream(classesDex));
            } catch (Throwable e) {
                Log.e(TAG, "Bad dex jar file: " + file.getAbsolutePath(), e);
                return false;
            } finally {
                // Bugfix: some device redefined ZipFile, which is not implemented closeable.
                // SharePatchFileUtil.closeZip(dexJar);
                if (dexJar != null) {
                    try {
                        dexJar.close();
                    } catch (Throwable thr) {
                        // Ignored.
                    }
                }
            }
        }

        return md5.equals(fileMd5);
    }

    public static void copyFileUsingStream(File source, File dest) throws IOException {
        if (!SharePatchFileUtil.isLegalFile(source) || dest == null) {
            return;
        }
        if (source.getAbsolutePath().equals(dest.getAbsolutePath())) {
            return;
        }
        FileInputStream is = null;
        FileOutputStream os = null;
        File parent = dest.getParentFile();
        if (parent != null && (!parent.exists())) {
            parent.mkdirs();
        }
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest, false);

            byte[] buffer = new byte[ShareConstants.BUFFER_SIZE];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            closeQuietly(is);
            closeQuietly(os);
        }
    }

    /**
     * for faster, read and get the contents
     *
     * @throws IOException
     */
    public static String loadDigestes(JarFile jarFile, JarEntry je) throws Exception {
        InputStream bis = null;
        StringBuilder sb = new StringBuilder();

        try {
            InputStream is = jarFile.getInputStream(je);
            byte[] bytes = new byte[ShareConstants.BUFFER_SIZE];
            bis = new BufferedInputStream(is);
            int readBytes;
            while ((readBytes = bis.read(bytes)) > 0) {
                sb.append(new String(bytes, 0, readBytes));
            }
        } finally {
            closeQuietly(bis);
        }
        return sb.toString();
    }

    /**
     * Get the md5 for inputStream.
     * This method cost less memory. It read bufLen bytes from the FileInputStream once.
     *
     * @param is
     */
    public final static String getMD5(final InputStream is) {
        if (is == null) {
            return null;
        }
        try {
            BufferedInputStream bis = new BufferedInputStream(is);
            MessageDigest md = MessageDigest.getInstance("MD5");
            StringBuilder md5Str = new StringBuilder(32);

            byte[] buf = new byte[ShareConstants.MD5_FILE_BUF_LENGTH];
            int readCount;
            while ((readCount = bis.read(buf)) != -1) {
                md.update(buf, 0, readCount);
            }

            byte[] hashValue = md.digest();

            for (int i = 0; i < hashValue.length; i++) {
                md5Str.append(Integer.toString((hashValue[i] & 0xff) + 0x100, 16).substring(1));
            }
            return md5Str.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String getMD5(byte[] buffer) {
        try {
            MessageDigest mdTemp = MessageDigest.getInstance("MD5");
            mdTemp.update(buffer);
            byte[] md = mdTemp.digest();
            int j = md.length;
            char[] str = new char[j * 2];
            int k = 0;
            for (int i = 0; i < j; i++) {
                byte byte0 = md[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the md5 for the file. call getMD5(FileInputStream is, int bufLen) inside.
     *
     * @param file
     */
    public static String getMD5(final File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            String md5 = getMD5(fin);
            return md5;
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(fin);
        }
    }

    /**
     * change the jar file path as the makeDexElements do
     * Android O change its path
     *
     * @param path
     * @param optimizedDirectory
     * @return
     */
    public static String optimizedPathFor(File path, File optimizedDirectory) {
        if (ShareTinkerInternals.isAfterAndroidO()) {
            // dex_location = /foo/bar/baz.jar
            // odex_location = /foo/bar/oat/<isa>/baz.odex

            String currentInstructionSet;
            try {
                currentInstructionSet = ShareTinkerInternals.getCurrentInstructionSet();
            } catch (Exception e) {
                throw new TinkerRuntimeException("getCurrentInstructionSet fail:", e);
            }

            File parentFile = path.getParentFile();
            String fileName = path.getName();
            int index = fileName.lastIndexOf('.');
            if (index > 0) {
                fileName = fileName.substring(0, index);
            }

            String result = parentFile.getAbsolutePath() + "/oat/"
                + currentInstructionSet + "/" + fileName + ShareConstants.ODEX_SUFFIX;
            return result;
        }

        String fileName = path.getName();
        if (!fileName.endsWith(ShareConstants.DEX_SUFFIX)) {
            int lastDot = fileName.lastIndexOf(".");
            if (lastDot < 0) {
                fileName += ShareConstants.DEX_SUFFIX;
            } else {
                StringBuilder sb = new StringBuilder(lastDot + 4);
                sb.append(fileName, 0, lastDot);
                sb.append(ShareConstants.DEX_SUFFIX);
                fileName = sb.toString();
            }
        }

        File result = new File(optimizedDirectory, fileName);
        return result.getPath();
    }

    /**
     * Closes the given {@code Closeable}. Suppresses any IO exceptions.
     */
    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to close resource", e);
        }
    }

    public static void closeZip(ZipFile zipFile) {
        try {
            if (zipFile != null) {
                zipFile.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to close resource", e);
        }
    }

    public static boolean checkResourceArscMd5(File resOutput, String destMd5) {
        ZipFile resourceZip = null;
        try {
            resourceZip = new ZipFile(resOutput);
            ZipEntry arscEntry = resourceZip.getEntry(ShareConstants.RES_ARSC);
            if (arscEntry == null) {
                Log.i(TAG, "checkResourceArscMd5 resources.arsc not found");
                return false;
            }
            InputStream inputStream = null;
            try {
                inputStream = resourceZip.getInputStream(arscEntry);
                String md5 = SharePatchFileUtil.getMD5(inputStream);
                if (md5 != null && md5.equals(destMd5)) {
                    return true;
                }
            } finally {
                SharePatchFileUtil.closeQuietly(inputStream);
            }

        } catch (Throwable e) {
            Log.i(TAG, "checkResourceArscMd5 throwable:" + e.getMessage());

        } finally {
            SharePatchFileUtil.closeZip(resourceZip);
        }
        return false;
    }

    public static void ensureFileDirectory(File file) {
        if (file == null) {
            return;
        }
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
    }

}

