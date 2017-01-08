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

package com.tencent.tinker.lib.service;


import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.tinker.TinkerLoadResult;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.lib.util.TinkerServiceInternals;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;

import java.io.File;

/**
 * Created by zhangshaowen on 16/3/19.
 */
public class DefaultTinkerResultService extends AbstractResultService {
    private static final String TAG = "Tinker.DefaultTinkerResultService";

    /**
     * we may want to use the new patch just now!!
     *
     * @param result
     */
    @Override
    public void onPatchResult(PatchResult result) {
        if (result == null) {
            TinkerLog.e(TAG, "DefaultTinkerResultService received null result!!!!");
            return;
        }
        TinkerLog.i(TAG, "DefaultTinkerResultService received a result:%s ", result.toString());

        //first, we want to kill the recover process
        TinkerServiceInternals.killTinkerPatchServiceProcess(getApplicationContext());

        // if success and newPatch, it is nice to delete the raw file, and restart at once
        // only main process can load an upgrade patch!
        if (result.isSuccess) {
            deleteRawPatchFile(new File(result.rawPatchFilePath));
            if (checkIfNeedKill(result)) {
                android.os.Process.killProcess(android.os.Process.myPid());
            } else {
                TinkerLog.i(TAG, "I have already install the newly patch version!");
            }
        }
    }

    /**
     * don't delete tinker version file
     * @param rawFile
     */
    public void deleteRawPatchFile(File rawFile) {
        if (!SharePatchFileUtil.isLegalFile(rawFile)) {
            return;
        }
        TinkerLog.w(TAG, "deleteRawPatchFile rawFile path: %s", rawFile.getPath());
        String fileName = rawFile.getName();
        if (!fileName.startsWith(ShareConstants.PATCH_BASE_NAME)
            || !fileName.endsWith(ShareConstants.PATCH_SUFFIX)) {
            SharePatchFileUtil.safeDeleteFile(rawFile);
            return;
        }
        File parentFile = rawFile.getParentFile();
        if (!parentFile.getName().startsWith(ShareConstants.PATCH_BASE_NAME)) {
            SharePatchFileUtil.safeDeleteFile(rawFile);
        } else {
            File grandFile = parentFile.getParentFile();
            if (!grandFile.getName().equals(ShareConstants.PATCH_DIRECTORY_NAME)) {
                SharePatchFileUtil.safeDeleteFile(rawFile);
            }
        }

    }

    public boolean checkIfNeedKill(PatchResult result) {
        Tinker tinker = Tinker.with(getApplicationContext());
        if (tinker.isTinkerLoaded()) {
            TinkerLoadResult tinkerLoadResult = tinker.getTinkerLoadResultIfPresent();
            if (tinkerLoadResult != null) {
                String currentVersion = tinkerLoadResult.currentVersion;
                if (result.patchVersion != null && result.patchVersion.equals(currentVersion)) {
                    return false;
                }
            }
        }
        return true;
    }


}
