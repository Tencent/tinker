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

package com.tencent.tinker.build.patch;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by zhangshaowen on 1/9/16.
 */
public class InputParam {
    /**
     * tinkerPatch
     */
    public final String  oldApk;
    public final String  newApk;
    public final String  outFolder;
    public final File    signFile;
    public final String  keypass;
    public final String  storealias;
    public final String  storepass;
    public final boolean ignoreWarning;
    public final boolean allowLoaderInAnyDex;
    public final boolean removeLoaderForAllDex;
    public final boolean isProtectedApp;
    public final boolean supportHotplugComponent;
    public final boolean useSign;

    /**
     * tinkerPatch.dex
     */
    public final ArrayList<String> dexFilePattern;
    public final ArrayList<String> dexLoaderPattern;
    public final ArrayList<String> dexIgnoreWarningLoaderPattern;

    public final String                  dexMode;
    /**
     * tinkerPatch.lib
     */
    public final ArrayList<String>       soFilePattern;
    /**
     * tinkerPatch.resource pattern
     */
    public final ArrayList<String>       resourceFilePattern;
    /**
     * tinkerPath.resource ignoreChange
     */
    public final ArrayList<String>       resourceIgnoreChangePattern;
    /**
     * tinkerPatch.resource ignoreChangeWarning
     */
    public final ArrayList<String>       resourceIgnoreChangeWarningPattern;
    /**
     * tinkerPath.resource largeModSize
     */
    public final int                     largeModSize;
    /**
     * tinkerPath.buildConfig applyResourceMapping
     */
    public final boolean                 useApplyResource;
    /**
     * tinkerPatch.packageConfig
     */
    public final HashMap<String, String> configFields;
    /**
     * tinkerPatch.sevenZip
     */
    public final String                  sevenZipPath;

    /**
     * TinkerPatch ark
     */
    public final String arkHotPatchPath;
    public final String arkHotPatchName;

    private InputParam(
            String oldApk,
            String newApk,
            String outFolder,
            File signFile,
            String keypass,
            String storealias,
            String storepass,
            boolean ignoreWarning,
            boolean allowLoaderInAnyDex,
            boolean removeLoaderForAllDex,
            boolean isProtectedApp,
            boolean supportHotplugComponent,
            boolean useSign,

            ArrayList<String> dexFilePattern,
            ArrayList<String> dexLoaderPattern,
            ArrayList<String> dexIgnoreChangeLoaderPattern,

            String dexMode,
            ArrayList<String> soFilePattern,
            ArrayList<String> resourceFilePattern,
            ArrayList<String> resourceIgnoreChangePattern,
            ArrayList<String> resourceIgnoreChangeWarningPattern,
            int largeModSize,
            boolean useApplyResource,
            HashMap<String, String> configFields,

        String sevenZipPath,
        String arkHotPatchPath,
        String arkHotPatchName
    ) {
        this.oldApk = oldApk;
        this.newApk = newApk;
        this.outFolder = outFolder;
        this.signFile = signFile;
        this.keypass = keypass;
        this.storealias = storealias;
        this.storepass = storepass;
        this.ignoreWarning = ignoreWarning;
        this.allowLoaderInAnyDex = allowLoaderInAnyDex;
        this.removeLoaderForAllDex = removeLoaderForAllDex;
        this.isProtectedApp = isProtectedApp;
        this.supportHotplugComponent = supportHotplugComponent;
        this.useSign = useSign;

        this.dexFilePattern = dexFilePattern;
        this.dexLoaderPattern = dexLoaderPattern;
        this.dexIgnoreWarningLoaderPattern = dexIgnoreChangeLoaderPattern;
        this.dexMode = dexMode;

        this.soFilePattern = soFilePattern;
        this.resourceFilePattern = resourceFilePattern;
        this.resourceIgnoreChangePattern = resourceIgnoreChangePattern;
        this.resourceIgnoreChangeWarningPattern = resourceIgnoreChangeWarningPattern;
        this.largeModSize = largeModSize;
        this.useApplyResource = useApplyResource;

        this.configFields = configFields;

        this.sevenZipPath = sevenZipPath;
        this.arkHotPatchPath = arkHotPatchPath;
        this.arkHotPatchName = arkHotPatchName;
    }

    public static class Builder {
        /**
         * tinkerPatch
         */
        private String  oldApk;
        private String  newApk;
        private String  outFolder;
        private File    signFile;
        private String  keypass;
        private String  storealias;
        private String  storepass;
        private boolean ignoreWarning;
        private boolean allowLoaderInAnyDex;
        private boolean removeLoaderForAllDex;
        private boolean isProtectedApp;
        private boolean isComponentHotplugSupported;
        private boolean useSign;

        /**
         * tinkerPatch.dex
         */
        private ArrayList<String> dexFilePattern;
        private ArrayList<String> dexLoaderPattern;
        private ArrayList<String> dexIgnoreWarningLoaderPattern;

        private String                  dexMode;
        /**
         * tinkerPatch.lib
         */
        private ArrayList<String>       soFilePattern;
        /**
         * tinkerPath.resource pattern
         */
        private ArrayList<String>       resourceFilePattern;
        /**
         * tinkerPath.resource ignoreChange
         */
        private ArrayList<String>       resourceIgnoreChangePattern;
        /**
         * tinkerPatch.resource ignoreChangeWarning
         */
        private ArrayList<String>       resourceIgnoreChangeWarningPattern;
        /**
         * tinkerPath.resource largeModSize
         */
        private  int                    largeModSize;
        /**
         * tinkerPath.buildConfig applyResourceMapping
         */
        private boolean                 useApplyResource;
        /**
         * tinkerPatch.packageConfig
         */
        private HashMap<String, String> configFields;
        /**
         * tinkerPatch.sevenZip
         */
        private String                  sevenZipPath;

        /**
         * tinkerPatch ark
         */
        private String arkHotPatchPath;
        private String arkHotPatchName;


        public Builder() {
        }

        public Builder setOldApk(String oldApk) {
            this.oldApk = oldApk;
            return this;
        }

        public Builder setNewApk(String newApk) {
            this.newApk = newApk;
            return this;
        }

        public Builder setSoFilePattern(ArrayList<String> soFilePattern) {
            this.soFilePattern = soFilePattern;
            return this;
        }

        public Builder setResourceFilePattern(ArrayList<String> resourceFilePattern) {
            this.resourceFilePattern = resourceFilePattern;
            return this;
        }

        public Builder setResourceIgnoreChangePattern(ArrayList<String> resourceIgnoreChangePattern) {
            this.resourceIgnoreChangePattern = resourceIgnoreChangePattern;
            return this;
        }

        public Builder setResourceIgnoreChangeWarningPattern(ArrayList<String> resourceIgnoreChangeWarningPattern) {
            this.resourceIgnoreChangeWarningPattern = resourceIgnoreChangeWarningPattern;
            return this;
        }

        public Builder setResourceLargeModSize(int largeModSize) {
            this.largeModSize = largeModSize;
            return this;
        }

        public Builder setUseApplyResource(boolean useApplyResource) {
            this.useApplyResource = useApplyResource;
            return this;
        }

        public Builder setDexFilePattern(ArrayList<String> dexFilePattern) {
            this.dexFilePattern = dexFilePattern;
            return this;
        }

        public Builder setOutBuilder(String outFolder) {
            this.outFolder = outFolder;
            return this;
        }

        public Builder setSignFile(File signFile) {
            this.signFile = signFile;
            return this;
        }

        public Builder setKeypass(String keypass) {
            this.keypass = keypass;
            return this;
        }

        public Builder setStorealias(String storealias) {
            this.storealias = storealias;
            return this;
        }

        public Builder setStorepass(String storepass) {
            this.storepass = storepass;
            return this;
        }

        public Builder setIgnoreWarning(boolean ignoreWarning) {
            this.ignoreWarning = ignoreWarning;
            return this;
        }

        public Builder setAllowLoaderInAnyDex(boolean allowLoaderInAnyDex) {
            this.allowLoaderInAnyDex = allowLoaderInAnyDex;
            return this;
        }

        public Builder setRemoveLoaderForAllDex(boolean removeLoaderForAllDex){
            this.removeLoaderForAllDex = removeLoaderForAllDex;
            return this;
        }

        public Builder setIsProtectedApp(boolean isProtectedApp) {
            this.isProtectedApp = isProtectedApp;
            return this;
        }

        public Builder setIsComponentHotplugSupported(boolean isComponentHotplugSupported) {
            this.isComponentHotplugSupported = isComponentHotplugSupported;
            return this;
        }

        public Builder setDexLoaderPattern(ArrayList<String> dexLoaderPattern) {
            this.dexLoaderPattern = dexLoaderPattern;
            return this;
        }

        public Builder setDexIgnoreWarningLoaderPattern(ArrayList<String> loader) {
            this.dexIgnoreWarningLoaderPattern = loader;
            return this;
        }

        public Builder setDexMode(String dexMode) {
            this.dexMode = dexMode;
            return this;
        }

        public Builder setConfigFields(HashMap<String, String> configFields) {
            this.configFields = configFields;
            return this;
        }

        public Builder setSevenZipPath(String sevenZipPath) {
            this.sevenZipPath = sevenZipPath;
            return this;
        }

        public Builder setUseSign(boolean useSign) {
            this.useSign = useSign;
            return this;
        }

        public Builder setArkHotPath(String path) {
            this.arkHotPatchPath = path;
            return this;
        }

        public Builder setArkHotName(String name) {
            this.arkHotPatchName = name;
            return this;
        }

        public InputParam create() {
            return new InputParam(
                    oldApk,
                    newApk,
                    outFolder,
                    signFile,
                    keypass,
                    storealias,
                    storepass,
                    ignoreWarning,
                    allowLoaderInAnyDex,
                    removeLoaderForAllDex,
                    isProtectedApp,
                    isComponentHotplugSupported,
                    useSign,
                    dexFilePattern,
                    dexLoaderPattern,
                    dexIgnoreWarningLoaderPattern,
                    dexMode,
                    soFilePattern,
                    resourceFilePattern,
                    resourceIgnoreChangePattern,
                    resourceIgnoreChangeWarningPattern,
                    largeModSize,
                    useApplyResource,
                    configFields,
                    sevenZipPath,
                    arkHotPatchPath,
                    arkHotPatchName
            );
        }
    }
}
