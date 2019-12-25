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

import java.io.File;

/**
 * Created by zhangshaowen on 16/3/10.
 */
public interface LoadReporter {
    void onLoadPatchListenerReceiveFail(File patchFile, int errorCode);

    void onLoadPatchVersionChanged(String oldVersion, String newVersion, File patchDirectoryFile, String currentPatchName);

    void onLoadInterpret(int type, Throwable e);

    void onLoadResult(File patchDirectory, int loadCode, long cost);

    void onLoadException(Throwable e, int errorCode);

    void onLoadFileNotFound(File file, int fileType, boolean isDirectory);

    void onLoadFileMd5Mismatch(File file, int fileType);

    void onLoadPatchInfoCorrupted(String oldVersion, String newVersion, File patchInfoFile);

    void onLoadPackageCheckFail(File patchFile, int errorCode);
}
