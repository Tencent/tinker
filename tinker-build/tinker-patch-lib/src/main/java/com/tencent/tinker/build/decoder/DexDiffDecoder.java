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


import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.DexFormat;
import com.tencent.tinker.android.dx.util.Hex;
import com.tencent.tinker.build.dexpatcher.DexPatchGenerator;
import com.tencent.tinker.build.dexpatcher.util.SmallDexPatchGenerator;
import com.tencent.tinker.build.info.InfoWriter;
import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.DexClassesComparator;
import com.tencent.tinker.build.util.DexClassesComparator.DexClassInfo;
import com.tencent.tinker.build.util.DexClassesComparator.DexGroup;
import com.tencent.tinker.build.util.ExcludedClassModifiedChecker;
import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.MD5;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;
import com.tencent.tinker.build.util.Utils;
import com.tencent.tinker.commons.dexpatcher.DexPatchApplier;
import com.tencent.tinker.commons.dexpatcher.DexPatcherLogger.IDexPatcherLogger;
import com.tencent.tinker.commons.dexpatcher.struct.SmallPatchedDexItemFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Created by zhangshaowen on 2016/3/23.
 */
public class DexDiffDecoder extends BaseDecoder {
    private static final String TEST_DEX_PATH = "test.dex";
    private final InfoWriter logWriter;
    private final InfoWriter metaWriter;

    private final ExcludedClassModifiedChecker excludedClassModifiedChecker;

    private final Map<String, String> addedClassDescToDexNameMap;
    private final Map<String, String> deletedClassDescToDexNameMap;

    private final List<AbstractMap.SimpleEntry<File, File>> oldAndNewDexFilePairList;

    private final Map<String, RelatedInfo> dexNameToRelatedInfoMap;
    private boolean hasDexChanged = false;
    private DexPatcherLoggerBridge dexPatcherLoggerBridge = null;

    public DexDiffDecoder(Configuration config, String metaPath, String logPath) throws IOException {
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

        if (logWriter != null) {
            this.dexPatcherLoggerBridge = new DexPatcherLoggerBridge(logWriter);
        }

        excludedClassModifiedChecker = new ExcludedClassModifiedChecker(config);

        addedClassDescToDexNameMap = new HashMap<>();
        deletedClassDescToDexNameMap = new HashMap<>();

        oldAndNewDexFilePairList = new ArrayList<>();

        dexNameToRelatedInfoMap = new HashMap<>();
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {

    }

    @SuppressWarnings("NewApi")
    @Override
    public boolean patch(final File oldFile, final File newFile) throws IOException, TinkerPatchException {
        // first of all, we should check input files if excluded classes were modified.
        Logger.d("Check for loader classes in dex: %s",
            (oldFile == null ? getRelativeString(newFile) : getRelativeString(oldFile))
        );

        try {
            excludedClassModifiedChecker.checkIfExcludedClassWasModifiedInNewDex(oldFile, newFile);
        } catch (IOException e) {
            throw new TinkerPatchException(e);
        } catch (TinkerPatchException e) {
            if (config.mIgnoreWarning) {
                Logger.e("Warning:ignoreWarning is true, but we found %s", e.getMessage());
            } else {
                Logger.e("Warning:ignoreWarning is false, but we found %s", e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // If corresponding new dex was completely deleted, just return false.
        // don't process 0 length dex
        if (newFile == null || !newFile.exists() || newFile.length() == 0) {
            return false;
        }

        File dexDiffOut = getOutputPath(newFile).toFile();

        final String newMd5 = MD5.getMD5(newFile);

        //new add file
        if (oldFile == null || !oldFile.exists() || oldFile.length() == 0) {
            hasDexChanged = true;
            copyNewDexAndMarkInMeta(newFile, newMd5, dexDiffOut);
            return true;
        }

        final String oldMd5 = MD5.getMD5(oldFile);

        if (!oldMd5.equals(newMd5)) {
            hasDexChanged = true;
            checkAddedOrDeletedClasses(oldFile, newFile);
        }

        RelatedInfo relatedInfo = new RelatedInfo();
        relatedInfo.oldMd5 = oldMd5;
        relatedInfo.newMd5 = newMd5;

        // collect current old dex file and corresponding new dex file for further processing.
        oldAndNewDexFilePairList.add(new AbstractMap.SimpleEntry<>(oldFile, newFile));

        final String dexName = oldFile.getName();
        dexNameToRelatedInfoMap.put(dexName, relatedInfo);

        return true;
    }

    @SuppressWarnings("NewApi")
    @Override
    public void onAllPatchesEnd() throws Exception {
        if (!hasDexChanged) {
            Logger.d("No dexes were changed, nothing needs to be done next.");
            return;
        }

        File tempFullPatchDexPath = new File(config.mOutFolder + File.separator + TypedValue.DEX_TEMP_PATCH_DIR + File.separator + "full");
        ensureDirectoryExist(tempFullPatchDexPath);
        File tempSmallPatchDexPath = new File(config.mOutFolder + File.separator + TypedValue.DEX_TEMP_PATCH_DIR + File.separator + "small");
        ensureDirectoryExist(tempSmallPatchDexPath);

        // Generate dex diff out and full patched dex if a pair of dex is different.
        for (AbstractMap.SimpleEntry<File, File> oldAndNewDexFilePair : oldAndNewDexFilePairList) {
            File oldFile = oldAndNewDexFilePair.getKey();
            File newFile = oldAndNewDexFilePair.getValue();
            final String dexName = oldFile.getName();
            RelatedInfo relatedInfo = dexNameToRelatedInfoMap.get(dexName);

            if (!relatedInfo.oldMd5.equals(relatedInfo.newMd5)) {
                File dexDiffOut = getOutputPath(newFile).toFile();
                ensureDirectoryExist(dexDiffOut.getParentFile());

                try {
                    DexPatchGenerator dexPatchGen = new DexPatchGenerator(oldFile, newFile);
                    dexPatchGen.setAdditionalRemovingClassPatterns(config.mDexLoaderPattern);

                    logWriter.writeLineToInfoFile(
                            String.format(
                                    "Start diff between [%s] as old and [%s] as new:",
                                    getRelativeStringBy(oldFile, config.mTempUnzipOldDir),
                                    getRelativeStringBy(newFile, config.mTempUnzipNewDir)
                            )
                    );

                    dexPatchGen.executeAndSaveTo(dexDiffOut);
                } catch (Exception e) {
                    throw new TinkerPatchException(e);
                }

                if (!dexDiffOut.exists()) {
                    throw new TinkerPatchException("can not find the diff file:" + dexDiffOut.getAbsolutePath());
                }

                relatedInfo.dexDiffFile = dexDiffOut;
                relatedInfo.dexDiffMd5 = MD5.getMD5(dexDiffOut);

                File tempFullPatchedDexFile = new File(tempFullPatchDexPath, dexName);

                try {
                    new DexPatchApplier(oldFile, dexDiffOut).executeAndSaveTo(tempFullPatchedDexFile);

                    Logger.d(
                            String.format("Verifying if patched new dex is logically the same as original new dex: %s ...", getRelativeStringBy(newFile, config.mTempUnzipNewDir))
                    );

                    Dex origNewDex = new Dex(newFile);
                    Dex patchedNewDex = new Dex(tempFullPatchedDexFile);
                    checkDexChange(origNewDex, patchedNewDex);

                    relatedInfo.newOrFullPatchedFile = tempFullPatchedDexFile;
                    relatedInfo.newOrFullPatchedMd5 = MD5.getMD5(tempFullPatchedDexFile);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new TinkerPatchException(
                            "Failed to generate temporary patched dex, which makes MD5 generating procedure of new dex failed, either.", e
                    );
                }

                if (!tempFullPatchedDexFile.exists()) {
                    throw new TinkerPatchException("can not find the temporary full patched dex file:" + tempFullPatchedDexFile.getAbsolutePath());
                }
                Logger.e("Gen %s for dalvik full dex file:%s, size:%d, md5:%s", dexName, tempFullPatchedDexFile.getAbsolutePath(), tempFullPatchedDexFile.length(), relatedInfo.newOrFullPatchedMd5);
            } else {
                // In this case newDexFile is the same as oldDexFile, but we still
                // need to treat it as patched dex file so that the SmallPatchGenerator
                // can analyze which class of this dex should be kept in small patch.
                relatedInfo.newOrFullPatchedFile = newFile;
                relatedInfo.newOrFullPatchedMd5 = relatedInfo.newMd5;
            }
        }

        Set<File> classNOldDexFiles = new HashSet<>();

        for (AbstractMap.SimpleEntry<File, File> oldAndNewDexFilePair : oldAndNewDexFilePairList) {
            File oldFile = oldAndNewDexFilePair.getKey();
            final String dexName = oldFile.getName();

            if (isDexNameMatchesClassNPattern(dexName)) {
                classNOldDexFiles.add(oldFile);
            }
        }

        // If we meet a case like:
        // classes.dex, classes2.dex, classes4.dex, classes5.dex
        // Since classes3.dex is missing, according to the logic in AOSP, we should not treat
        // rest dexes as part of class N dexes.
        Map<String, File> dexNameToClassNOldDexFileMap = new HashMap<>();
        for (File classNOldDex : classNOldDexFiles) {
            dexNameToClassNOldDexFileMap.put(classNOldDex.getName(), classNOldDex);
        }

        boolean isRestDexNotInClassN = false;
        for (int i = 0; i < classNOldDexFiles.size(); ++i) {
            final String expectedDexName = (i == 0 ? DexFormat.DEX_IN_JAR_NAME : "classes" + (i + 1) + ".dex");
            if (!dexNameToClassNOldDexFileMap.containsKey(expectedDexName)) {
                isRestDexNotInClassN = true;
            } else {
                if (isRestDexNotInClassN) {
                    File mistakenClassNOldDexFile = dexNameToClassNOldDexFileMap.get(expectedDexName);
                    classNOldDexFiles.remove(mistakenClassNOldDexFile);
                }
            }
        }

        File tempSmallPatchInfoFile = new File(config.mTempResultDir, TypedValue.DEX_SMALLPATCH_INFO_FILE);
        ensureDirectoryExist(tempSmallPatchInfoFile.getParentFile());

        // So far we know whether a pair of dex is belong to class N dexes or other dexes.
        // Then we collect class N dex pairs and other dex pairs by separate their old dex
        // and full patched dex into different list.
        SmallDexPatchGenerator smallDexPatchGenerator = new SmallDexPatchGenerator();
        smallDexPatchGenerator.setLoaderClassPatterns(config.mDexLoaderPattern);
        smallDexPatchGenerator.setLogger(dexPatcherLoggerBridge);

        logWriter.writeLineToInfoFile("\nStart collecting old dex and full patched dex...");

        List<File> classNOldDexFileList = new ArrayList<>();
        List<File> classNFullPatchedDexFileList = new ArrayList<>();
        List<File> otherOldDexFileList = new ArrayList<>();
        List<File> otherFullPatchedDexFileList = new ArrayList<>();
        for (AbstractMap.SimpleEntry<File, File> oldAndNewDexFilePair : oldAndNewDexFilePairList) {
            File oldFile = oldAndNewDexFilePair.getKey();
            final String dexName = oldFile.getName();
            File fullPatchedFile = dexNameToRelatedInfoMap.get(dexName).newOrFullPatchedFile;
            if (classNOldDexFiles.contains(oldFile)) {
                classNOldDexFileList.add(oldFile);
                classNFullPatchedDexFileList.add(fullPatchedFile);
            } else {
                otherOldDexFileList.add(oldFile);
                otherFullPatchedDexFileList.add(fullPatchedFile);
            }
        }

        logWriter.writeLineToInfoFile(String.format("\nCollected class N old dexes: %s", classNOldDexFileList));
        logWriter.writeLineToInfoFile(String.format("Collected class N full patched dexes: %s", classNFullPatchedDexFileList));
        logWriter.writeLineToInfoFile(String.format("\nCollected other old dexes: %s", otherOldDexFileList));
        logWriter.writeLineToInfoFile(String.format("Collected other full patched dexes: %s", otherFullPatchedDexFileList));

        smallDexPatchGenerator.appendDexGroup(DexGroup.wrap(classNOldDexFileList), DexGroup.wrap(classNFullPatchedDexFileList));

        if (!otherOldDexFileList.isEmpty()) {
            smallDexPatchGenerator.appendDexGroup(DexGroup.wrap(otherOldDexFileList), DexGroup.wrap(otherFullPatchedDexFileList));
        }

        try {
            Logger.d("Start generating small patch info file...");
            smallDexPatchGenerator.executeAndSaveTo(tempSmallPatchInfoFile);
        } catch (Exception e) {
            throw new TinkerPatchException("\nFailed to generate small patch info file.", e);
        }
        if (!tempSmallPatchInfoFile.exists()) {
            throw new TinkerPatchException("can not find the small patch info file:" + tempSmallPatchInfoFile.getAbsolutePath());
        }

        SmallPatchedDexItemFile smallPatchedDexItemFile = new SmallPatchedDexItemFile(tempSmallPatchInfoFile);

        // Generate small patched dex and write meta.
        for (AbstractMap.SimpleEntry<File, File> oldAndNewDexFilePair : oldAndNewDexFilePairList) {
            File oldFile = oldAndNewDexFilePair.getKey();
            File newFile = oldAndNewDexFilePair.getValue();
            final String dexName = oldFile.getName();
            final String oldDexSignStr = Hex.toHexString(new Dex(oldFile).computeSignature(false));
            File tempSmallPatchedFile = new File(tempSmallPatchDexPath, dexName);
            RelatedInfo relatedInfo = dexNameToRelatedInfoMap.get(dexName);
            File dexDiffFile = relatedInfo.dexDiffFile;

            if (!smallPatchedDexItemFile.isSmallPatchedDexEmpty(oldDexSignStr)) {
                try {
                    new DexPatchApplier(oldFile, dexDiffFile, smallPatchedDexItemFile).executeAndSaveTo(tempSmallPatchedFile);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new TinkerPatchException(
                            "Failed to generate temporary small patched dex, which makes MD5 generating procedure of small patched dex failed, either.", e
                    );
                }
                if (!tempSmallPatchedFile.exists()) {
                    throw new TinkerPatchException("can not find the temporary small patched dex file:" + tempSmallPatchInfoFile.getAbsolutePath());
                }
                relatedInfo.smallPatchedMd5 = MD5.getMD5(tempSmallPatchedFile);
                Logger.e("Gen %s for art small dex file:%s, size:%d, md5:%s", dexName, tempSmallPatchedFile.getAbsolutePath(), tempSmallPatchedFile.length(), relatedInfo.smallPatchedMd5);

                if (relatedInfo.oldMd5.equals(relatedInfo.newMd5)) {
                    // Unmodified dex, which has no dexDiffFile, and is ignored in dvm environment.
                    // So we pass zero string to destMd5InDvm and dexDiffMd5.
                    writeLogFiles(newFile, oldFile, relatedInfo.dexDiffFile, "0", relatedInfo.smallPatchedMd5, "0");
                } else {
                    writeLogFiles(newFile, oldFile, relatedInfo.dexDiffFile, relatedInfo.newOrFullPatchedMd5, relatedInfo.smallPatchedMd5, relatedInfo.dexDiffMd5);
                }
            }
        }

        addTestDex();

        // Here we will check if any classes that were deleted in one dex
        // would be added to another dex. e.g. classA is deleted in dex0 and
        // added in dex1.
        // Since DexClassesComparator will guarantee that a class can be either 'added'
        // or 'deleted' between two files it compares. We can achieve our checking works
        // by calculating the intersection of deletedClassDescs and addedClassDescs.
        Set<String> deletedClassDescs = new HashSet(deletedClassDescToDexNameMap.keySet());
        Set<String> addedClassDescs = new HashSet(addedClassDescToDexNameMap.keySet());
        deletedClassDescs.retainAll(addedClassDescs);

        // So far deletedClassNames only contains the intersect elements between
        // deletedClassNames and addedClassNames.
        Set<String> movedCrossFilesClassDescs = deletedClassDescs;
        if (!movedCrossFilesClassDescs.isEmpty()) {
            Logger.e("Warning:Class Moved. Some classes are just moved from one dex to another. "
                + "This behavior may leads to unnecessary enlargement of patch file. you should try to check them:");

            for (String classDesc : movedCrossFilesClassDescs) {
                StringBuilder sb = new StringBuilder();
                sb.append('{');
                sb.append("classDesc:").append(classDesc).append(',');
                sb.append("from:").append(deletedClassDescToDexNameMap.get(classDesc)).append(',');
                sb.append("to:").append(addedClassDescToDexNameMap.get(classDesc));
                sb.append('}');
                Logger.e(sb.toString());
            }
        }
    }

    @Override
    public void clean() {
        metaWriter.close();
        logWriter.close();
    }

    private void ensureDirectoryExist(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new TinkerPatchException("failed to create directory: " + dir);
            }
        }
    }

    private boolean isDexNameMatchesClassNPattern(String dexName) {
        return (dexName.matches("^classes[0-9]*\\.dex$"));
    }

    private void copyNewDexAndMarkInMeta(File newFile, String newMd5, File output) throws IOException {
        newMd5 = checkNewDexAndMd5(newMd5, newFile);
        FileOperation.copyFileUsingStream(newFile, output);
        writeLogFiles(newFile, null, null, newMd5, newMd5, "0");
    }

    private void checkAddedOrDeletedClasses(File oldFile, File newFile) throws IOException {
        // Before starting real diff works, we collect added class descriptor
        // and deleted class descriptor for further analysing.
        Dex oldDex = new Dex(oldFile);
        Dex newDex = new Dex(newFile);

        Set<String> oldClassDescs = new HashSet<>();
        for (ClassDef oldClassDef : oldDex.classDefs()) {
            oldClassDescs.add(oldDex.typeNames().get(oldClassDef.typeIndex));
        }

        Set<String> newClassDescs = new HashSet<>();
        for (ClassDef newClassDef : newDex.classDefs()) {
            newClassDescs.add(newDex.typeNames().get(newClassDef.typeIndex));
        }

        Set<String> addedClassDescs = new HashSet<>(newClassDescs);
        addedClassDescs.removeAll(oldClassDescs);

        Set<String> deletedClassDescs = new HashSet<>(oldClassDescs);
        deletedClassDescs.removeAll(newClassDescs);

        for (String addedClassDesc : addedClassDescs) {
            if (addedClassDescToDexNameMap.containsKey(addedClassDesc)) {
                throw new TinkerPatchException(
                        String.format(
                                "Class Duplicate. Class [%s] is added in both new dex: [%s] and [%s]. Please check your newly apk.",
                                addedClassDesc,
                                addedClassDescToDexNameMap.get(addedClassDesc),
                                newFile.toString()
                        )
                );
            } else {
                addedClassDescToDexNameMap.put(addedClassDesc, newFile.toString());
            }
        }

        for (String deletedClassDesc : deletedClassDescs) {
            if (deletedClassDescToDexNameMap.containsKey(deletedClassDesc)) {
                throw new TinkerPatchException(
                        String.format(
                                "Class Duplicate. Class [%s] is deleted in both old dex: [%s] and [%s]. Please check your base apk.",
                                deletedClassDesc,
                                addedClassDescToDexNameMap.get(deletedClassDesc),
                                oldFile.toString()
                        )
                );
            } else {
                deletedClassDescToDexNameMap.put(deletedClassDesc, newFile.toString());
            }
        }
    }

    private void checkDexChange(Dex originDex, Dex newDex) {
        DexClassesComparator classesCmptor = new DexClassesComparator("*");
        classesCmptor.setIgnoredRemovedClassDescPattern(config.mDexLoaderPattern);
        classesCmptor.startCheck(originDex, newDex);

        List<DexClassInfo> addedClassInfos = classesCmptor.getAddedClassInfos();
        boolean isNoClassesAdded = addedClassInfos.isEmpty();

        Map<String, DexClassInfo[]> changedClassDescToClassInfosMap;
        boolean isNoClassesChanged;

        if (isNoClassesAdded) {
            changedClassDescToClassInfosMap = classesCmptor.getChangedClassDescToInfosMap();
            isNoClassesChanged = changedClassDescToClassInfosMap.isEmpty();
        } else {
            throw new TinkerPatchException(
                    "some classes was unexpectedly added in patched new dex, check if there's any bugs in "
                            + "patch algorithm. Related classes: " + Utils.collectionToString(addedClassInfos)
            );
        }

        if (isNoClassesChanged) {
            List<DexClassInfo> deletedClassInfos = classesCmptor.getDeletedClassInfos();
            if (!deletedClassInfos.isEmpty()) {
                throw new TinkerPatchException(
                        "some classes that are not matched to loader class pattern "
                                + "was unexpectedly deleted in patched new dex, check if there's any bugs in "
                                + "patch algorithm. Related classes: "
                                + Utils.collectionToString(deletedClassInfos)
                );
            }
        } else {
            throw new TinkerPatchException(
                    "some classes was unexpectedly changed in patched new dex, check if there's any bugs in "
                            + "patch algorithm. Related classes: "
                            + Utils.collectionToString(changedClassDescToClassInfosMap.keySet())
            );
        }
    }

    private void addTestDex() throws IOException {
        //write test dex
        String dexMode = "jar";
        if (config.mDexRaw) {
            dexMode = "raw";
        }

        final InputStream is = DexDiffDecoder.class.getResourceAsStream("/" + TEST_DEX_PATH);
        String md5 = MD5.getMD5(is, 1024);
        is.close();

        String meta = TEST_DEX_PATH + "," + "" + "," + md5 + "," + md5 + "," + 0 + "," + 0 + "," + dexMode;

        File dest = new File(config.mTempResultDir + "/" + TEST_DEX_PATH);
        FileOperation.copyResourceUsingStream(TEST_DEX_PATH, dest);
        Logger.d("Add test install result dex: %s, size:%d", dest.getAbsolutePath(), dest.length());
        Logger.d("DexDecoder:write test dex meta file data: %s", meta);

        metaWriter.writeLineToInfoFile(meta);
    }

    private String checkNewDexAndMd5(String md5, File dexFile) {
        String name = dexFile.getName();
        if (name.endsWith(".dex")) {
            return md5;
        } else {
            try {
                final JarFile dexJar = new JarFile(dexFile);
                ZipEntry classesDex = dexJar.getEntry(DexFormat.DEX_IN_JAR_NAME);
                // no code
                if (null == classesDex) {
                    throw new TinkerPatchException(
                        String.format("dex jar file %s do not contain 'classes.dex', it is not a correct dex jar file!", dexFile.getAbsolutePath())
                    );
                }

                return MD5.getMD5(dexJar.getInputStream(classesDex), 1024 * 100);
            } catch (IOException e) {
                throw new TinkerPatchException(
                    String.format("dex file %s is not end with '.dex', but it is not a correct dex jar file also!", dexFile.getAbsolutePath()), e
                );
            }
        }
    }

    private String getRelativeStringBy(File file, File reference) {
        File actualReference = reference.getParentFile();
        if (actualReference == null) {
            actualReference = reference;
        }
        return actualReference.toPath().relativize(file.toPath()).toString().replace("\\", "/");
    }

    /**
     * Construct dex meta-info and write it to meta file and log.
     *
     * @param newOrFullPatchedFile
     * New dex file or full patched dex file.
     * @param oldFile
     * Old dex file.
     * @param dexDiffFile
     * Dex diff file. (Generated by DexPatchGenerator.)
     * @param destMd5InDvm
     * Md5 of output dex in dvm environment, could be full patched dex md5 or new dex.
     * @param destMd5InArt
     * Md5 of output dex in dvm environment, could be small patched dex md5 or new dex.
     * @param dexDiffMd5
     * Md5 of dex patch info file.
     *
     * @throws IOException
     */
    protected void writeLogFiles(File newOrFullPatchedFile, File oldFile, File dexDiffFile, String destMd5InDvm, String destMd5InArt, String dexDiffMd5) throws IOException {
        if (metaWriter == null && logWriter == null) {
            return;
        }
        String parentRelative = getParentRelativeString(newOrFullPatchedFile);
        String relative = getRelativeString(newOrFullPatchedFile);

        if (metaWriter != null) {
            String fileName = newOrFullPatchedFile.getName();
            String dexMode = "jar";
            if (config.mDexRaw) {
                dexMode = "raw";
            }

            //new file
            String oldCrc;
            if (oldFile == null) {
                oldCrc = "0";
                Logger.d("DexDecoder:add newly dex file: %s", parentRelative);
            } else {
                oldCrc = FileOperation.getZipEntryCrc(config.mOldApkFile, relative);
                if (oldCrc == null || oldCrc.equals("0")) {
                    throw new TinkerPatchException(
                        String.format("can't find zipEntry %s from old apk file %s", relative, config.mOldApkFile.getPath())
                    );
                }
            }

            String meta = fileName + "," + parentRelative + "," + destMd5InDvm + "," + destMd5InArt + "," + dexDiffMd5 + "," + oldCrc + "," + dexMode;

            Logger.d("DexDecoder:write meta file data: %s", meta);
            metaWriter.writeLineToInfoFile(meta);
        }

        if (logWriter != null) {
            String log = relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                + FileOperation.getFileSizes(newOrFullPatchedFile) + ", diffSize=" + FileOperation.getFileSizes(dexDiffFile);

            logWriter.writeLineToInfoFile(log);
        }
    }

    private final class RelatedInfo {
        File newOrFullPatchedFile = null;
        /**
         * This field could be null if old dex and new dex
         *  are the same.
         */
        File dexDiffFile = null;
        String oldMd5 = "0";
        String newMd5 = "0";
        String dexDiffMd5 = "0";
        /**
         * This field could be one of the following value:
         *  fullPatchedDex md5, if old dex and new dex are different;
         *  newDex md5, if new dex is marked to be copied directly;
         */
        String newOrFullPatchedMd5 = "0";
        String smallPatchedMd5 = "0";
    }

    private final class DexPatcherLoggerBridge implements IDexPatcherLogger {
        private final InfoWriter logWritter;

        DexPatcherLoggerBridge(InfoWriter logWritter) {
            this.logWritter = logWritter;
        }

        @Override
        public void v(String msg) {
            this.logWritter.writeLineToInfoFile(msg);
        }

        @Override
        public void d(String msg) {
            this.logWritter.writeLineToInfoFile(msg);
        }

        @Override
        public void i(String msg) {
            this.logWritter.writeLineToInfoFile(msg);
        }

        @Override
        public void w(String msg) {
            this.logWritter.writeLineToInfoFile(msg);
        }

        @Override
        public void e(String msg) {
            this.logWritter.writeLineToInfoFile(msg);
        }
    }
}


