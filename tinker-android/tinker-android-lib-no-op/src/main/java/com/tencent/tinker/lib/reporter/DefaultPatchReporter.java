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

package com.tencent.tinker.lib.reporter;


import android.content.Context;
import android.content.Intent;

import com.tencent.tinker.loader.shareutil.SharePatchInfo;

import java.io.File;
import java.util.List;

/**
 * Created by zhangshaowen on 16/3/14.
 */
public class DefaultPatchReporter implements PatchReporter {
    protected final Context context;

    public DefaultPatchReporter(Context context) {
        this.context = context;
        // Ignored.
    }

    @Override
    public void onPatchServiceStart(Intent intent) {
        // Ignored.
    }

    @Override
    public void onPatchPackageCheckFail(File patchFile, int errorCode) {
        // Ignored.
    }

    @Override
    public void onPatchVersionCheckFail(File patchFile, SharePatchInfo oldPatchInfo, String patchFileVersion) {
        // Ignored.
    }

    @Override
    public void onPatchTypeExtractFail(File patchFile, File extractTo, String filename, int fileType) {
        // Ignored.
    }

    @Override
    public void onPatchDexOptFail(File patchFile, List<File> dexFiles, Throwable t) {
        // Ignored.
    }

    @Override
    public void onPatchResult(File patchFile, boolean success, long cost) {
        // Ignored.
    }

    @Override
    public void onPatchInfoCorrupted(File patchFile, String oldVersion, String newVersion) {
        // Ignored.
    }

    @Override
    public void onPatchException(File patchFile, Throwable e) {
        // Ignored.
    }
}
