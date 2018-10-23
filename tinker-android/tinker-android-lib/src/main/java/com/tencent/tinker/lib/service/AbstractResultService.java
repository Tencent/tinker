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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import com.tencent.tinker.lib.util.TinkerJobIntentService;

import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.BuildConfig;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;

/**
 * Created by zhangshaowen on 16/3/14.
 */
public abstract class AbstractResultService extends TinkerJobIntentService {
    private static final String TAG = "Tinker.AbstractResultService";
    private static final int JOB_ID = 0xf3f4f5f6;

    private static final String RESULT_EXTRA = "result_extra";

    public static void runResultService(Context context, PatchResult result, String resultServiceClass) {
        if (resultServiceClass == null) {
            throw new TinkerRuntimeException("resultServiceClass is null.");
        }
        try {
            if (!TinkerJobIntentService.class.isAssignableFrom(Class.forName(resultServiceClass))) {
                throw new TinkerRuntimeException("on tinker version " + BuildConfig.TINKER_VERSION + " result service class must inherit from TinkerJobIntentService.");
            }
        } catch (ClassNotFoundException e) {
            throw new TinkerRuntimeException("cannot find result service class: " + resultServiceClass, e);
        }
        try {
            Intent intent = new Intent();
            intent.setClassName(context, resultServiceClass);
            intent.putExtra(RESULT_EXTRA, result);
            enqueueWork(context, new ComponentName(context, resultServiceClass), JOB_ID, intent);
        } catch (Throwable throwable) {
            TinkerLog.e(TAG, "run result service fail, exception:" + throwable);
        }
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        onHandleIntent(intent);
    }

    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            TinkerLog.e(TAG, "AbstractResultService received a null intent, ignoring.");
            return;
        }
        PatchResult result = (PatchResult) ShareIntentUtil.getSerializableExtra(intent, RESULT_EXTRA);

        onPatchResult(result);
    }

    public abstract void onPatchResult(PatchResult result);

}
