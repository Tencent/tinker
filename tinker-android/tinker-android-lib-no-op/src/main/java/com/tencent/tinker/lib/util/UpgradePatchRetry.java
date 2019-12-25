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

package com.tencent.tinker.lib.util;

import android.content.Context;
import android.content.Intent;

/**
 * Created by zhangshaowen on 16/7/3.
 */
public class UpgradePatchRetry {
    private static final String TAG = "Tinker.UpgradePatchRetry";

    private static UpgradePatchRetry sInstance;

    public UpgradePatchRetry(Context context) {
        TinkerLog.e(TAG, "[-] Ignore this invocation since I'm no-op version.");
    }

    public static UpgradePatchRetry getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new UpgradePatchRetry(context);
        }
        return sInstance;
    }

    public void setRetryEnable(boolean enable) {
        TinkerLog.e(TAG, "[-] Ignore this invocation since I'm no-op version.");
    }

    public void setMaxRetryCount(int count) {
        TinkerLog.e(TAG, "[-] Ignore this invocation since I'm no-op version.");
    }

    public boolean onPatchRetryLoad() {
        TinkerLog.e(TAG, "[-] Ignore this invocation since I'm no-op version.");
        return false;
    }

    public void onPatchServiceStart(Intent intent) {
        TinkerLog.e(TAG, "[-] Ignore this invocation since I'm no-op version.");
    }

    public boolean onPatchListenerCheck(String md5) {
        return true;
    }

    public boolean onPatchResetMaxCheck(String md5) {
        return true;
    }

    public void onPatchServiceResult() {
        TinkerLog.e(TAG, "[-] Ignore this invocation since I'm no-op version.");
    }
}
