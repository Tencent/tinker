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

package com.tencent.tinker.build.info;

import com.tencent.tinker.build.apkparser.AndroidParser;
import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;
import com.tencent.tinker.commons.util.IOHelper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Properties;

/**
 * Created by zhangshaowen on 16/3/8.
 */
public class PatchInfoGen {
    private final Configuration config;
    private final File          packageInfoFile;

    public PatchInfoGen(Configuration config) {
        this.config = config;
        packageInfoFile = new File(config.mTempResultDir + File.separator + "assets" + File.separator + TypedValue.PACKAGE_META_FILE);
    }

    private void addTinkerID() throws IOException, ParseException {
        if (!config.mPackageFields.containsKey(TypedValue.TINKER_ID)) {
            AndroidParser oldAndroidManifest = AndroidParser.getAndroidManifest(config.mOldApkFile);
            String tinkerID = oldAndroidManifest.metaDatas.get(TypedValue.TINKER_ID);

            if (tinkerID == null) {
                throw new TinkerPatchException("can't find TINKER_ID from the old apk manifest file, it must be set!");
            }
            config.mPackageFields.put(TypedValue.TINKER_ID, tinkerID);
        }

        if (!config.mPackageFields.containsKey(TypedValue.NEW_TINKER_ID)) {
            AndroidParser newAndroidManifest = AndroidParser.getAndroidManifest(config.mNewApkFile);
            String tinkerID = newAndroidManifest.metaDatas.get(TypedValue.TINKER_ID);

            if (tinkerID == null) {
                throw new TinkerPatchException("can't find TINKER_ID from the new apk manifest file, it must be set!");
            }
            config.mPackageFields.put(TypedValue.NEW_TINKER_ID, tinkerID);
        }
    }

    private void addProtectedAppFlag() {
        // If user happens to specify a value with this key, just override it for logic correctness.
        config.mPackageFields.put(TypedValue.PKGMETA_KEY_IS_PROTECTED_APP, config.mIsProtectedApp ? "1" : "0");
    }

    public void gen() throws Exception {
        addTinkerID();
        addProtectedAppFlag();

        Properties newProperties = new Properties();
        for (String key : config.mPackageFields.keySet()) {
            newProperties.put(key, config.mPackageFields.get(key));
        }

        String comment = "base package config field";
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(packageInfoFile, false));
            newProperties.store(os, comment);
        } finally {
            IOHelper.closeQuietly(os);
        }
    }
}
