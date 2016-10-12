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

package com.tencent.tinker.build.auxiliaryinject;

import com.tencent.tinker.commons.ziputil.Streams;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by tangyinsheng on 2016/10/9.
 */

public final class AuxiliaryInjector {
    public static final String AUXILIARY_CLASSNAME = "dalvik.system.PathClassLoader";

    public static void processClass(File classIn, File classOut) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new BufferedInputStream(new FileInputStream(classIn));
            os = new BufferedOutputStream(new FileOutputStream(classOut));
            processClass(is, os);
        } finally {
            closeQuietly(os);
            closeQuietly(is);
        }
    }

    public static void processJar(File jarIn, File jarOut) throws IOException {
        try {
            processJarHelper(jarIn, jarOut, Charset.forName("UTF-8"), Charset.forName("UTF-8"));
        } catch (IllegalArgumentException e) {
            if ("MALFORMED".equals(e.getMessage())) {
                processJarHelper(jarIn, jarOut, Charset.forName("GBK"), Charset.forName("UTF-8"));
            } else {
                throw e;
            }
        }
    }

    @SuppressWarnings("NewApi")
    private static void processJarHelper(File jarIn, File jarOut, Charset charsetIn, Charset charsetOut) throws IOException {
        ZipInputStream zis = null;
        ZipOutputStream zos = null;
        try {
            zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(jarIn)), charsetIn);
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(jarOut)), charsetOut);
            ZipEntry entryIn = null;
            while ((entryIn = zis.getNextEntry()) != null) {
                final String entryName = entryIn.getName();
                ZipEntry entryOut = new ZipEntry(entryName);
                zos.putNextEntry(entryOut);
                if (!entryIn.isDirectory()) {
                    if (entryName.endsWith(".class")) {
                        processClass(zis, zos);
                    } else {
                        Streams.copy(zis, zos);
                    }
                }
                zos.closeEntry();
            }
        } finally {
            closeQuietly(zos);
            closeQuietly(zis);
        }
    }

    private static void processClass(InputStream classIn, OutputStream classOut) throws IOException {
        ClassReader cr = new ClassReader(classIn);
        ClassWriter cw = new ClassWriter(0);
        AuxiliaryInjectAdapter aia = new AuxiliaryInjectAdapter(AUXILIARY_CLASSNAME, cw);
        cr.accept(aia, 0);
        classOut.write(cw.toByteArray());
        classOut.flush();
    }

    private static void closeQuietly(Closeable target) {
        if (target != null) {
            try {
                target.close();
            } catch (Exception e) {
                // Ignored.
            }
        }
    }
}
