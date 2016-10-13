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

package com.tencent.tinker.build.auxiliaryclass;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Pattern;

/**
 * Created by tangyinsheng on 2016/10/13.
 */

public final class AuxiliaryClassGenerator {
    private static final String JAVA_IDENTIFIER_PATTERN_STR =
            "(?:\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)";

    private static final String JAVA_FULL_CLASSNAME_PATTERN_STR =
            String.format("(%s(?:\\.%s)*)", JAVA_IDENTIFIER_PATTERN_STR,
                    JAVA_IDENTIFIER_PATTERN_STR);

    private static final Pattern JAVA_FULL_CLASSNAME_PATTERN =
            Pattern.compile(JAVA_FULL_CLASSNAME_PATTERN_STR);

    public static void generateAuxiliaryClass(File dirOutput, String dotClassName) throws IOException {
        if (!JAVA_FULL_CLASSNAME_PATTERN.matcher(dotClassName).matches()) {
            throw new IllegalArgumentException("Bad dotClassName: " + dotClassName);
        }
        if (isPrimitiveClass(dotClassName)) {
            throw new UnsupportedOperationException("Cannot generate primitive class.");
        }
        if (isArrayClass(dotClassName)) {
            throw new UnsupportedOperationException("Cannot generate array class.");
        }

        final int lastDotSepPos = dotClassName.lastIndexOf('.');
        final String classPkgPart =
                (lastDotSepPos >= 0 ? dotClassName.substring(0, lastDotSepPos) : "");
        final String classNamePart = dotClassName.substring(lastDotSepPos + 1);

        final File realDirOutput = new File(dirOutput, classPkgPart.replace('.', '/'));
        if (!realDirOutput.exists()) {
            realDirOutput.mkdirs();
        }
        final File fileOut = new File(realDirOutput, classNamePart + ".class");

        generateClass(dotClassName, fileOut);
    }

    private static void generateClass(String dotClassName, File fileOut) throws IOException {
        final String classDesc = dotClassName.replace('.', '/');
        ClassWriter cw = new ClassWriter(0);
        cw.visit(
                Opcodes.V1_7,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                classDesc,
                null,
                "java/lang/Object",
                null
        );
        cw.visitSource(fileOut.getName(), null);
        {
            MethodVisitor mv = cw.visitMethod(
                    Opcodes.ACC_PUBLIC, "<init>", "()V", null, null
            );
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "java/lang/Object",
                    "<init>",
                    "()V",
                    false
            );
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        byte[] classBytes = cw.toByteArray();

        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(fileOut));
            os.write(classBytes);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception e) {
                    // Ignored.
                }
            }
        }
    }

    private static boolean isPrimitiveClass(String className) {
        try {
            return Class.forName(className).isPrimitive();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isArrayClass(String className) {
        try {
            return Class.forName(className).isArray();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
