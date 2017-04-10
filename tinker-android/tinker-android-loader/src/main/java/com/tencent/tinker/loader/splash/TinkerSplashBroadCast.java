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

package com.tencent.tinker.loader.splash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

/**
 * Created by zhangshaowen on 17/3/2.
 */

public class TinkerSplashBroadCast extends BroadcastReceiver {
    private static final String TAG = "Tinker.SplashBroadCast";

    private static final long WAIT_MAIN_ACTIVITY_TIME = 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        long costTime = 0;
        if (intent != null) {
            final long begin = ShareIntentUtil.getLongExtra(intent, ShareTinkerInternals.INTENT_SPLASH_BEGIN, 0);
            if (begin != 0) {
                costTime = SystemClock.elapsedRealtime() - begin;
                Log.i(TAG, "quick launch splash broadcast cost: " + costTime + "ms");
            }
        }
        final long waitTime = costTime >= WAIT_MAIN_ACTIVITY_TIME ? 0 : WAIT_MAIN_ACTIVITY_TIME - costTime;
        Log.i(TAG, "receive splash end broadcast, try to dismiss activity after " + waitTime + "ms");

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "receive splash end broadcast, dismiss activity now");
                TinkerOTASplashActivity.dismissActivityAndKillProcess();
            }
        }, waitTime);
    }
}
