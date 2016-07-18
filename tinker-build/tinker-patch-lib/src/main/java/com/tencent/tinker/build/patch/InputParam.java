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

package com.tencent.tinker.build.patch;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by shwenzhang on 1/9/16.
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
    public final boolean useSign;

    /**
     * tinkerPatch.dex
     */
    public final ArrayList<String>       dexFilePattern;
    public final ArrayList<String>       dexLoaderPattern;
    public final String                  dexMode;
    /**
     * tinkerPatch.lib
     */
    public final ArrayList<String>       soFilePattern;
    /**
     * tinkerPatch.packageConfig
     */
    public final HashMap<String, String> configFields;
    /**
     * tinkerPatch.sevenZip
     */
    public final String                  sevenZipPath;

    private InputParam(
        String oldApk,
        String newApk,
        String outFolder,
        File signFile,
        String keypass,
        String storealias,
        String storepass,
        boolean ignoreWarning,
        boolean useSign,

        ArrayList<String> dexFilePattern,
        ArrayList<String> dexLoaderPattern,
        String dexMode,
        ArrayList<String> soFilePattern,

        HashMap<String, String> configFields,

        String sevenZipPath
    ) {
        this.oldApk = oldApk;
        this.newApk = newApk;
        this.outFolder = outFolder;
        this.signFile = signFile;
        this.keypass = keypass;
        this.storealias = storealias;
        this.storepass = storepass;
        this.ignoreWarning = ignoreWarning;
        this.useSign = useSign;

        this.dexFilePattern = dexFilePattern;
        this.dexLoaderPattern = dexLoaderPattern;
        this.dexMode = dexMode;

        this.soFilePattern = soFilePattern;

        this.configFields = configFields;

        this.sevenZipPath = sevenZipPath;
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
        private boolean useSign;

        /**
         * tinkerPatch.dex
         */
        private ArrayList<String>       dexFilePattern;
        private ArrayList<String>       dexLoaderPattern;
        private String                  dexMode;
        /**
         * tinkerPatch.lib
         */
        private ArrayList<String>       soFilePattern;
        /**
         * tinkerPatch.packageConfig
         */
        private HashMap<String, String> configFields;
        /**
         * tinkerPatch.sevenZip
         */
        private String                  sevenZipPath;


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

        public Builder setDexLoaderPattern(ArrayList<String> dexLoaderPattern) {
            this.dexLoaderPattern = dexLoaderPattern;
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
                useSign,
                dexFilePattern,
                dexLoaderPattern,
                dexMode,
                soFilePattern,
                configFields,
                sevenZipPath
            );
        }
    }
}
