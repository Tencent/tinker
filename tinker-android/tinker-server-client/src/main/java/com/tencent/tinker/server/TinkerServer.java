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

package com.tencent.tinker.server;

import android.content.Context;
import android.content.SharedPreferences;

import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;

import java.io.File;

/**
 * Created by shwenzhang on 16/5/29.
 */
public class TinkerServer {
    private static final String TAG = "TinkerServer";

    private static final String TINKER_LAST_CHECK = "tinker_last_check";

    private static final long DEFAULT_CHECK_INTERVAL = 24 * 3600 * 1000;
    private static final long NEVER_CHECK_UPDATE     = -1;

    private long checkInterval = DEFAULT_CHECK_INTERVAL;
    private Tinker               tinker;
    private Context              context;
    private TinkerServerListener serverListener;

    public TinkerServer(Context context, Tinker tinker, TinkerServerListener listener) {
        this.tinker = tinker;
        this.context = context;
        this.serverListener = listener;
    }

    public TinkerServer(Context context, Tinker tinker) {
        this.tinker = tinker;
        this.context = context;
        this.serverListener = new DefaultServerListener();
    }

    public boolean checkTinkerUpdate() {
        SharedPreferences sp = context.getSharedPreferences(ShareConstants.TINKER_PREFERENCE_CONFIG, Context.MODE_MULTI_PROCESS);
        long last = sp.getLong(TINKER_LAST_CHECK, 0);
        if (last == NEVER_CHECK_UPDATE) {
            TinkerLog.i(TAG, "TinkerUpdate is disabled");
            return false;
        }
        long interval = System.currentTimeMillis() - last;
        if (interval > checkInterval) {
            sp.edit().putLong(TINKER_LAST_CHECK, System.currentTimeMillis()).commit();
            return checkTinkerUpdateWithInternet();
        }

        return false;

    }

    public void disableTinkerUpdate() {
        SharedPreferences sp = context.getSharedPreferences(ShareConstants.TINKER_PREFERENCE_CONFIG, Context.MODE_MULTI_PROCESS);
        sp.edit().putLong(TINKER_LAST_CHECK, NEVER_CHECK_UPDATE).commit();
        TinkerLog.i(TAG, "disableTinkerUpdate");
    }

    public void setCheckIntervalByHours(int hours) {
        if (hours <= 0) {
            throw new TinkerRuntimeException("hours must be greater than 0");
        }
        checkInterval = hours * 3600 * 1000;
    }

    private boolean checkTinkerUpdateWithInternet() {
        TinkerCheckResult checkResult = serverListener.checkTinkerUpdate(context, tinker);
        if (checkResult.hasUpdate) {
            File patchFile = serverListener.downloadTinkerPatch(context, tinker, checkResult);
            if (patchFile.isFile() && patchFile.exists()) {
                if (SharePatchFileUtil.verifyFileMd5(patchFile, checkResult.patchMd5)) {
                    serverListener.onPatchDownloaded(context, tinker, patchFile);
                }
            }
        }
        return checkResult.hasUpdate;
    }

}
