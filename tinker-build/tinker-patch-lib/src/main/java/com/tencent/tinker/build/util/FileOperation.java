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

package com.tencent.tinker.build.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class FileOperation {
    public static final boolean fileExists(String filePath) {
        if (filePath == null) {
            return false;
        }

        File file = new File(filePath);
        if (file.exists()) {
            return true;
        }
        return false;
    }

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

    public static long getFileSizes(File f) {
        if (f == null) {
            return 0;
        }
        long size = 0;
        if (f.exists() && f.isFile()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(f);
                size = fis.available();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (fis != null) {
                        fis.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
            if (is != null) {
                is.close();
            }
            if (os != null) {
                os.close();
            }
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
    public static void zipFiles(Collection<File> resFileList, File zipFile) throws IOException {
        ZipOutputStream zipout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile), TypedValue.BUFFER_SIZE));
        for (File resFile : resFileList) {
            if (resFile.exists()) {
                zipFile(resFile, zipout, "");
            }
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
            //这里需要强转成linux格式，果然坑！！
            if (rootpath.contains("\\")) {
                rootpath = rootpath.replace("\\", "/");
            }
            ZipEntry entry = new ZipEntry(rootpath);
            //这里强制压缩

//            if (compressMethod == ZipEntry.DEFLATED) {
            entry.setMethod(ZipEntry.DEFLATED);
//            } else {
//                entry.setMethod(ZipEntry.STORED);
//                entry.setSize(fileContents.length);
//                final CRC32 checksumCalculator = new CRC32();
//                checksumCalculator.update(fileContents);
//                entry.setCrc(checksumCalculator.getValue());
//            }
            zipout.putNextEntry(entry);
            zipout.write(fileContents);
            zipout.flush();
            zipout.closeEntry();
        }
    }

    private static byte[] readContents(final File file) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final int bufferSize = 4096;
        try {
            final FileInputStream in = new FileInputStream(file);
            final BufferedInputStream bIn = new BufferedInputStream(in);
            int length;
            byte[] buffer = new byte[bufferSize];
            byte[] bufferCopy;
            while ((length = bIn.read(buffer, 0, bufferSize)) != -1) {
                bufferCopy = new byte[length];
                System.arraycopy(buffer, 0, bufferCopy, 0, length);
                output.write(bufferCopy);
            }
            bIn.close();
        } finally {
            output.close();
        }
        return output.toByteArray();
    }
}
