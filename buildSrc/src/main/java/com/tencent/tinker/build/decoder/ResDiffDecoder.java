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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import tinker.net.dongliu.apk.parser.ApkParser;
import tinker.net.dongliu.apk.parser.struct.ResourceValue;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceEntry;
import tinker.net.dongliu.apk.parser.struct.resource.ResourcePackage;
import tinker.net.dongliu.apk.parser.struct.resource.Type;

/**
 * Created by zhangshaowen on 16/8/8.
 */
public class ResDiffDecoder extends BaseDecoder {
    private static final String TEST_RESOURCE_NAME        = "only_use_to_test_tinker_resource.txt";
    private static final String TEST_RESOURCE_ASSETS_PATH = "assets/" + TEST_RESOURCE_NAME;

    private static final String TEMP_RES_ZIP = "temp_res.zip";
    private final InfoWriter        logWriter;
    private final InfoWriter        metaWriter;
    private       ArrayList<String> addedSet;
    private       ArrayList<String> modifiedSet;
    private       ArrayList<String> storedSet;

    private ArrayList<String>              largeModifiedSet;
    private HashMap<String, LargeModeInfo> largeModifiedMap;
    private ArrayList<String>              deletedSet;

    private ApkParser                      newApkParser;
    private Set<String>                    newApkAnimResNames;

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
        storedSet = new ArrayList<>();

        newApkParser = new ApkParser(config.mNewApkFile);
        newApkAnimResNames = new HashSet<>();
    }

    @Override
    public void clean() {
        metaWriter.close();
        logWriter.close();
        try {
            newApkParser.close();
        } catch (Throwable ignored) {
            // Ignored.
        }
    }

    /**
     * last modify or store files
     *
     * @param file
     * @return
     */
    private boolean checkLargeModFile(File file) {
        long length = file.length();
        if (length > config.mLargeModSize * TypedValue.K_BYTES) {
            return true;
        }
        return false;
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {
        newApkParser.parseResourceTable();
        final Map<String, ResourcePackage> newApkResPkgNameMap = newApkParser.getResourceTable().getPackageNameMap();
        do {
            if (newApkResPkgNameMap == null) {
                break;
            }

            final ResourcePackage newApkResPackage = newApkResPkgNameMap.get(newApkParser.getApkMeta().getPackageName());
            if (newApkResPackage == null) {
                break;
            }

            final Map<String, List<Type>> newApkResTypesNameMap = newApkResPackage.getTypesNameMap();
            if (newApkResTypesNameMap == null) {
                break;
            }

            final List<Type> newApkAnimResTypes = newApkResTypesNameMap.get("anim");
            if (newApkAnimResTypes == null) {
                break;
            }

            for (Type animType : newApkAnimResTypes) {
                for (ResourceEntry value : animType.getResourceEntryNameHashMap().values()) {
                    if (value == null) {
                        continue;
                    }
                    final ResourceValue resValue = value.getValue();
                    if (resValue == null) {
                        continue;
                    }
                    newApkAnimResNames.add(resValue.toStringValue());
                }
            }
        } while (false);
    }

    @Override
    public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException {
        String name = getRelativePathStringToNewFile(newFile);

        //actually, it won't go below
        if (newFile == null || !newFile.exists()) {
            String relativeStringByOldDir = getRelativePathStringToOldFile(oldFile);
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
        }
        dealWithModifyFile(name, newMd5, oldFile, newFile, outputFile);
        return true;
    }

    private boolean dealWithModifyFile(String name, String newMd5, File oldFile, File newFile, File outputFile) throws IOException {
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
                    relative = getRelativePathStringToNewFile(newFile);
                    Logger.d("Found add resource: " + relative);
                    log = "add resource: " + relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                        + FileOperation.getFileSizes(newFile);
                    break;
                case TypedValue.MOD:
                    relative = getRelativePathStringToNewFile(newFile);
                    Logger.d("Found modify resource: " + relative);
                    log = "modify resource: " + relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                        + FileOperation.getFileSizes(newFile);
                    break;
                case TypedValue.DEL:
                    relative = getRelativePathStringToOldFile(oldFile);
                    Logger.d("Found deleted resource: " + relative);
                    log = "deleted resource: " + relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                        + FileOperation.getFileSizes(newFile);
                    break;
                case TypedValue.LARGE_MOD:
                    relative = getRelativePathStringToNewFile(newFile);
                    Logger.d("Found large modify resource: " + relative + " size:" + newFile.length());
                    log = "large modify resource: " + relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                        + FileOperation.getFileSizes(newFile);
                    break;
                default:
                    break;
            }
            logWriter.writeLineToInfoFile(log);
        }
    }

    private void addAssetsFileForTestResource() throws IOException {
        File dest = new File(config.mTempResultDir + "/" + TEST_RESOURCE_ASSETS_PATH);
        FileOperation.copyResourceUsingStream(TEST_RESOURCE_NAME, dest);
        addedSet.add(TEST_RESOURCE_ASSETS_PATH);
        Logger.d("Add Test resource file: " + TEST_RESOURCE_ASSETS_PATH);
        String log = "add test resource: " + TEST_RESOURCE_ASSETS_PATH + ", oldSize=" + 0 + ", newSize="
            + FileOperation.getFileSizes(dest);
        logWriter.writeLineToInfoFile(log);
    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {
        //only there is only deleted set, we just ignore
        if (addedSet.isEmpty() && modifiedSet.isEmpty() && largeModifiedSet.isEmpty()) {
            return;
        }

        if (!config.mResRawPattern.contains(TypedValue.RES_ARSC)) {
            throw new TinkerPatchException("resource must contain resources.arsc pattern");
        }
        if (!config.mResRawPattern.contains(TypedValue.RES_MANIFEST)) {
            throw new TinkerPatchException("resource must contain AndroidManifest.xml pattern");
        }

        //check gradle build
        if (config.mUsingGradle) {
            final boolean ignoreWarning = config.mIgnoreWarning;
            final boolean resourceArscChanged = modifiedSet.contains(TypedValue.RES_ARSC)
                || largeModifiedSet.contains(TypedValue.RES_ARSC);
            if (resourceArscChanged && !config.mUseApplyResource) {
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

        // after ignore-changes resource files are being removed, we now check if there's any anim
        // resources in added and modified files.
        checkIfSpecificResWasAnimRes(addedSet);
        checkIfSpecificResWasAnimRes(modifiedSet);
        checkIfSpecificResWasAnimRes(largeModifiedSet);

        // last add test res in assets for user cannot ignore it;
        addAssetsFileForTestResource();

        File tempResZip = new File(config.mOutFolder + File.separator + TEMP_RES_ZIP);
        final File tempResFiles = config.mTempResultDir;

        //gen zip resources_out.zip
        FileOperation.zipInputDir(tempResFiles, tempResZip, null);
        File extractToZip = new File(config.mOutFolder + File.separator + TypedValue.RES_OUT);

        String resZipMd5 = Utils.genResOutputFile(extractToZip, tempResZip, config,
            addedSet, modifiedSet, deletedSet, largeModifiedSet, largeModifiedMap);

        Logger.e("Final normal zip resource: %s, size=%d, md5=%s", extractToZip.getName(), extractToZip.length(), resZipMd5);
        logWriter.writeLineToInfoFile(
            String.format("Final normal zip resource: %s, size=%d, md5=%s", extractToZip.getName(), extractToZip.length(), resZipMd5)
        );
        //delete temp file
        FileOperation.deleteFile(tempResZip);

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

        //add store files
        getCompressMethodFromApk();

        //write meta file, write large modify first
        writeMetaFile(largeModifiedSet, TypedValue.LARGE_MOD);
        writeMetaFile(modifiedSet, TypedValue.MOD);
        writeMetaFile(addedSet, TypedValue.ADD);
        writeMetaFile(deletedSet, TypedValue.DEL);
        writeMetaFile(storedSet, TypedValue.STORED);

    }

    private void checkIfSpecificResWasAnimRes(Collection<String> specificFileNames) {
        final Set<String> changedAnimResNames = new HashSet<>();
        for (String resFileName : specificFileNames) {
            String resName = resFileName;
            int lastPathSepPos = resFileName.lastIndexOf('/');
            if (lastPathSepPos < 0) {
                lastPathSepPos = resFileName.lastIndexOf('\\');
            }
            if (lastPathSepPos >= 0) {
                resName = resName.substring(lastPathSepPos + 1);
            }
            final int firstDotPos = resName.indexOf('.');
            if (firstDotPos >= 0) {
                resName = resName.substring(0, firstDotPos);
            }
            if (newApkAnimResNames.contains(resName)) {
                if (Utils.isStringMatchesPatterns(resFileName, config.mResIgnoreChangeWarningPattern)) {
                    Logger.d("\nAnimation resource: " + resFileName
                            + " was changed, but it's filtered by ignoreChangeWarning pattern, just ignore.\n");
                } else {
                    changedAnimResNames.add(resFileName);
                }
            }
        }
        if (!changedAnimResNames.isEmpty()) {
            if (config.mIgnoreWarning) {
                //ignoreWarning, just log
                Logger.e("Warning:ignoreWarning is true, but we found animation resource is changed. "
                        + "Please check if any one was used in 'overridePendingTransition' which may leads to crash. "
                        + "If all of them were not used in that method, just add them into 'res { ignoreChangeWarning }' option.\n"
                        + "related res: " + changedAnimResNames + "\n");
            } else {
                Logger.e("Warning:ignoreWarning is false, but we found animation resource is changed. "
                        + "Please check if any one was used in 'overridePendingTransition' which may leads to crash. "
                        + "If all of them were not used in that method, just add them into 'res { ignoreChangeWarning }' option.\n"
                        + "related res: " + changedAnimResNames + "\n");
                throw new TinkerPatchException(
                        "ignoreWarning is false, but we found animation resource is changed. "
                        + "Please check if any one was used in 'overridePendingTransition' which may leads to crash. "
                        + "If all of them were not used in that method, just add them into 'res { ignoreChangeWarning }' option.\n"
                        + "related res: " + changedAnimResNames);
            }
        }
    }

    private void getCompressMethodFromApk() {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(config.mNewApkFile);
            ArrayList<String> sets = new ArrayList<>();
            sets.addAll(modifiedSet);
            sets.addAll(addedSet);

            ZipEntry zipEntry;
            for (String name : sets) {
                zipEntry = zipFile.getEntry(name);
                if (zipEntry != null && zipEntry.getMethod() == ZipEntry.STORED) {
                    storedSet.add(name);
                }
            }

        } catch (Throwable throwable) {
            // Ignored.
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    // Ignored.
                }
            }
        }
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
                case TypedValue.STORED:
                    title = TypedValue.STORE_TITLE + set.size();
                    break;
                default:
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
                    deletedFiles.add(patternKey);
                    writeResLog(newPath.toFile(), file.toFile(), TypedValue.DEL);
                }
                return FileVisitResult.CONTINUE;
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
