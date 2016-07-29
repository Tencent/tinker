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

package tinker.sample.android.reporter;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.tencent.tinker.lib.listener.DefaultPatchListener;
import com.tencent.tinker.lib.service.TinkerPatchService;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.tinker.TinkerLoadResult;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;

import tinker.sample.android.crash.SampleUncaughtExceptionHandler;
import tinker.sample.android.util.UpgradePatchRetry;
import tinker.sample.android.util.Utils;

/**
 * Created by shwenzhang on 16/4/30.
 * optional, you can just use DefaultPatchListener
 * we can check whatever you want whether we actually send a patch request
 * such as we can check rom space or apk channel
 */
public class SamplePatchListener extends DefaultPatchListener {
    private static final String TAG = "SamplePatchListener";

    protected static final long NEW_PATCH_RESTRICTION_SPACE_SIZE_MIN = ShareTinkerInternals.isVmArt() ? 80 * 1024 * 1024 : 50 * 1024 * 1024;
    protected static final long OLD_PATCH_RESTRICTION_SPACE_SIZE_MIN = ShareTinkerInternals.isVmArt() ? 50 * 1024 * 1024 : 25 * 1024 * 1024;

    private final int maxMemory;

    public SamplePatchListener(Context context) {
        super(context);
        maxMemory = ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
        TinkerLog.i(TAG, "application maxMemory:" + maxMemory);
    }

    /**
     * optional, copy sdcard patch file to /data/data first
     * copy sdcard
     * @param path
     * @param isUpgrade
     * @return
     */
    @Override
    public int onPatchReceived(String path, boolean isUpgrade) {
        int returnCode = this.patchCheck(path, isUpgrade);

        if(returnCode == ShareConstants.ERROR_PATCH_OK) {
            path = UpgradePatchRetry.getInstance(context).onPatchListenerPass(path, isUpgrade);
            TinkerPatchService.runPatchService(this.context, path, isUpgrade);
        } else {
            Tinker.with(this.context).getLoadReporter().onLoadPatchListenerReceiveFail(new File(path), returnCode, isUpgrade);
        }

        return returnCode;
    }

    /**
     * because we use the defaultCheckPatchReceived method
     * the error code define by myself should after {@code ShareConstants.ERROR_RECOVER_INSERVICE
     *
     * @param path
     * @param newPatch
     * @return
     */
    @Override
    public int patchCheck(String path, boolean isUpgrade) {
        int returnCode = super.patchCheck(path, isUpgrade);

        if (returnCode == ShareConstants.ERROR_PATCH_OK) {
            if (isUpgrade) {
                returnCode = Utils.checkForPatchRecover(NEW_PATCH_RESTRICTION_SPACE_SIZE_MIN, maxMemory);
            } else {
                returnCode = Utils.checkForPatchRecover(OLD_PATCH_RESTRICTION_SPACE_SIZE_MIN, maxMemory);
            }
        }

        if (returnCode == ShareConstants.ERROR_PATCH_OK) {
            String patchMd5 = SharePatchFileUtil.getMD5(new File(path));
            SharedPreferences sp = context.getSharedPreferences(ShareConstants.TINKER_PREFERENCE_CONFIG, Context.MODE_MULTI_PROCESS);
            //optional, only disable this patch file with md5
            int fastCrashCount = sp.getInt(patchMd5, 0);
            if (fastCrashCount >= SampleUncaughtExceptionHandler.MAX_CRASH_COUNT) {
                returnCode = Utils.ERROR_PATCH_CRASH_LIMIT;
            } else {
                //for upgrade patch, version must be not the same
                //for repair patch, we won't has the tinker load flag
                Tinker tinker = Tinker.with(context);

                if (tinker.isTinkerLoaded()) {
                    TinkerLoadResult tinkerLoadResult = tinker.getTinkerLoadResultIfPresent();
                    if (tinkerLoadResult != null) {
                        String currentVersion = tinkerLoadResult.currentVersion;
                        if (patchMd5.equals(currentVersion)) {
                            returnCode = Utils.ERROR_PATCH_ALREADY_APPLY;
                        }
                    }
                }
            }
        }

        return returnCode;
    }
}
