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

package com.tencent.tinker.lib.listener;

import android.content.Context;

import android.os.Bundle;
import com.tencent.tinker.lib.service.TinkerPatchService;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerServiceInternals;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;

/**
 * Created by zhangshaowen on 16/3/14.
 */
public class DefaultPatchListener implements PatchListener {
    protected final Context context;

    public DefaultPatchListener(Context context) {
        this.context = context;
    }

    /**
     * when we receive a patch, what would we do?
     * you can overwrite it
     *
     * @param path
     * @return
     */
    @Override
    public int onPatchReceived(String path, Bundle customBundle) {

        int returnCode = patchCheck(path);

        if (returnCode == ShareConstants.ERROR_PATCH_OK) {
            TinkerPatchService.runPatchService(context, path, customBundle);
        } else {
            Tinker.with(context).getLoadReporter().onLoadPatchListenerReceiveFail(new File(path), returnCode);
        }
        return returnCode;
    }

    protected int patchCheck(String path) {
        Tinker manager = Tinker.with(context);
        //check SharePreferences also
        if (!manager.isTinkerEnabled() || !ShareTinkerInternals.isTinkerEnableWithSharedPreferences(context)) {
            return ShareConstants.ERROR_PATCH_DISABLE;
        }
        File file = new File(path);

        if (!SharePatchFileUtil.isLegalFile(file)) {
            return ShareConstants.ERROR_PATCH_NOTEXIST;
        }

        //patch service can not send request
        if (manager.isPatchProcess()) {
            return ShareConstants.ERROR_PATCH_INSERVICE;
        }

        //if the patch service is running, pending
        if (TinkerServiceInternals.isTinkerPatchServiceRunning(context)) {
            return ShareConstants.ERROR_PATCH_RUNNING;
        }
        if (ShareTinkerInternals.isVmJit()) {
            return ShareConstants.ERROR_PATCH_JIT;
        }
        return ShareConstants.ERROR_PATCH_OK;
    }

}
