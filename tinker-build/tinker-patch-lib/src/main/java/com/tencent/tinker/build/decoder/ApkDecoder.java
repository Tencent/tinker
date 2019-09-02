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

package com.tencent.tinker.build.decoder;


import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.MD5;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;
import com.tencent.tinker.build.util.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

/**
 * Created by zhangshaowen on 16/3/15.
 */
public class ApkDecoder extends BaseDecoder {
    private final File mOldApkDir;
    private final File mNewApkDir;

    private final ManifestDecoder      manifestDecoder;
    private final UniqueDexDiffDecoder dexPatchDecoder;
    private final BsDiffDecoder        soPatchDecoder;
    private final ResDiffDecoder       resPatchDecoder;
    private final ArkHotDecoder arkHotDecoder;

    /**
     * if resource's file is also contain in dex or library pattern,
     * they won't change in new resources' apk, and we will just warn you.
     */
    ArrayList<File> resDuplicateFiles;

    public ApkDecoder(Configuration config) throws IOException {
        super(config);
        this.mNewApkDir = config.mTempUnzipNewDir;
        this.mOldApkDir = config.mTempUnzipOldDir;

        this.manifestDecoder = new ManifestDecoder(config);

        //put meta files in assets
        String prePath = TypedValue.FILE_ASSETS + File.separator;
        dexPatchDecoder = new UniqueDexDiffDecoder(config, prePath + TypedValue.DEX_META_FILE, TypedValue.DEX_LOG_FILE);
        soPatchDecoder = new BsDiffDecoder(config, prePath + TypedValue.SO_META_FILE, TypedValue.SO_LOG_FILE);
        resPatchDecoder = new ResDiffDecoder(config, prePath + TypedValue.RES_META_TXT, TypedValue.RES_LOG_FILE);
        arkHotDecoder = new ArkHotDecoder(config, prePath + TypedValue.ARKHOT_META_TXT);
        Logger.d("config: " + config.mArkHotPatchPath + " " + config.mArkHotPatchName + prePath + TypedValue.ARKHOT_META_TXT);
        resDuplicateFiles = new ArrayList<>();
    }

    private void unzipApkFile(File file, File destFile) throws TinkerPatchException, IOException {
        String apkName = file.getName();
        if (!apkName.endsWith(TypedValue.FILE_APK)) {
            throw new TinkerPatchException(
                String.format("input apk file path must end with .apk, yours %s\n", apkName)
            );
        }

        String destPath = destFile.getAbsolutePath();
        Logger.d("UnZipping apk to %s", destPath);
        FileOperation.unZipAPk(file.getAbsoluteFile().getAbsolutePath(), destPath);

    }

    private void unzipApkFiles(File oldFile, File newFile) throws IOException, TinkerPatchException {
        unzipApkFile(oldFile, this.mOldApkDir);
        unzipApkFile(newFile, this.mNewApkDir);
    }

    private void writeToLogFile(File oldFile, File newFile) throws IOException {
        String line1 = "old apk1131: " + oldFile.getName() + ", size=" + FileOperation.getFileSizes(oldFile) + ", md5=" + MD5.getMD5(oldFile);
        String line2 = "new apk: " + newFile.getName() + ", size=" + FileOperation.getFileSizes(newFile) + ", md5=" + MD5.getMD5(newFile);
        Logger.d("Analyze old and new apk files1:");
        Logger.d(line1);
        Logger.d(line2);
        Logger.d("");
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {
        manifestDecoder.onAllPatchesStart();
        dexPatchDecoder.onAllPatchesStart();
        soPatchDecoder.onAllPatchesStart();
        resPatchDecoder.onAllPatchesStart();
    }

    public boolean patch(File oldFile, File newFile) throws Exception {
        writeToLogFile(oldFile, newFile);
        //check manifest change first
        manifestDecoder.patch(oldFile, newFile);

        unzipApkFiles(oldFile, newFile);

        Files.walkFileTree(mNewApkDir.toPath(), new ApkFilesVisitor(config, mNewApkDir.toPath(), mOldApkDir.toPath(), dexPatchDecoder, soPatchDecoder, resPatchDecoder));

        // get all duplicate resource file
        for (File duplicateRes : resDuplicateFiles) {
            // resPatchDecoder.patch(duplicateRes, null);
            Logger.e("Warning: res file %s is also match at dex or library pattern, "
                + "we treat it as unchanged in the new resource_out.zip", getRelativePathStringToOldFile(duplicateRes));
        }

        soPatchDecoder.onAllPatchesEnd();
        dexPatchDecoder.onAllPatchesEnd();
        manifestDecoder.onAllPatchesEnd();
        resPatchDecoder.onAllPatchesEnd();
        arkHotDecoder.onAllPatchesEnd();

        //clean resources
        dexPatchDecoder.clean();
        soPatchDecoder.clean();
        resPatchDecoder.clean();
        arkHotDecoder.clean();

        return true;
    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {
    }

    class ApkFilesVisitor extends SimpleFileVisitor<Path> {
        BaseDecoder     dexDecoder;
        BaseDecoder     soDecoder;
        BaseDecoder     resDecoder;
        Configuration   config;
        Path            newApkPath;
        Path            oldApkPath;

        ApkFilesVisitor(Configuration config, Path newPath, Path oldPath, BaseDecoder dex, BaseDecoder so, BaseDecoder resDecoder) {
            this.config = config;
            this.dexDecoder = dex;
            this.soDecoder = so;
            this.resDecoder = resDecoder;
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

            if (Utils.checkFileInPattern(config.mDexFilePattern, patternKey)) {
                //also treat duplicate file as unchanged
                if (Utils.checkFileInPattern(config.mResFilePattern, patternKey) && oldFile != null) {
                    resDuplicateFiles.add(oldFile);
                }

                try {
                    dexDecoder.patch(oldFile, file.toFile());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return FileVisitResult.CONTINUE;
            }
            if (Utils.checkFileInPattern(config.mSoFilePattern, patternKey)) {
                //also treat duplicate file as unchanged
                if (Utils.checkFileInPattern(config.mResFilePattern, patternKey) && oldFile != null) {
                    resDuplicateFiles.add(oldFile);
                }
                try {
                    soDecoder.patch(oldFile, file.toFile());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return FileVisitResult.CONTINUE;
            }
            if (Utils.checkFileInPattern(config.mResFilePattern, patternKey)) {
                try {
                    resDecoder.patch(oldFile, file.toFile());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return FileVisitResult.CONTINUE;
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
