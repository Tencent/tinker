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
import com.tencent.tinker.build.util.TinkerPatchException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by zhangshaowen on 16/3/9.
 */
public class UniqueDexDiffDecoder extends DexDiffDecoder {
    private ArrayList<String> addedDexFiles;

    public UniqueDexDiffDecoder(Configuration config, String metaPath, String logPath) throws IOException {
        super(config, metaPath, logPath);
        addedDexFiles = new ArrayList<>();
    }

    @Override
    public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException {
        boolean added = super.patch(oldFile, newFile);
        if (added) {
            String name = newFile.getName();
            if (addedDexFiles.contains(name)) {
                throw new TinkerPatchException("illegal dex name, dex name should be unique, dex:" + name);
            } else {
                addedDexFiles.add(name);
            }
        }
        return added;
    }

}