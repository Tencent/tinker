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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import com.tencent.tinker.lib.util.TinkerLog;

/**
 * Created by zhangshaowen on 16/3/14.
 */
public abstract class AbstractResultService extends IntentService {
    private static final String TAG = "Tinker.AbstractResultService";

    public AbstractResultService() {
        super("TinkerResultService");
    }

    public static void runResultService(Context context, PatchResult result, String resultServiceClass) {
        TinkerLog.e(TAG, "[-] Ignore this invocation since I'm no-op version.");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        TinkerLog.e(TAG, "[-] Ignore this invocation since I'm no-op version.");
    }

    public abstract void onPatchResult(PatchResult result);
}
