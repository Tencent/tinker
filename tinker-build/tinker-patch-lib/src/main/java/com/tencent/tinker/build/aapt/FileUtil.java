/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.tencent.tinker.build.aapt;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class FileUtil {

    private FileUtil() {
    }

    /**
     * is file exist,include directory or file
     *
     * @param path directory or file
     * @return boolean
     */
    public static boolean isExist(String path) {
        File file = new File(path);
        return file.exists();
    }

    /**
     * create directory
     *
     * @param directoryPath
     */
    public static void createDirectory(final String directoryPath) {
        File file = new File(directoryPath);
        if (!file.exists()) {
            file.setReadable(true, false);
            file.setWritable(true, true);
            file.mkdirs();
        }
    }

    /**
     * create file,full filename,signle empty file.
     *
     * @param fullFilename
     * @return boolean
     */
    public static boolean createFile(final String fullFilename) {
        boolean result = false;
        File file = new File(fullFilename);
        createDirectory(file.getParent());
        try {
            file.setReadable(true, false);
            file.setWritable(true, true);
            result = file.createNewFile();
        } catch (Exception e) {
            throw new FileUtilException(e);
        }
        return result;
    }

    /**
     * find match file
     *
     * @param sourceDirectory
     * @param fileSuffix
     * @return List<String>
     */
    public static List<String> findMatchFile(String sourceDirectory, String fileSuffix) {
        return findMatchFileOrMatchFileDirectory(sourceDirectory, fileSuffix, null, true, true);
    }

    /**
     * find match file or match file directory
     *
     * @param sourceDirectory
     * @param fileSuffix
     * @param somethingAppendToRear
     * @param isFindMatchFile
     * @param includeHidden
     * @return List<String>
     */
    private static List<String> findMatchFileOrMatchFileDirectory(String sourceDirectory, String fileSuffix, String somethingAppendToRear, boolean isFindMatchFile, boolean includeHidden) {
        fileSuffix = StringUtil.nullToBlank(fileSuffix);
        somethingAppendToRear = StringUtil.nullToBlank(somethingAppendToRear);
        List<String> list = new ArrayList<String>();
        File sourceDirectoryFile = new File(sourceDirectory);
        Queue<File> queue = new ConcurrentLinkedQueue<File>();
        queue.add(sourceDirectoryFile);
        while (!queue.isEmpty()) {
            File file = queue.poll();
            if (file.isHidden() && !includeHidden) {
                continue;
            }
            if (file.isDirectory()) {
                File[] fileArray = file.listFiles();
                if (fileArray != null) {
                    queue.addAll(Arrays.asList(fileArray));
                }
            } else if (file.isFile()) {
                if (file.getName().toLowerCase().endsWith(fileSuffix.toLowerCase())) {
                    if (isFindMatchFile) {
                        list.add(file.getAbsolutePath() + somethingAppendToRear);
                    } else {
                        String parentPath = file.getParent();
                        parentPath = parentPath + somethingAppendToRear;
                        if (!list.contains(parentPath)) {
                            list.add(parentPath);
                        }
                    }
                }
            }
        }
        return list;
    }

    public static class FileUtilException extends RuntimeException {
        private static final long serialVersionUID = 3884649425767533205L;

        public FileUtilException(Throwable cause) {
            super(cause);
        }
    }

}
