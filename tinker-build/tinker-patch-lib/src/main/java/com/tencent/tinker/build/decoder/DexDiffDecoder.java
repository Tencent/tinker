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
import com.tencent.tinker.build.dexpatcher.DexPatchGenerator;
import com.tencent.tinker.build.dexpatcher.util.ChangedClassesDexClassInfoCollector;
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

import org.jf.dexlib2.builder.BuilderMutableMethodImplementation;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.writer.builder.BuilderField;
import org.jf.dexlib2.writer.builder.BuilderMethod;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.dexlib2.writer.io.FileDataStore;

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
    private static final String TEST_DEX_NAME = "test.dex";
    private static final String CHANGED_CLASSES_DEX_NAME = "changed_classes.dex";

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

    /**
     * Provide /oldFileRoot/dir/to/oldDex, /newFileRoot/dir/to/newDex,
     * return dir/to/oldDex or dir/to/newDex if any one is not null.
     */
    protected String getRelativeDexName(File oldDexFile, File newDexFile) {
        return oldDexFile != null ? getRelativePathStringToOldFile(oldDexFile) : getRelativePathStringToNewFile(newDexFile);
    }

    @SuppressWarnings("NewApi")
    @Override
    public boolean patch(final File oldFile, final File newFile) throws IOException, TinkerPatchException {
        final String dexName = getRelativeDexName(oldFile, newFile);

        // first of all, we should check input files if excluded classes were modified.
        Logger.d("Check for loader classes in dex: %s", dexName);

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

        final String newMd5 = getRawOrWrappedDexMD5(newFile);

        //new add file
        if (oldFile == null || !oldFile.exists() || oldFile.length() == 0) {
            hasDexChanged = true;
            copyNewDexAndLogToDexMeta(newFile, newMd5, dexDiffOut);
            return true;
        }

        final String oldMd5 = getRawOrWrappedDexMD5(oldFile);

        if ((oldMd5 != null && !oldMd5.equals(newMd5)) || (oldMd5 == null && newMd5 != null)) {
            hasDexChanged = true;
            if (oldMd5 != null) {
                collectAddedOrDeletedClasses(oldFile, newFile);
            }
        }

        RelatedInfo relatedInfo = new RelatedInfo();
        relatedInfo.oldMd5 = oldMd5;
        relatedInfo.newMd5 = newMd5;

        // collect current old dex file and corresponding new dex file for further processing.
        oldAndNewDexFilePairList.add(new AbstractMap.SimpleEntry<>(oldFile, newFile));

        dexNameToRelatedInfoMap.put(dexName, relatedInfo);

        return true;
    }

    @Override
    public void onAllPatchesEnd() throws Exception {
        if (!hasDexChanged) {
            Logger.d("No dexes were changed, nothing needs to be done next.");
            return;
        }

        if (config.mIsProtectedApp) {
            generateChangedClassesDexFile();
        } else {
            generatePatchInfoFile();
        }

        addTestDex();
    }

    @SuppressWarnings("NewApi")
    private void generateChangedClassesDexFile() throws IOException {
        final String dexMode = config.mDexRaw ? "raw" : "jar";
        final File dest = new File(config.mTempResultDir + "/" + CHANGED_CLASSES_DEX_NAME);

        Logger.d("\nBuilding changed classes dex: %s, size: %d\n", dest.getAbsolutePath(), dest.length());

        List<File> oldDexList = new ArrayList<>();
        List<File> newDexList = new ArrayList<>();
        for (AbstractMap.SimpleEntry<File, File> oldAndNewDexFilePair : oldAndNewDexFilePairList) {
            File oldDexFile = oldAndNewDexFilePair.getKey();
            File newDexFile = oldAndNewDexFilePair.getValue();
            if (oldDexFile != null) {
                oldDexList.add(oldDexFile);
            }
            if (newDexFile != null) {
                newDexList.add(newDexFile);
            }
        }

        DexGroup oldDexGroup = DexGroup.wrap(oldDexList);
        DexGroup newDexGroup = DexGroup.wrap(newDexList);

        ChangedClassesDexClassInfoCollector collector = new ChangedClassesDexClassInfoCollector();
        collector.setExcludedClassPatterns(config.mDexLoaderPattern);
        collector.setLogger(dexPatcherLoggerBridge);
        collector.setIncludeRefererToRefererAffectedClasses(true);

        Set<DexClassInfo> classInfosInChangedClassesDex = collector.doCollect(oldDexGroup, newDexGroup);

        Set<String> descsOfClassInChangedClassesDex = new HashSet<>();
        Set<Dex> owners = new HashSet<>();
        for (DexClassInfo classInfo : classInfosInChangedClassesDex) {
            descsOfClassInChangedClassesDex.add(classInfo.classDesc);
            owners.add(classInfo.owner);
        }

        DexBuilder dexBuilder = DexBuilder.makeDexBuilder();
        for (Dex dex : owners) {
            DexFile dexFile = new DexBackedDexFile(org.jf.dexlib2.Opcodes.forApi(20), dex.getBytes());
            for (org.jf.dexlib2.iface.ClassDef classDef : dexFile.getClasses()) {
                if (!descsOfClassInChangedClassesDex.contains(classDef.getType())) {
                    continue;
                }

                Logger.d("Class %s will be added into changed classes dex ...", classDef.getType());

                List<BuilderField> builderFields = new ArrayList<>();
                for (Field field : classDef.getFields()) {
                    final BuilderField builderField = dexBuilder.internField(
                            field.getDefiningClass(),
                            field.getName(),
                            field.getType(),
                            field.getAccessFlags(),
                            field.getInitialValue(),
                            field.getAnnotations()
                    );
                    builderFields.add(builderField);
                }
                List<BuilderMethod> builderMethods = new ArrayList<>();

                for (Method method : classDef.getMethods()) {
                    BuilderMethod builderMethod = dexBuilder.internMethod(
                            method.getDefiningClass(),
                            method.getName(),
                            method.getParameters(),
                            method.getReturnType(),
                            method.getAccessFlags(),
                            method.getAnnotations(),
                            new BuilderMutableMethodImplementation(dexBuilder, method.getImplementation())
                    );
                    builderMethods.add(builderMethod);
                }
                dexBuilder.internClassDef(
                        classDef.getType(),
                        classDef.getAccessFlags(),
                        classDef.getSuperclass(),
                        classDef.getInterfaces(),
                        classDef.getSourceFile(),
                        classDef.getAnnotations(),
                        builderFields,
                        builderMethods
                );
            }
        }

        // Write constructed changed classes dex to file and record it in meta file.
        FileDataStore fileDataStore = new FileDataStore(dest);
        dexBuilder.writeTo(fileDataStore);

        final String md5 = MD5.getMD5(dest);

        String meta = CHANGED_CLASSES_DEX_NAME + "," + "" + "," + md5 + "," + md5 + "," + 0
                + "," + 0 + "," + dexMode;

        Logger.d("\nDexDecoder:write changed classes dex meta file data: %s", meta);

        metaWriter.writeLineToInfoFile(meta);
    }

    @SuppressWarnings("NewApi")
    private void generatePatchInfoFile() throws IOException {
        generatePatchedDexInfoFile();

        // generateSmallPatchedDexInfoFile is blocked by issue we found in ART environment
        // which indicates that if inline optimization is done on patched class, some error
        // such as crash, ClassCastException, mistaken string fetching, etc. would happen.
        //
        // Instead, we will log all classN dexes as 'copy directly' in dex-meta, so that
        // tinker patch applying procedure will copy them out and load them in ART environment.

        //generateSmallPatchedDexInfoFile();

        logDexesToDexMeta();

        checkCrossDexMovingClasses();
    }

    @SuppressWarnings("NewApi")
    private void logDexesToDexMeta() throws IOException {
        Map<String, File> dexNameToClassNOldDexFileMap = new HashMap<>();
        Set<File> realClassNDexFiles = new HashSet<>();

        for (AbstractMap.SimpleEntry<File, File> oldAndNewDexFilePair : oldAndNewDexFilePairList) {
            File oldFile = oldAndNewDexFilePair.getKey();
            final String dexName = getRelativeDexName(oldFile, null);
            if (isDexNameMatchesClassNPattern(dexName)) {
                dexNameToClassNOldDexFileMap.put(dexName, oldFile);
            }
        }

        // If we meet a case like:
        // classes.dex, classes2.dex, classes4.dex, classes5.dex
        // Since classes3.dex is missing, according to the logic in AOSP, we should not treat
        // rest dexes as part of class N dexes.
        for (int i = 0; i < dexNameToClassNOldDexFileMap.size(); ++i) {
            final String expectedDexName = (i == 0 ? DexFormat.DEX_IN_JAR_NAME : "classes" + (i + 1) + ".dex");
            if (dexNameToClassNOldDexFileMap.containsKey(expectedDexName)) {
                File oldDexFile = dexNameToClassNOldDexFileMap.get(expectedDexName);
                realClassNDexFiles.add(oldDexFile);
            } else {
                break;
            }
        }

        for (AbstractMap.SimpleEntry<File, File> oldAndNewDexFilePair : oldAndNewDexFilePairList) {
            final File oldDexFile = oldAndNewDexFilePair.getKey();
            final File newDexFile = oldAndNewDexFilePair.getValue();
            final String dexName = getRelativeDexName(oldDexFile, newDexFile);
            final RelatedInfo relatedInfo = dexNameToRelatedInfoMap.get(dexName);
            if (!relatedInfo.oldMd5.equals(relatedInfo.newMd5)) {
                logToDexMeta(newDexFile, oldDexFile, relatedInfo.dexDiffFile, relatedInfo.newOrFullPatchedMd5, relatedInfo.newOrFullPatchedMd5, relatedInfo.dexDiffMd5);
            } else {
                // For class N dexes, if new dex is the same as old dex, we should log it as 'copy directly'
                // in dex meta to fix problems in Art environment.
                if (realClassNDexFiles.contains(oldDexFile)) {
                    // Bugfix: However, if what we would copy directly is main dex, we should do an additional diff operation
                    // so that patch applier would help us remove all loader classes of it in runtime.
                    if (dexName.equals(DexFormat.DEX_IN_JAR_NAME)) {
                        Logger.d("\nDo additional diff on main dex to remove loader classes in it.");
                        diffDexPairAndFillRelatedInfo(oldDexFile, newDexFile, relatedInfo);
                        logToDexMeta(newDexFile, oldDexFile, relatedInfo.dexDiffFile, relatedInfo.newOrFullPatchedMd5, relatedInfo.newOrFullPatchedMd5, relatedInfo.dexDiffMd5);
                    } else {
                        logToDexMeta(newDexFile, oldDexFile, null, "0", relatedInfo.oldMd5, "0");
                    }
                }
            }
        }
    }

    @SuppressWarnings("NewApi")
    private void generatePatchedDexInfoFile() {
        // Generate dex diff out and full patched dex if a pair of dex is different.
        for (AbstractMap.SimpleEntry<File, File> oldAndNewDexFilePair : oldAndNewDexFilePairList) {
            File oldFile = oldAndNewDexFilePair.getKey();
            File newFile = oldAndNewDexFilePair.getValue();
            final String dexName = getRelativeDexName(oldFile, newFile);
            RelatedInfo relatedInfo = dexNameToRelatedInfoMap.get(dexName);
            if (!relatedInfo.oldMd5.equals(relatedInfo.newMd5)) {
                diffDexPairAndFillRelatedInfo(oldFile, newFile, relatedInfo);
            } else {
                // In this case newDexFile is the same as oldDexFile, but we still
                // need to treat it as patched dex file so that the SmallPatchGenerator
                // can analyze which class of this dex should be kept in small patch.
                relatedInfo.newOrFullPatchedFile = newFile;
                relatedInfo.newOrFullPatchedMd5 = relatedInfo.newMd5;
            }
        }
    }

    private void diffDexPairAndFillRelatedInfo(File oldDexFile, File newDexFile, RelatedInfo relatedInfo) {
        File tempFullPatchDexPath = new File(config.mOutFolder + File.separator + TypedValue.DEX_TEMP_PATCH_DIR);
        final String dexName = getRelativeDexName(oldDexFile, newDexFile);

        File dexDiffOut = getOutputPath(newDexFile).toFile();
        ensureDirectoryExist(dexDiffOut.getParentFile());

        try {
            DexPatchGenerator dexPatchGen = new DexPatchGenerator(oldDexFile, newDexFile);
            dexPatchGen.setAdditionalRemovingClassPatterns(config.mDexLoaderPattern);

            logWriter.writeLineToInfoFile(
                    String.format(
                            "Start diff between [%s] as old and [%s] as new:",
                            getRelativeStringBy(oldDexFile, config.mTempUnzipOldDir),
                            getRelativeStringBy(newDexFile, config.mTempUnzipNewDir)
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
        Logger.d("\nGen %s patch file:%s, size:%d, md5:%s", dexName, relatedInfo.dexDiffFile.getAbsolutePath(), relatedInfo.dexDiffFile.length(), relatedInfo.dexDiffMd5);

        File tempFullPatchedDexFile = new File(tempFullPatchDexPath, dexName);
        if (!tempFullPatchedDexFile.exists()) {
            ensureDirectoryExist(tempFullPatchedDexFile.getParentFile());
        }

        try {
            new DexPatchApplier(oldDexFile, dexDiffOut).executeAndSaveTo(tempFullPatchedDexFile);

            Logger.d(
                    String.format("Verifying if patched new dex is logically the same as original new dex: %s ...", getRelativeStringBy(newDexFile, config.mTempUnzipNewDir))
            );

            Dex origNewDex = new Dex(newDexFile);
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
        Logger.d("\nGen %s for dalvik full dex file:%s, size:%d, md5:%s", dexName, tempFullPatchedDexFile.getAbsolutePath(), tempFullPatchedDexFile.length(), relatedInfo.newOrFullPatchedMd5);
    }

    private void addTestDex() throws IOException {
        //write test dex
        String dexMode = "jar";
        if (config.mDexRaw) {
            dexMode = "raw";
        }

        final InputStream is = DexDiffDecoder.class.getResourceAsStream("/" + TEST_DEX_NAME);
        String md5 = MD5.getMD5(is, 1024);
        is.close();

        String meta = TEST_DEX_NAME + "," + "" + "," + md5 + "," + md5 + "," + 0 + "," + 0 + "," + dexMode;

        File dest = new File(config.mTempResultDir + "/" + TEST_DEX_NAME);
        FileOperation.copyResourceUsingStream(TEST_DEX_NAME, dest);
        Logger.d("\nAdd test install result dex: %s, size:%d", dest.getAbsolutePath(), dest.length());
        Logger.d("DexDecoder:write test dex meta file data: %s", meta);

        metaWriter.writeLineToInfoFile(meta);
    }

    private void checkCrossDexMovingClasses() {
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

    /**
     * Before starting real diff works, we collect added class descriptor
     * and deleted class descriptor for further analysing in {@code checkCrossDexMovingClasses}.
     */
    private void collectAddedOrDeletedClasses(File oldFile, File newFile) throws IOException {
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

    private boolean isDexNameMatchesClassNPattern(String dexName) {
        return (dexName.matches("^classes[0-9]*\\.dex$"));
    }

    private void copyNewDexAndLogToDexMeta(File newFile, String newMd5, File output) throws IOException {
        FileOperation.copyFileUsingStream(newFile, output);
        logToDexMeta(newFile, null, null, newMd5, newMd5, "0");
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
    protected void logToDexMeta(File newOrFullPatchedFile, File oldFile, File dexDiffFile, String destMd5InDvm, String destMd5InArt, String dexDiffMd5) throws IOException {
        if (metaWriter == null && logWriter == null) {
            return;
        }
        String parentRelative = getParentRelativePathStringToNewFile(newOrFullPatchedFile);
        String relative = getRelativePathStringToNewFile(newOrFullPatchedFile);

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

    @Override
    public void clean() {
        metaWriter.close();
        logWriter.close();
    }

    private String getRawOrWrappedDexMD5(File dexOrJarFile) {
        final String name = dexOrJarFile.getName();
        if (name.endsWith(".dex")) {
            return MD5.getMD5(dexOrJarFile);
        } else {
            JarFile dexJar = null;
            try {
                dexJar = new JarFile(dexOrJarFile);
                ZipEntry classesDex = dexJar.getEntry(DexFormat.DEX_IN_JAR_NAME);
                // no code
                if (classesDex == null) {
                    throw new TinkerPatchException(
                            String.format("Jar file %s do not contain 'classes.dex', it is not a correct dex jar file!", dexOrJarFile.getAbsolutePath())
                    );
                }
                return MD5.getMD5(dexJar.getInputStream(classesDex), 1024 * 100);
            } catch (IOException e) {
                throw new TinkerPatchException(
                        String.format("File %s is not end with '.dex', but it is not a correct dex jar file !", dexOrJarFile.getAbsolutePath()), e
                );
            } finally {
                if (dexJar != null) {
                    try {
                        dexJar.close();
                    } catch (Exception e) {
                        // Ignored.
                    }
                }
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

    private void ensureDirectoryExist(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new TinkerPatchException("failed to create directory: " + dir);
            }
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
    }

    private final class DexPatcherLoggerBridge implements IDexPatcherLogger {
        private final InfoWriter logWriter;

        DexPatcherLoggerBridge(InfoWriter logWritter) {
            this.logWriter = logWritter;
        }

        @Override
        public void v(String msg) {
            this.logWriter.writeLineToInfoFile(msg);
        }

        @Override
        public void d(String msg) {
            this.logWriter.writeLineToInfoFile(msg);
        }

        @Override
        public void i(String msg) {
            this.logWriter.writeLineToInfoFile(msg);
        }

        @Override
        public void w(String msg) {
            this.logWriter.writeLineToInfoFile(msg);
        }

        @Override
        public void e(String msg) {
            this.logWriter.writeLineToInfoFile(msg);
        }
    }
}


