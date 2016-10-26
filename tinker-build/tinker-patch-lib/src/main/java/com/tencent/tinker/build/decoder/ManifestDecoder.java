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


import com.tencent.tinker.build.apkparser.AndroidParser;
import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

/**
 * Created by zhangshaowen on 16/4/6.
 */

public class ManifestDecoder extends BaseDecoder {

    public ManifestDecoder(Configuration config) throws IOException {
        super(config);
    }

    @Override
    public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException {
        final boolean ignoreWarning = config.mIgnoreWarning;
        try {
            AndroidParser oldAndroidManifest = AndroidParser.getAndroidManifest(oldFile);
            AndroidParser newAndroidManifest = AndroidParser.getAndroidManifest(newFile);
            //check minSdkVersion
            int minSdkVersion = Integer.parseInt(oldAndroidManifest.apkMeta.getMinSdkVersion());

            if (minSdkVersion < TypedValue.ANDROID_40_API_LEVEL) {
                if (config.mDexRaw) {
                    if (ignoreWarning) {
                        //ignoreWarning, just log
                        Logger.e("Warning:ignoreWarning is true, but your old apk's minSdkVersion %d is below 14, you should set the dexMode to 'jar', otherwise, it will crash at some time", minSdkVersion);
                    } else {
                        Logger.e("Warning:ignoreWarning is false, but your old apk's minSdkVersion %d is below 14, you should set the dexMode to 'jar', otherwise, it will crash at some time", minSdkVersion);

                        throw new TinkerPatchException(
                            String.format("ignoreWarning is false, but your old apk's minSdkVersion %d is below 14, you should set the dexMode to 'jar', otherwise, it will crash at some time", minSdkVersion)
                        );
                    }
                }
            }

            //check whether there is any new Android Component
            List<String> oldAndroidComponent = oldAndroidManifest.getComponents();
            List<String> newAndroidComponent = newAndroidManifest.getComponents();

            for (String newComponentName : newAndroidComponent) {
                boolean found = false;
                for (String oldComponentName : oldAndroidComponent) {
                    if (newComponentName.equals(oldComponentName)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    if (ignoreWarning) {
                        Logger.e("Warning:ignoreWarning is true, but we found a new AndroidComponent %s, it will crash at some time", newComponentName);
                    } else {
                        Logger.e("Warning:ignoreWarning is false, but we found a new AndroidComponent %s, it will crash at some time", newComponentName);
                        throw new TinkerPatchException(
                            String.format("ignoreWarning is false, but we found a new AndroidComponent %s, it will crash at some time", newComponentName)
                        );
                    }
                }
            }

        } catch (ParseException e) {
            e.printStackTrace();
            throw new TinkerPatchException("parse android manifest error!");
        }
        return false;
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {

    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {

    }
}
