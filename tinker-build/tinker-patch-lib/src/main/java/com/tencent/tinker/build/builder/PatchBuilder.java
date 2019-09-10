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

package com.tencent.tinker.build.builder;


import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.TypedValue;
import com.tencent.tinker.commons.util.IOHelper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;

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
        this.unSignedApk = new File(config.mOutFolder, PATCH_NAME + "_unsigned.apk");
        this.signedApk = new File(config.mOutFolder, PATCH_NAME + "_signed.apk");
        this.signedWith7ZipApk = new File(config.mOutFolder, PATCH_NAME + "_signed_7zip.apk");
        this.sevenZipOutPutDir = new File(config.mOutFolder, TypedValue.OUT_7ZIP_FILE_PATH);
    }

    public void buildPatch() throws Exception {
        final File resultDir = config.mTempResultDir;
        if (!resultDir.exists()) {
            throw new IOException(String.format(
                "Missing patch unzip files, path=%s\n", resultDir.getAbsolutePath()));
        }
        //no file change
        if (resultDir.listFiles().length == 0) {
            return;
        }
        generateUnsignedApk(unSignedApk);
        signApk(unSignedApk, signedApk);

        use7zApk(signedApk, signedWith7ZipApk, sevenZipOutPutDir);

        if (!signedApk.exists()) {
            Logger.e("Result: final unsigned patch result: %s, size=%d", unSignedApk.getAbsolutePath(), unSignedApk.length());
        } else {
            long length = signedApk.length();
            Logger.e("Result: final signed patch result: %s, size=%d", signedApk.getAbsolutePath(), length);
            if (signedWith7ZipApk.exists()) {
                long length7zip = signedWith7ZipApk.length();
                Logger.e("Result: final signed with 7zip patch result: %s, size=%d", signedWith7ZipApk.getAbsolutePath(), length7zip);
                if (length7zip > length) {
                    Logger.e("Warning: %s is bigger than %s %d byte, you should choose %s at these time!",
                        signedWith7ZipApk.getName(),
                        signedApk.getName(),
                        (length7zip - length),
                        signedApk.getName());
                }
            }
        }

    }

    private String getSignatureAlgorithm() throws Exception {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(config.mSignatureFile));
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(is, config.mStorePass.toCharArray());
            Key key = keyStore.getKey(config.mStoreAlias, config.mKeyPass.toCharArray());
            String keyAlgorithm = key.getAlgorithm();
            String signatureAlgorithm;
            if (keyAlgorithm.equalsIgnoreCase("DSA")) {
                signatureAlgorithm = "SHA1withDSA";
            } else if (keyAlgorithm.equalsIgnoreCase("RSA")) {
                signatureAlgorithm = "SHA1withRSA";
            } else if (keyAlgorithm.equalsIgnoreCase("EC")) {
                signatureAlgorithm = "SHA1withECDSA";
            } else {
                throw new RuntimeException("private key is not a DSA or "
                        + "RSA key");
            }
            return signatureAlgorithm;
        } finally {
            IOHelper.closeQuietly(is);
        }
    }

    /**
     * @param input  unsigned file input
     * @param output signed file output
     * @throws IOException
     * @throws InterruptedException
     */
    private void signApk(File input, File output) throws Exception {
        //sign apk
        if (config.mUseSignAPk) {
            Logger.d("Signing apk: %s", output.getName());
            String signatureAlgorithm = getSignatureAlgorithm();
            Logger.d("Signing key algorithm is %s", signatureAlgorithm);

            if (output.exists()) {
                output.delete();
            }
            ArrayList<String> command = new ArrayList<>();
            command.add("jarsigner");
            // issue https://github.com/Tencent/tinker/issues/118
            command.add("-sigalg");
            command.add(signatureAlgorithm);
            command.add("-digestalg");
            command.add("SHA1");
            command.add("-keystore");
            command.add(config.mSignatureFile.getAbsolutePath());
            command.add("-storepass");
            command.add(config.mStorePass);
            command.add("-keypass");
            command.add(config.mKeyPass);
            command.add("-signedjar");
            command.add(output.getAbsolutePath());
            command.add(input.getAbsolutePath());
            command.add(config.mStoreAlias);

            Process process = new ProcessBuilder(command).start();
            process.waitFor();
            process.destroy();
            if (!output.exists()) {
                throw new IOException("Can't Generate signed APK. Please check if your sign info is correct.");
            }
        }
    }

    /**
     * @param output unsigned apk file output
     * @throws IOException
     */
    private void generateUnsignedApk(File output) throws IOException {
        Logger.d("Generate unsigned apk: %s", output.getName());
        final File tempOutDir = config.mTempResultDir;
        if (!tempOutDir.exists()) {
            throw new IOException(String.format(
                "Missing patch unzip files, path=%s\n", tempOutDir.getAbsolutePath()));
        }
        FileOperation.zipInputDir(tempOutDir, output, null);

        if (!output.exists()) {
            throw new IOException(String.format(
                "can not found the unsigned apk file path=%s",
                output.getAbsolutePath()));
        }
    }

    private void use7zApk(File inputSignedFile, File out7zipFile, File tempFilesDir) throws IOException {
        if (!config.mUseSignAPk) {
            return;
        }
        if (!inputSignedFile.exists()) {
            throw new IOException(
                String.format("can not found the signed apk file to 7z, if you want to use 7z, "
                    + "you must fill the sign data in the config file path=%s", inputSignedFile.getAbsolutePath())
            );
        }
        Logger.d("Try use 7za to compress the patch file: %s, will cost much more time", out7zipFile.getName());
        Logger.d("Current 7za path:%s", config.mSevenZipPath);

        FileOperation.unZipAPk(inputSignedFile.getAbsolutePath(), tempFilesDir.getAbsolutePath());
        //7zip may not enable
        if (!FileOperation.sevenZipInputDir(tempFilesDir, out7zipFile, config)) {
            return;
        }
        FileOperation.deleteDir(tempFilesDir);
        if (!out7zipFile.exists()) {
            throw new IOException(String.format(
                "[use7zApk]7z repackage signed apk fail,you must install 7z command line version first, linux: p7zip, window: 7za, path=%s",
                out7zipFile.getAbsolutePath()));
        }
    }
}
