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

import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.commons.util.IOHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class FileOperation {
    public static final boolean deleteFile(String filePath) {
        if (filePath == null) {
            return true;
        }

        File file = new File(filePath);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    public static final boolean deleteFile(File file) {
        if (file == null) {
            return true;
        }
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    public static boolean isLegalFile(String path) {
        if (path == null) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.isFile() && file.length() > 0;
    }

    public static long getFileSizes(File f) {
        if (f == null) {
            return 0;
        }
        long size = 0;
        if (f.exists() && f.isFile()) {
            InputStream fis = null;
            try {
                fis = new BufferedInputStream(new FileInputStream(f));
                size = fis.available();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                IOHelper.closeQuietly(fis);
            }
        }
        return size;
    }

    public static final boolean deleteDir(File file) {
        if (file == null || (!file.exists())) {
            return false;
        }
        if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                deleteDir(files[i]);
            }
        }
        file.delete();
        return true;
    }

    public static void cleanDir(File dir) {
        if (dir.exists()) {
            FileOperation.deleteDir(dir);
            dir.mkdirs();
        }
    }

    public static void copyResourceUsingStream(String name, File dest) throws IOException {
        FileOutputStream os = null;
        File parent = dest.getParentFile();
        if (parent != null && (!parent.exists())) {
            parent.mkdirs();
        }
        InputStream is = null;

        try {
            is = FileOperation.class.getResourceAsStream("/" + name);
            os = new FileOutputStream(dest, false);

            byte[] buffer = new byte[TypedValue.BUFFER_SIZE];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            IOHelper.closeQuietly(os);
            IOHelper.closeQuietly(is);
        }
    }

    public static void copyFileUsingStream(File source, File dest) throws IOException {
        FileInputStream is = null;
        FileOutputStream os = null;
        File parent = dest.getParentFile();
        if (parent != null && (!parent.exists())) {
            parent.mkdirs();
        }
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest, false);

            byte[] buffer = new byte[TypedValue.BUFFER_SIZE];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            IOHelper.closeQuietly(os);
            IOHelper.closeQuietly(is);
        }
    }

    public static boolean checkDirectory(String dir) {
        File dirObj = new File(dir);
        deleteDir(dirObj);

        if (!dirObj.exists()) {
            dirObj.mkdirs();
        }
        return true;
    }

    @SuppressWarnings("rawtypes")
    public static void unZipAPk(String fileName, String filePath) throws IOException {
        checkDirectory(filePath);

        ZipFile zipFile = new ZipFile(fileName);
        Enumeration enumeration = zipFile.entries();
        try {
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) enumeration.nextElement();
                if (!validateZipEntryName(new File(filePath), entry.getName())) {
                    throw new IOException("Bad ZipEntry name: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    new File(filePath, entry.getName()).mkdirs();
                    continue;
                }
                BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));

                File file = new File(filePath + File.separator + entry.getName());

                File parentFile = file.getParentFile();
                if (parentFile != null && (!parentFile.exists())) {
                    parentFile.mkdirs();
                }
                FileOutputStream fos = null;
                BufferedOutputStream bos = null;
                try {
                    fos = new FileOutputStream(file);
                    bos = new BufferedOutputStream(fos, TypedValue.BUFFER_SIZE);

                    byte[] buf = new byte[TypedValue.BUFFER_SIZE];
                    int len;
                    while ((len = bis.read(buf, 0, TypedValue.BUFFER_SIZE)) != -1) {
                        fos.write(buf, 0, len);
                    }
                } finally {
                    if (bos != null) {
                        bos.flush();
                        bos.close();
                    }
                    if (bis != null) {
                        bis.close();
                    }
                }
            }
        } finally {
            if (zipFile != null) {
                zipFile.close();
            }
        }
    }

    /**
     * zip list of file
     *
     * @param resFileList file(dir) list
     * @param zipFile     output zip file
     * @throws IOException
     */
    public static void zipFiles(Collection<File> resFileList, File zipFile, String comment) throws IOException {
        ZipOutputStream zipout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile), TypedValue.BUFFER_SIZE));
        for (File resFile : resFileList) {
            if (resFile.exists()) {
                zipFile(resFile, zipout, "");
            }
        }
        if (comment != null) {
            zipout.setComment(comment);
        }
        zipout.close();
    }

    private static void zipFile(File resFile, ZipOutputStream zipout, String rootpath) throws IOException {
        rootpath = rootpath + (rootpath.trim().length() == 0 ? "" : File.separator) + resFile.getName();
        if (resFile.isDirectory()) {
            File[] fileList = resFile.listFiles();
            for (File file : fileList) {
                zipFile(file, zipout, rootpath);
            }
        } else {
            final byte[] fileContents = readContents(resFile);
            //linux format！！
            if (rootpath.contains("\\")) {
                rootpath = rootpath.replace("\\", "/");
            }
            ZipEntry entry = new ZipEntry(rootpath);
            // if (compressMethod == ZipEntry.DEFLATED) {
            entry.setMethod(ZipEntry.DEFLATED);
            // } else {
            //     entry.setMethod(ZipEntry.STORED);
            //     entry.setSize(fileContents.length);
            //     final CRC32 checksumCalculator = new CRC32();
            //     checksumCalculator.update(fileContents);
            //     entry.setCrc(checksumCalculator.getValue());
            // }
            zipout.putNextEntry(entry);
            zipout.write(fileContents);
            zipout.flush();
            zipout.closeEntry();
        }
    }

    private static byte[] readContents(final File file) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final int bufferSize = TypedValue.BUFFER_SIZE;
        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(file));
            int length;
            byte[] buffer = new byte[bufferSize];
            byte[] bufferCopy;
            while ((length = in.read(buffer, 0, bufferSize)) > 0) {
                bufferCopy = new byte[length];
                System.arraycopy(buffer, 0, bufferCopy, 0, length);
                output.write(bufferCopy);
            }
        } finally {
            IOHelper.closeQuietly(output);
            IOHelper.closeQuietly(in);
        }
        return output.toByteArray();
    }

    public static long getFileCrc32(File file) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(file));
            CRC32 crc = new CRC32();
            int cnt;
            while ((cnt = inputStream.read()) != -1) {
                crc.update(cnt);
            }
            return crc.getValue();
        } finally {
            IOHelper.closeQuietly(inputStream);
        }
    }

    public static String getZipEntryCrc(File file, String entryName) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            return String.valueOf(entry.getCrc());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static String getZipEntryMd5(File file, String entryName) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            return MD5.getMD5(zipFile.getInputStream(entry), 1024 * 100);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static void zipInputDir(File inputDir, File outputFile, String comment) throws IOException {
        File[] unzipFiles = inputDir.listFiles();
        List<File> collectFiles = new ArrayList<>();
        for (File f : unzipFiles) {
            collectFiles.add(f);
        }

        FileOperation.zipFiles(collectFiles, outputFile, comment);
    }

    public static boolean sevenZipInputDir(File inputDir, File outputFile, Configuration config) {
        String outPath = inputDir.getAbsolutePath();
        String path = outPath + File.separator + "*";
        String cmd = config.mSevenZipPath;

        ProcessBuilder pb = new ProcessBuilder(cmd, "a", "-tzip", outputFile.getAbsolutePath(), path, "-mx9");
        pb.redirectErrorStream(true);
        Process pro = null;
        LineNumberReader reader = null;
        try {
            pro = pb.start();
            reader = new LineNumberReader(new InputStreamReader(pro.getInputStream()));
            while (reader.readLine() != null) {
            }
        } catch (IOException e) {
            FileOperation.deleteFile(outputFile);
            Logger.e("7a patch file failed, you should set the zipArtifact, or set the path directly");
            return false;
        } finally {
            //destroy the stream
            try {
                pro.waitFor();
            } catch (Throwable ignored) {
                // Ignored.
            }
            try {
                pro.destroy();
            } catch (Throwable ignored) {
                // Ignored.
            }
            IOHelper.closeQuietly(reader);
        }
        return true;
    }

    private static boolean validateZipEntryName(File destDir, String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return false;
        }
        try {
            final String canonicalDestinationDir = destDir.getCanonicalPath();
            final File destEntryFile = destDir.toPath().resolve(entryName).toFile();
            return destEntryFile.getCanonicalPath().startsWith(canonicalDestinationDir + File.separator);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
