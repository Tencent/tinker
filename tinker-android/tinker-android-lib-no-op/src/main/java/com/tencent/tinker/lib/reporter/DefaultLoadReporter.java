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

import java.io.File;

/**
 * Created by zhangshaowen on 16/3/10.
 * the default implement for LoadReporter
 * you can extent it for your own work
 * all is running in the process which loading the patch
 */
public class DefaultLoadReporter implements LoadReporter {
    protected final Context context;

    public DefaultLoadReporter(Context context) {
        this.context = context;
        // Ignored.
    }

    @Override
    public void onLoadPatchListenerReceiveFail(File patchFile, int errorCode) {
        // Ignored.
    }

    @Override
    public void onLoadPatchVersionChanged(String oldVersion, String newVersion, File patchDirectoryFile, String currentPatchName) {
        // Ignored.
    }

    @Override
    public void onLoadInterpret(int type, Throwable e) {
        // Ignored.
    }

    @Override
    public void onLoadFileNotFound(File file, int fileType, boolean isDirectory) {
        // Ignored.
    }

    @Override
    public void onLoadFileMd5Mismatch(File file, int fileType) {
        // Ignored.
    }

    @Override
    public void onLoadPatchInfoCorrupted(String oldVersion, String newVersion, File patchInfoFile) {
        // Ignored.
    }

    @Override
    public void onLoadResult(File patchDirectory, int loadCode, long cost) {
        // Ignored.
    }

    @Override
    public void onLoadException(Throwable e, int errorCode) {
        // Ignored.
    }

    @Override
    public void onLoadPackageCheckFail(File patchFile, int errorCode) {
        // Ignored.
    }

    public void checkAndCleanPatch() {
        // Ignored.
    }

    public boolean retryPatch() {
        return false;
    }
}
