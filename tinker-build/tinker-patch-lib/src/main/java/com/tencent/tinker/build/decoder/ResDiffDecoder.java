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

import com.tencent.tinker.bsdiff.BSDiff;
import com.tencent.tinker.build.apkparser.AndroidParser;
import com.tencent.tinker.build.info.InfoWriter;
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
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by zhangshaowen on 16/8/8.
 */
public class ResDiffDecoder extends BaseDecoder {

    private static final String TEMP_RES_ZIP  = "temp_res.zip";
    private static final String TEMP_RES_7ZIP = "temp_res_7ZIP.zip";
    private final InfoWriter                     logWriter;
    private final InfoWriter                     metaWriter;
    private       ArrayList<String>              addedSet;
    private       ArrayList<String>              modifiedSet;
    private       ArrayList<String>              largeModifiedSet;
    private       HashMap<String, LargeModeInfo> largeModifiedMap;
    private ArrayList<String> deletedSet;

    private boolean arscChanged;
    private File oldArscFile;
    private File newArscFile;


    public ResDiffDecoder(Configuration config, String metaPath, String logPath) throws IOException {
        super(config);

        if (metaPath != null) {
            metaWriter = new InfoWriter(config, config.mTempResultDir + File.separator + metaPath);
        } else {
            metaWriter = null;
        }

        if (logPath != null) {
            logWriter = new InfoWriter(config, config.mOutFolder + File.separator + logPath);
        } else {
            logWriter = null;
        }
        addedSet = new ArrayList<>();
        modifiedSet = new ArrayList<>();
        largeModifiedSet = new ArrayList<>();
        largeModifiedMap = new HashMap<>();
        deletedSet = new ArrayList<>();
    }

    @Override
    public void clean() {
        metaWriter.close();
        logWriter.close();
    }

    private boolean checkLargeModFile(File file) {
        long length = file.length();
        if (length > config.mLargeModSize * TypedValue.K_BYTES) {
            return true;
        }
        return false;
    }

    @Override
    public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException {
        String name = getRelativeString(newFile);
        if (name.equals(TypedValue.RES_ARSC)) {
            oldArscFile = oldFile;
            newArscFile = newFile;
        }
        //actually, it won't go below
        if (newFile == null || !newFile.exists()) {
            String relativeStringByOldDir = getRelativeStringByOldDir(oldFile);
            if (Utils.checkFileInPattern(config.mResIgnoreChangePattern, relativeStringByOldDir)) {
                Logger.e("found delete resource: " + relativeStringByOldDir + " ,but it match ignore change pattern, just ignore!");
                return false;
            }
            deletedSet.add(relativeStringByOldDir);
            writeResLog(newFile, oldFile, TypedValue.DEL);
            return true;
        }

        File outputFile = getOutputPath(newFile).toFile();

        if (oldFile == null || !oldFile.exists()) {
            if (Utils.checkFileInPattern(config.mResIgnoreChangePattern, name)) {
                Logger.e("found add resource: " + name + " ,but it match ignore change pattern, just ignore!");
                return false;
            }
            FileOperation.copyFileUsingStream(newFile, outputFile);
            addedSet.add(name);
            writeResLog(newFile, oldFile, TypedValue.ADD);
            return true;
        }
        //both file length is 0
        if (oldFile.length() == 0 && newFile.length() == 0) {
            return false;
        }
        //new add file
        String newMd5 = MD5.getMD5(newFile);
        String oldMd5 = MD5.getMD5(oldFile);

        //oldFile or newFile may be 0b length
        if (oldMd5 != null && oldMd5.equals(newMd5)) {
            return false;
        }
        if (Utils.checkFileInPattern(config.mResIgnoreChangePattern, name)) {
            Logger.d("found modify resource: " + name + ", but it match ignore change pattern, just ignore!");
            return false;
        }
        if (name.equals(TypedValue.RES_MANIFEST)) {
            Logger.d("found modify resource: " + name + ", but it is AndroidManifest.xml, just ignore!");
            return false;
        }
        if (name.equals(TypedValue.RES_ARSC)) {
            if (AndroidParser.resourceTableLogicalChange(config)) {
                Logger.d("found modify resource: " + name + ", but it is logically the same as original new resources.arsc, just ignore!");
                return false;
            }
            //deal with resources.arsc later
            arscChanged = true;
            return true;
        }
        dealWithModeFile(name, newMd5, oldFile, newFile, outputFile);
        return true;
    }

    private boolean dealWithModeFile(String name, String newMd5, File oldFile, File newFile, File outputFile) throws IOException {
        if (checkLargeModFile(newFile)) {
            if (!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }
            BSDiff.bsdiff(oldFile, newFile, outputFile);
            //treat it as normal modify
            if (Utils.checkBsDiffFileSize(outputFile, newFile)) {
                LargeModeInfo largeModeInfo = new LargeModeInfo();
                largeModeInfo.path = newFile;
                largeModeInfo.crc = FileOperation.getFileCrc32(newFile);
                largeModeInfo.md5 = newMd5;
                largeModifiedSet.add(name);
                largeModifiedMap.put(name, largeModeInfo);
                writeResLog(newFile, oldFile, TypedValue.LARGE_MOD);
                return true;
            }
        }
        modifiedSet.add(name);
        FileOperation.copyFileUsingStream(newFile, outputFile);
        writeResLog(newFile, oldFile, TypedValue.MOD);
        return false;
    }

    private void writeResLog(File newFile, File oldFile, int mode) throws IOException {
        if (logWriter != null) {
            String log = "";
            String relative;
            switch (mode) {
                case TypedValue.ADD:
                    relative = getRelativeString(newFile);
                    Logger.d("Found add resource: " + relative);
                    log = "add resource: " + relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                        + FileOperation.getFileSizes(newFile);
                    break;
                case TypedValue.MOD:
                    relative = getRelativeString(newFile);
                    Logger.d("Found modify resource: " + relative);
                    log = "modify resource: " + relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                        + FileOperation.getFileSizes(newFile);
                    break;
                case TypedValue.DEL:
                    relative = getRelativeStringByOldDir(oldFile);
                    Logger.d("Found deleted resource: " + relative);
                    log = "deleted resource: " + relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                        + FileOperation.getFileSizes(newFile);
                    break;
                case TypedValue.LARGE_MOD:
                    relative = getRelativeString(newFile);
                    Logger.d("Found large modify resource: " + relative + " size:" + newFile.length());
                    log = "large modify resource: " + relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                        + FileOperation.getFileSizes(newFile);
                    break;
            }
            logWriter.writeLineToInfoFile(log);
        }
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {

    }

    private void modArscFileForTestResource() throws IOException {
        File tempArscFile = new File(config.mOutFolder + File.separator + "edited_resources.arsc");
        //there is resource changed, edit test resource string
        AndroidParser.editResourceTableString(TypedValue.TEST_STRING_VALUE_A, TypedValue.TEST_STRING_VALUE_B, newArscFile, tempArscFile);
        dealWithModeFile(TypedValue.RES_ARSC, MD5.getMD5(tempArscFile), oldArscFile, tempArscFile, getOutputPath(newArscFile).toFile());
        Logger.d("Edit resources.arsc file for test resource change, final path: " + tempArscFile.getAbsolutePath());
    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {
        //only there is only deleted set, we just ignore
        if (addedSet.isEmpty() && modifiedSet.isEmpty() && largeModifiedSet.isEmpty() && !arscChanged) {
            return;
        }

        if (!config.mResRawPattern.contains(TypedValue.RES_ARSC)) {
            throw new TinkerPatchException("resource must contain resources.arsc pattern");
        }
        if (!config.mResRawPattern.contains(TypedValue.RES_MANIFEST)) {
            throw new TinkerPatchException("resource must contain AndroidManifest.xml pattern");
        }

        modArscFileForTestResource();

        //check gradle build
        if (config.mUsingGradle) {
            final boolean ignoreWarning = config.mIgnoreWarning;
            if (arscChanged && !config.mUseApplyResource) {
                if (ignoreWarning) {
                    //ignoreWarning, just log
                    Logger.e("Warning:ignoreWarning is true, but resources.arsc is changed, you should use applyResourceMapping mode to build the new apk, otherwise, it may be crash at some times");
                } else {
                    Logger.e("Warning:ignoreWarning is false, but resources.arsc is changed, you should use applyResourceMapping mode to build the new apk, otherwise, it may be crash at some times");

                    throw new TinkerPatchException(
                        String.format("ignoreWarning is false, but resources.arsc is changed, you should use applyResourceMapping mode to build the new apk, otherwise, it may be crash at some times")
                    );
                }
            } /*else if (config.mUseApplyResource) {
                int totalChangeSize = addedSet.size() + modifiedSet.size() + largeModifiedSet.size();
                if (totalChangeSize == 1 && resourceArscChanged) {
                    Logger.e("Warning: we are using applyResourceMapping mode to build the new apk, but there is only resources.arsc changed, you should ensure there is actually resource changed!");
                }
            }*/
        }
        //add delete set
        deletedSet.addAll(getDeletedResource(config.mTempUnzipOldDir, config.mTempUnzipNewDir));

        //we can't modify AndroidManifest file
        addedSet.remove(TypedValue.RES_MANIFEST);
        deletedSet.remove(TypedValue.RES_MANIFEST);
        modifiedSet.remove(TypedValue.RES_MANIFEST);
        largeModifiedSet.remove(TypedValue.RES_MANIFEST);
        //remove add, delete or modified if they are in ignore change pattern also
        removeIgnoreChangeFile(modifiedSet);
        removeIgnoreChangeFile(deletedSet);
        removeIgnoreChangeFile(addedSet);
        removeIgnoreChangeFile(largeModifiedSet);

        File tempResZip = new File(config.mOutFolder + File.separator + TEMP_RES_ZIP);
        final File tempResFiles = config.mTempResultDir;

        //gen zip resources_out.zip
        FileOperation.zipInputDir(tempResFiles, tempResZip);
        File extractToZip = new File(config.mOutFolder + File.separator + TypedValue.RES_OUT);

        String resZipMd5 = Utils.genResOutputFile(extractToZip, tempResZip, config,
            addedSet, modifiedSet, deletedSet, largeModifiedSet, largeModifiedMap);

        Logger.e("Final normal zip resource: %s, size=%d, md5=%s", extractToZip.getName(), extractToZip.length(), resZipMd5);
        logWriter.writeLineToInfoFile(
            String.format("Final normal zip resource: %s, size=%d, md5=%s", extractToZip.getName(), extractToZip.length(), resZipMd5)
        );
        //delete temp file
        FileOperation.deleteFile(tempResZip);

        //gen zip resources_out_7z.zip
        File extractTo7Zip = new File(config.mOutFolder + File.separator + TypedValue.RES_OUT_7ZIP);
        File tempRes7Zip = new File(config.mOutFolder + File.separator + TEMP_RES_7ZIP);

        //ensure 7zip is enable
        if (FileOperation.sevenZipInputDir(tempResFiles, tempRes7Zip, config)) {
            //7zip whether actual exist
            if (tempRes7Zip.exists()) {

                String res7zipMd5 = Utils.genResOutputFile(extractTo7Zip, tempRes7Zip, config,
                    addedSet, modifiedSet, deletedSet, largeModifiedSet, largeModifiedMap);
                //delete temp file
                FileOperation.deleteFile(tempRes7Zip);
                Logger.e("Final 7zip resource: %s, size=%d, md5=%s", extractTo7Zip.getName(), extractTo7Zip.length(), res7zipMd5);
                logWriter.writeLineToInfoFile(
                    String.format("Final 7zip resource: %s, size=%d, md5=%s", extractTo7Zip.getName(), extractTo7Zip.length(), res7zipMd5)
                );
            }
        }
        //first, write resource meta first
        //use resources.arsc's base crc to identify base.apk
        String arscBaseCrc = FileOperation.getZipEntryCrc(config.mOldApkFile, TypedValue.RES_ARSC);
        String arscMd5 = FileOperation.getZipEntryMd5(extractToZip, TypedValue.RES_ARSC);
        if (arscBaseCrc == null || arscMd5 == null) {
            throw new TinkerPatchException("can't find resources.arsc's base crc or md5");
        }

        String resourceMeta = Utils.getResourceMeta(arscBaseCrc, arscMd5);
        writeMetaFile(resourceMeta);

        //pattern
        String patternMeta = TypedValue.PATTERN_TITLE;
        HashSet<String> patterns = new HashSet<>(config.mResRawPattern);
        //we will process them separate
        patterns.remove(TypedValue.RES_MANIFEST);

        writeMetaFile(patternMeta + patterns.size());
        //write pattern
        for (String item : patterns) {
            writeMetaFile(item);
        }
        //write meta file, write large modify first
        writeMetaFile(largeModifiedSet, TypedValue.LARGE_MOD);
        writeMetaFile(modifiedSet, TypedValue.MOD);
        writeMetaFile(addedSet, TypedValue.ADD);
        writeMetaFile(deletedSet, TypedValue.DEL);
    }

    private void removeIgnoreChangeFile(ArrayList<String> array) {
        ArrayList<String> removeList = new ArrayList<>();
        for (String name : array) {
            if (Utils.checkFileInPattern(config.mResIgnoreChangePattern, name)) {
                Logger.e("ignore change resource file: " + name);
                removeList.add(name);
            }
        }
        array.removeAll(removeList);
    }

    private void writeMetaFile(String line) {
        metaWriter.writeLineToInfoFile(line);
    }

    private void writeMetaFile(ArrayList<String> set, int mode) {
        if (!set.isEmpty()) {
            String title = "";
            switch (mode) {
                case TypedValue.ADD:
                    title = TypedValue.ADD_TITLE + set.size();
                    break;
                case TypedValue.MOD:
                    title = TypedValue.MOD_TITLE + set.size();
                    break;
                case TypedValue.LARGE_MOD:
                    title = TypedValue.LARGE_MOD_TITLE + set.size();
                    break;
                case TypedValue.DEL:
                    title = TypedValue.DEL_TITLE + set.size();
                    break;
            }
            metaWriter.writeLineToInfoFile(title);
            for (String name : set) {
                String line = name;
                if (mode == TypedValue.LARGE_MOD) {
                    LargeModeInfo info = largeModifiedMap.get(name);
                    line = name + "," + info.md5 + "," + info.crc;
                }
                metaWriter.writeLineToInfoFile(line);
            }
        }
    }

    public ArrayList<String> getDeletedResource(File oldApkDir, File newApkDir) throws IOException {
        //get deleted resource
        DeletedResVisitor deletedResVisitor = new DeletedResVisitor(config, newApkDir.toPath(), oldApkDir.toPath());
        Files.walkFileTree(oldApkDir.toPath(), deletedResVisitor);
        return deletedResVisitor.deletedFiles;
    }

    public class LargeModeInfo {
        public File path = null;
        public long crc;
        public String md5 = null;
    }

    class DeletedResVisitor extends SimpleFileVisitor<Path> {
        Configuration     config;
        Path              newApkPath;
        Path              oldApkPath;
        ArrayList<String> deletedFiles;

        DeletedResVisitor(Configuration config, Path newPath, Path oldPath) {
            this.config = config;
            this.newApkPath = newPath;
            this.oldApkPath = oldPath;
            this.deletedFiles = new ArrayList<>();
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

            Path relativePath = oldApkPath.relativize(file);

            Path newPath = newApkPath.resolve(relativePath);

            String patternKey = relativePath.toString().replace("\\", "/");

            if (Utils.checkFileInPattern(config.mResFilePattern, patternKey)) {
                //not contain in new path, is deleted
                if (!newPath.toFile().exists()) {
                    deletedFiles.add(relativePath.toString());
                    writeResLog(newPath.toFile(), file.toFile(), TypedValue.DEL);
                }
                return FileVisitResult.CONTINUE;
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
