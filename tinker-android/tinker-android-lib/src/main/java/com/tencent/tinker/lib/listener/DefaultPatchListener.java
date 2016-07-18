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

package com.tencent.tinker.lib.listener;

import android.content.Context;

import com.tencent.tinker.lib.service.TinkerPatchService;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerServiceInternals;
import com.tencent.tinker.loader.shareutil.ShareConstants;

import java.io.File;

/**
 * Created by shwenzhang on 16/3/14.
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
     * @param isUpgrade
     * @return
     */
    @Override
    public int onPatchReceived(String path, boolean isUpgrade) {

        int returnCode = patchCheck(path, isUpgrade);

        if (returnCode == ShareConstants.ERROR_PATCH_OK) {
            TinkerPatchService.runPatchService(context, path, isUpgrade);
        } else {
            Tinker.with(context).getLoadReporter().onLoadPatchListenerReceiveFail(new File(path), returnCode, isUpgrade);
        }
        return returnCode;

    }

    protected int patchCheck(String path, boolean isUpgrade) {
        Tinker manager = Tinker.with(context);

        if (!manager.isEnabled()) {
            return ShareConstants.ERROR_PATCH_DISABLE;
        }
        File file = new File(path);

        if (!file.exists()) {
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
        return ShareConstants.ERROR_PATCH_OK;
    }

}
