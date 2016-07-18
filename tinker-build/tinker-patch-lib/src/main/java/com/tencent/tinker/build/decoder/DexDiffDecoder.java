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


import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.DexFormat;
import com.tencent.tinker.build.dexdifflib.DexDiff;
import com.tencent.tinker.build.dexdifflib.util.PatternUtils;
import com.tencent.tinker.build.info.InfoWriter;
import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.DexClassesComparator;
import com.tencent.tinker.build.util.ExcludedClassModifiedChecker;
import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.MD5;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;
import com.tencent.tinker.build.util.Utils;
import com.tencent.tinker.commons.dexdifflib.DexPatch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Created by shwenzhang on 2016/3/23.
 */
public class DexDiffDecoder extends BaseDecoder {
    private final InfoWriter logWriter;
    private final InfoWriter metaWriter;

    private final ExcludedClassModifiedChecker excludedClassModifiedChecker;
    private final DexClassesComparator         addedOrDeletedClassesCmptor;

    private final Map<String, String> addedClassDescToDexNameMap;
    private final Map<String, String> deletedClassDescToDexNameMap;

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

        excludedClassModifiedChecker = new ExcludedClassModifiedChecker(config);
        addedOrDeletedClassesCmptor = new DexClassesComparator("*");

        addedClassDescToDexNameMap = new HashMap<>();
        deletedClassDescToDexNameMap = new HashMap<>();
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {

    }

    private void dealWithNewDexFile(File newFile, String newMd5, File output) throws IOException {
        newMd5 = checkNewDexAndMd5(newMd5, newFile);
        FileOperation.copyFileUsingStream(newFile, output);
        writeLogFiles(newFile, null, newFile, newMd5, null);
    }

    private boolean dealWithClassNDex(File dexFile, String dexMd5) throws IOException {
        String relative = getRelativeString(dexFile);
        //file is classN dex
        if (relative.startsWith("classes") && relative.endsWith(".dex")) {
            writeLogFiles(dexFile, dexFile, null, dexMd5, dexMd5);
            return true;
        }
        return false;
    }

    @Override
    public boolean patch(final File oldFile, final File newFile) throws IOException, TinkerPatchException {
        //fist of all, we should check input files
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
        if (newFile == null) {
            return false;
        }

        //new add file
        String newMd5 = MD5.getMD5(newFile);

        File dexDiffOut = getOutputPath(newFile).toFile();
        if (oldFile == null) {
            dealWithNewDexFile(newFile, newMd5, dexDiffOut);
            return true;
        }

        String oldMd5 = MD5.getMD5(oldFile);
        if (oldMd5.equals(newMd5)) {
            return dealWithClassNDex(newFile, newMd5);
        }

        if (!dexDiffOut.getParentFile().exists()) {
            dexDiffOut.getParentFile().mkdirs();
        }

        checkAddedOrDeletedClasses(oldFile, newFile);

        try {
            List<String> excludeClassPatternList = new ArrayList<>();
            excludeClassPatternList.addAll(config.mDexLoaderPattern);
            DexDiff dexDiff = new DexDiff(oldFile, newFile);
            dexDiff.setLogWriter(new DexDiff.LogWriter() {
                @Override
                public void write(String message) {
                    logWriter.writeLineToInfoFile(message);
                }
            });

            logWriter.writeLineToInfoFile(
                String.format(
                    "Start diff between [%s] as old and [%s] as new:",
                    getRelativeStringBy(oldFile, config.mTempUnzipOldDir),
                    getRelativeStringBy(newFile, config.mTempUnzipNewDir)
                )
            );

            dexDiff.doDiffAndSaveTo(dexDiffOut, excludeClassPatternList);
        } catch (Exception e) {
            throw new TinkerPatchException(e);
        }

        if (!dexDiffOut.exists()) {
            throw new TinkerPatchException("can not find the diff file:" + dexDiffOut.getAbsolutePath());
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream((int) newFile.length());
            new DexPatch(dexDiffOut).applyPatchAndSave(oldFile, baos);
            byte[] patchedDexData = baos.toByteArray();

            Logger.d(
                String.format("Verifying if patched new dex is logically the same as original new dex: %s ...", getRelativeStringBy(newFile, config.mTempUnzipNewDir))
            );

            Dex origNewDex = new Dex(newFile);
            Dex patchedNewDex = new Dex(patchedDexData);
            checkDexChange(origNewDex, patchedNewDex);

            newMd5 = MD5.getMessageDigest(patchedDexData);
        } catch (Exception e) {
            e.printStackTrace();
            throw new TinkerPatchException(
                "Failed to generate temporary patched dex, which makes MD5 generating procedure of new dex failed, either.", e
            );
        }

        //check dexDiffOut file size
        double ratio = dexDiffOut.length() / (double) newFile.length();
        double maxRatio = newFile.getName().endsWith(".dex") ? TypedValue.DEX_PATCH_MAX_RATIO : TypedValue.DEX_JAR_PATCH_MAX_RATIO;
        //I think it is better to treat as new file!
        if (ratio > maxRatio) {
            Logger.e("dex patch file:%s, size:%dk, new dex file:%s, size:%dk. patch file is too large, treat it as newly file to save patch time!",
                dexDiffOut.getName(),
                dexDiffOut.length() / 1024,
                newFile.getName(),
                newFile.length() / 1024
            );
            dealWithNewDexFile(newFile, newMd5, dexDiffOut);
        } else {
            writeLogFiles(newFile, oldFile, dexDiffOut, newMd5, oldMd5);
        }
        return true;
    }

    private void checkAddedOrDeletedClasses(File oldFile, File newFile) throws IOException {
        // Before starting real diff works, we collect added class descriptor
        // and deleted class descriptor for further analysing.
        addedOrDeletedClassesCmptor.startCheck(oldFile, newFile);
        for (String addedClassDesc : addedOrDeletedClassesCmptor.getAddedClassDescriptors()) {
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

        for (String deletedClassDesc : addedOrDeletedClassesCmptor.getDeletedClassDescriptors()) {
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
        classesCmptor.setLoaderClassDescPattern(config.mDexLoaderPattern);
        classesCmptor.startCheck(originDex, newDex);

        Collection<String> addedClassDescs = classesCmptor.getAddedClassDescriptors();
        boolean isNoClassesAdded = addedClassDescs.isEmpty();

        Collection<String> changedClassDescs;
        boolean isNoClassesChanged;

        if (isNoClassesAdded) {
            changedClassDescs = classesCmptor.getChangedClassDescriptors();
            isNoClassesChanged = changedClassDescs.isEmpty();
        } else {
            throw new TinkerPatchException(
                "some classes was unexpectedly added in patched new dex, check if there's any bugs in "
                    + "patch algorithm. Related classes: " + Utils.collectionToString(addedClassDescs)
            );
        }

        if (isNoClassesChanged) {
            List<Pattern> loaderClassDescPatternList = new ArrayList<>();
            for (String loaderClassPatternStr : config.mDexLoaderPattern) {
                loaderClassDescPatternList.add(
                    Pattern.compile(
                        PatternUtils.dotClassNamePatternToDescriptorRegEx(loaderClassPatternStr)
                    )
                );
            }

            Collection<String> deletedClassDescs = classesCmptor.getDeletedClassDescriptors();
            List<String> deletedNotLoaderClassDescs = new ArrayList<>();
            for (String desc : deletedClassDescs) {
                if (!Utils.isStringMatchesPatterns(desc, loaderClassDescPatternList)) {
                    deletedNotLoaderClassDescs.add(desc);
                }
            }

            if (!deletedNotLoaderClassDescs.isEmpty()) {
                throw new TinkerPatchException(
                    "some classes that are not matched to loader class pattern "
                        + "was unexpectedly deleted in patched new dex, check if there's any bugs in "
                        + "patch algorithm. Related classes: "
                        + Utils.collectionToString(deletedNotLoaderClassDescs)
                );
            }
        } else {
            throw new TinkerPatchException(
                "some classes was unexpectedly changed in patched new dex, check if there's any bugs in "
                    + "patch algorithm. Related classes: "
                    + Utils.collectionToString(changedClassDescs)
            );
        }
    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {
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

    protected void writeLogFiles(File newFile, File oldFile, File bsDiff, String newMd5, String oldMd5) throws IOException {
        if (metaWriter != null) {
            String parentRelative = getParentRelativeString(newFile);
            String fileName = newFile.getName();
            String dexMode = "jar";
            if (config.mDexRaw) {
                dexMode = "raw";
            }
            //new file
            if (oldFile == null) {
                oldMd5 = "0";
                Logger.d("dexDecoder:add newly dex file: %s", parentRelative);
            }

            String bsDiffMd5;

            if (bsDiff == null) {
                bsDiffMd5 = "0";
                Logger.d("dexDecoder:add dex for classN.dex: %s", parentRelative);
            } else {
                bsDiffMd5 = MD5.getMD5(bsDiff);
            }

            String meta = fileName + "," + parentRelative + "," + newMd5 + "," + oldMd5 + "," + bsDiffMd5 + "," + dexMode;

            Logger.d("dexDecoder:write meta file data: %s", meta);
            metaWriter.writeLineToInfoFile(meta);
        }

        if (logWriter != null) {
            String relative = getRelativeString(newFile);

            String log = relative + ", oldSize=" + FileOperation.getFileSizes(oldFile) + ", newSize="
                + FileOperation.getFileSizes(newFile) + ", diffSize=" + FileOperation.getFileSizes(bsDiff);

            logWriter.writeLineToInfoFile(log);
        }
    }
}


