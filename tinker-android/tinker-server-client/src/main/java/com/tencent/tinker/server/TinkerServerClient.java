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
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;
import com.tencent.tinker.server.model.DataFetcher;

/**
 * Created by zhangshaowen on 16/5/29.
 */
public class TinkerServerClient {
    private static final String TAG = "Tinker.ServerClient";

    private static final String TINKER_LAST_CHECK = "tinker_last_check";

    private static final long DEFAULT_CHECK_INTERVAL = 1 * 3600 * 1000;
    private static final long NEVER_CHECK_UPDATE     = -1;

    private long checkInterval = DEFAULT_CHECK_INTERVAL;

    private Tinker          tinker;
    private Context         context;
    private TinkerClientImp tinkerClientImp;

    public TinkerServerClient(Context context, Tinker tinker, String appKey, String appVersion, Boolean debug) {
        this.tinker = tinker;
        this.context = context;
        this.tinkerClientImp = TinkerClientImp.init(context, appKey, appVersion, debug);
    }

    public void checkTinkerUpdate() {
        //check SharePreferences also
        if (!tinker.isTinkerEnabled() || !ShareTinkerInternals.isTinkerEnableWithSharedPreferences(context)) {
            TinkerLog.e(TAG, "tinker is disable, just return");
            return;
        }
        SharedPreferences sp = context.getSharedPreferences(ShareConstants.TINKER_SHARE_PREFERENCE_CONFIG, Context.MODE_MULTI_PROCESS);
        long last = sp.getLong(TINKER_LAST_CHECK, 0);
        if (last == NEVER_CHECK_UPDATE) {
            TinkerLog.i(TAG, "tinker update is disabled, with never check flag!");
            return;
        }
        long interval = System.currentTimeMillis() - last;
        if (tinkerClientImp.isDebug() || interval > checkInterval) {
            sp.edit().putLong(TINKER_LAST_CHECK, System.currentTimeMillis()).commit();
            tinkerClientImp.sync(context, new DataFetcher.DataCallback<String>() {
                @Override
                public void onDataReady(String data) {
                    TinkerLog.i(TAG, "tinker sync onDataReady:" + data);
                }

                @Override
                public void onLoadFailed(Exception e) {
                    TinkerLog.i(TAG, "tinker sync onLoadFailed:" + e);
                }
            });
        }

        return;

    }

    public void setCheckIntervalByHours(int hours) {
        if (hours <= 0 || hours > 24) {
            throw new TinkerRuntimeException("hours must be between 0 and 24");
        }
        checkInterval = hours * 3600 * 1000;
    }

}
