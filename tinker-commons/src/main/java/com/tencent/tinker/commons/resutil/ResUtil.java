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

package com.tencent.tinker.commons.resutil;

import com.tencent.tinker.commons.ziputil.TinkerZipEntry;
import com.tencent.tinker.commons.ziputil.TinkerZipFile;
import com.tencent.tinker.commons.ziputil.TinkerZipOutputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by zhangshaowen on 16/8/10.
 */
public class ResUtil {
    private static final int BUFFER_SIZE = 16384;

    public static void extractTinkerEntry(TinkerZipFile apk, TinkerZipEntry zipEntry, TinkerZipOutputStream outputStream) throws IOException {
        InputStream in = null;
        try {
            in = apk.getInputStream(zipEntry);
            outputStream.putNextEntry(new TinkerZipEntry(zipEntry));
            byte[] buffer = new byte[BUFFER_SIZE];

            for (int length = in.read(buffer); length != -1; length = in.read(buffer)) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.closeEntry();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    public static void extractTinkerEntry(TinkerZipEntry zipEntry, InputStream inputStream, TinkerZipOutputStream outputStream) throws IOException {
        outputStream.putNextEntry(zipEntry);
        byte[] buffer = new byte[BUFFER_SIZE];

        for (int length = inputStream.read(buffer); length != -1; length = inputStream.read(buffer)) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.closeEntry();
    }

    public static void extractLargeModifyFile(TinkerZipEntry sourceArscEntry, File newFile, long newFileCrc, TinkerZipOutputStream outputStream) throws IOException {
        TinkerZipEntry newArscZipEntry = new TinkerZipEntry(sourceArscEntry);

        newArscZipEntry.setMethod(TinkerZipEntry.STORED);
        newArscZipEntry.setSize(newFile.length());
        newArscZipEntry.setCompressedSize(newFile.length());
        newArscZipEntry.setCrc(newFileCrc);
        BufferedInputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(newFile));
            outputStream.putNextEntry(new TinkerZipEntry(newArscZipEntry));
            byte[] buffer = new byte[BUFFER_SIZE];

            for (int length = in.read(buffer); length != -1; length = in.read(buffer)) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.closeEntry();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
