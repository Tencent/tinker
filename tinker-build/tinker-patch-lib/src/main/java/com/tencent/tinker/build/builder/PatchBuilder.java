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

package com.tencent.tinker.build.builder;


import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.TypedValue;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhangshaowen
 */
public class PatchBuilder {
    private static final String PATCH_NAME = "patch";
    private final Configuration config;
    private       File          unSignedApk;
    private       File          signedApk;
    private       File          signedWith7ZipApk;
    private       File          sevenZipOutPutDir;

    public PatchBuilder(Configuration config) {
        this.config = config;
        unSignedApk = new File(config.mOutFolder, PATCH_NAME + "_unsigned.apk");
        signedApk = new File(config.mOutFolder, PATCH_NAME + "_signed.apk");
        signedWith7ZipApk = new File(config.mOutFolder, PATCH_NAME + "_signed_7zip.apk");
        sevenZipOutPutDir = new File(config.mOutFolder, TypedValue.OUT_7ZIP_FILE_PATH);
    }

    public void buildPatch() throws IOException, InterruptedException {
        final File resultDir = config.mTempResultDir;
        if (!resultDir.exists()) {
            throw new IOException(String.format(
                "Missing patch unzip files, path=%s\n", resultDir.getAbsolutePath()));
        }
        //no file change
        if (resultDir.listFiles().length == 0) {
            return;
        }
        generalUnsignApk();
        signApk();
        use7zApk();
    }


    private void signApk() throws IOException, InterruptedException {
        //尝试去对apk签名
        if (config.mUseSignAPk) {
            Logger.d("signing apk: %s", signedApk.getName());
            if (signedApk.exists()) {
                signedApk.delete();
            }
            String cmd = "jarsigner -sigalg MD5withRSA -digestalg SHA1 -keystore " + config.mSignatureFile
                + " -storepass " + config.mStorePass
                + " -keypass " + config.mKeyPass
                + " -signedjar " + signedApk.getAbsolutePath()
                + " " + unSignedApk.getAbsolutePath()
                + " " + config.mStoreAlias;
            Process pro = Runtime.getRuntime().exec(cmd);
            //destroy the stream
            pro.waitFor();
            pro.destroy();

            if (!signedApk.exists()) {
                throw new IOException("Can't Generate signed APK. Plz check your sign info is correct.");
            }
        }
    }

    private void generalUnsignApk() throws IOException {
        Logger.d("general unsigned apk: %s", unSignedApk.getName());
        final File tempOutDir = config.mTempResultDir;
        if (!tempOutDir.exists()) {
            throw new IOException(String.format(
                "Missing patch unzip files, path=%s\n", tempOutDir.getAbsolutePath()));
        }

        File[] unzipFiles = tempOutDir.listFiles();
        List<File> collectFiles = new ArrayList<>();
        for (File f : unzipFiles) {
            collectFiles.add(f);
        }

        FileOperation.zipFiles(collectFiles, unSignedApk);

        if (!unSignedApk.exists()) {
            throw new IOException(String.format(
                "can not found the unsign apk file path=%s",
                unSignedApk.getAbsolutePath()));
        }
    }

    private void use7zApk() throws IOException, InterruptedException {
        if (!config.mUseSignAPk) {
            return;
        }
        if (!signedApk.exists()) {
            throw new IOException(
                String.format("can not found the signed apk file to 7z, if you want to use 7z, "
                    + "you must fill the sign data in the config file path=%s", signedApk.getAbsolutePath())
            );
        }
        Logger.d("try use 7za to compress the patch file: %s, will cost much more time", signedWith7ZipApk.getName());
        FileOperation.unZipAPk(signedApk.getAbsolutePath(), sevenZipOutPutDir.getAbsolutePath());
        //首先一次性生成一个全部都是压缩的安装包
        String outPath = sevenZipOutPutDir.getAbsoluteFile().getAbsolutePath();
        String path = outPath + File.separator + "*";
        //极限压缩
        String cmd = config.mSevenZipPath;
        Logger.d("current 7za path:%s", cmd);

        ProcessBuilder pb = new ProcessBuilder(cmd, "a", "-tzip", signedWith7ZipApk.getAbsolutePath(), path, "-mx9");
        Process pro = null;
        try {
            pro = pb.start();
            InputStreamReader ir = new InputStreamReader(pro.getInputStream());
            LineNumberReader input = new LineNumberReader(ir);
            //如果不读会有问题，被阻塞
            while (input.readLine() != null) {
            }
            //destroy the stream
            pro.waitFor();
            pro.destroy();
        } catch (IOException | InterruptedException e) {
//            e.printStackTrace();
            Logger.e("7a patch file failed, you should set the zipArtifact, or set the path directly");
            return;
        }

        if (!signedWith7ZipApk.exists()) {
            throw new IOException(String.format(
                "[use7zApk]7z repackage signed apk fail,you must install 7z command line version first, linux: p7zip, window: 7za, path=%s",
                signedWith7ZipApk.getAbsolutePath()));
        }
    }
}
