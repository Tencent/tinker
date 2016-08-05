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

package com.tencent.tinker.build.decoder;


import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.MD5;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Created by zhangshaowen on 16/3/15.
 */
public class ApkDecoder extends BaseDecoder {
    private final File mOldApkDir;
    private final File mNewApkDir;

    private final ManifestDecoder      manifestDecoder;
    private final UniqueDexDiffDecoder dexPatchDecoder;
    private final BsDiffDecoder        soPatchDecoder;

    public ApkDecoder(Configuration config) throws IOException {
        super(config);
        this.mNewApkDir = config.mTempUnzipNewDir;
        this.mOldApkDir = config.mTempUnzipOldDir;

        this.manifestDecoder = new ManifestDecoder(config);

        //put meta files in assets
        String prePath = "assets" + File.separator;
        dexPatchDecoder = new UniqueDexDiffDecoder(config, prePath + TypedValue.DEX_META_FILE, TypedValue.DEX_LOG_FILE);
        soPatchDecoder = new BsDiffDecoder(config, prePath + TypedValue.SO_META_FILE, TypedValue.SO_LOG_FILE);
    }

    private void unzipApkFile(File file, File destFile) throws TinkerPatchException, IOException {
        String apkName = file.getName();
        if (!apkName.endsWith(TypedValue.FILE_APK)) {
            throw new TinkerPatchException(
                String.format("input apk file path must end with .apk, yours %s\n", apkName)
            );
        }

        String destPath = destFile.getAbsolutePath();
        Logger.d("unziping apk to %s", destPath);
        FileOperation.unZipAPk(file.getAbsoluteFile().getAbsolutePath(), destPath);

    }

    private void unzipApkFiles(File oldFile, File newFile) throws IOException, TinkerPatchException {
        unzipApkFile(oldFile, this.mOldApkDir);
        unzipApkFile(newFile, this.mNewApkDir);
    }

    private void writeToLogFile(File oldFile, File newFile) throws IOException {
        String line1 = oldFile.getName() + ", size=" + FileOperation.getFileSizes(oldFile) + ", md5=" + MD5.getMD5(oldFile);
        String line2 = newFile.getName() + ", size=" + FileOperation.getFileSizes(newFile) + ", md5=" + MD5.getMD5(newFile);
        Logger.d("analyze old and new apk files:");
        Logger.d(line1);
        Logger.d(line2);
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {
        manifestDecoder.onAllPatchesStart();
        dexPatchDecoder.onAllPatchesStart();
        soPatchDecoder.onAllPatchesStart();
    }

    public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException {
        //check manifest change fisrt
        manifestDecoder.patch(oldFile, newFile);

        unzipApkFiles(oldFile, newFile);

        Files.walkFileTree(mNewApkDir.toPath(), new ApkFilesVisitor(config, mNewApkDir.toPath(), mOldApkDir.toPath(), dexPatchDecoder, soPatchDecoder));

        writeToLogFile(oldFile, newFile);

        //clean resources
        dexPatchDecoder.clean();
        soPatchDecoder.clean();
        return true;
    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {
        soPatchDecoder.onAllPatchesEnd();
        dexPatchDecoder.onAllPatchesEnd();
        manifestDecoder.onAllPatchesEnd();
    }

    class ApkFilesVisitor extends SimpleFileVisitor<Path> {
        BaseDecoder   dexDecoder;
        BaseDecoder   soDecoder;
        Configuration config;
        Path          newApkPath;
        Path          oldApkPath;

        ApkFilesVisitor(Configuration config, Path newPath, Path oldPath, BaseDecoder dex, BaseDecoder so) {
            // TODO Auto-generated constructor stub
            this.config = config;
            this.dexDecoder = dex;
            this.soDecoder = so;
            this.newApkPath = newPath;
            this.oldApkPath = oldPath;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

            Path relativePath = newApkPath.relativize(file);

            Path oldPath = oldApkPath.resolve(relativePath);

            File oldFile = null;
            //is a new file?!
            if (oldPath.toFile().exists()) {
                oldFile = oldPath.toFile();
            }
            String patternKey = relativePath.toString().replace("\\", "/");

            if (checkFileInPattern(config.mDexFilePattern, patternKey)) {
                try {
                    dexDecoder.patch(oldFile, file.toFile());
                } catch (TinkerPatchException e) {
//                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                return FileVisitResult.CONTINUE;
            }
            if (checkFileInPattern(config.mSoFilePattern, patternKey)) {
                try {
                    soDecoder.patch(oldFile, file.toFile());
                } catch (TinkerPatchException e) {
//                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                return FileVisitResult.CONTINUE;
            }
            return FileVisitResult.CONTINUE;
        }


        private boolean checkFileInPattern(HashSet<Pattern> patterns, String key) {
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
    }
}
