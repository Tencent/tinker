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

package com.tencent.tinker.lib.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;

/**
 * Created by zhangshaowen on 16/3/14.
 */
public abstract class AbstractResultService extends IntentService {
    private static final String TAG = "AbstractResultService";

    private static final String RESULT_EXTRA = "result_extra";

    private static Class<? extends AbstractResultService> resultServiceClass = null;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     */
    public AbstractResultService() {
        super(AbstractResultService.class.getSimpleName());
    }

    public static void runResultService(Context context, PatchResult result) {
        if (resultServiceClass == null) {
            throw new TinkerRuntimeException("resultServiceClass is null.");
        }
        Intent intent = new Intent(context, resultServiceClass);
        intent.putExtra(RESULT_EXTRA, result);

        context.startService(intent);
    }

    public static void setResultServiceClass(Class<? extends AbstractResultService> serviceClass) {
        resultServiceClass = serviceClass;
        //try to load
        try {
            Class.forName(serviceClass.getName());
        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
        }

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            TinkerLog.d(TAG, "AbstractResultService received a null intent, ignoring.");
            return;
        }
        PatchResult result = (PatchResult) ShareIntentUtil.getSerializableExtra(intent, RESULT_EXTRA);

        onPatchResult(result);
    }

    public abstract void onPatchResult(PatchResult result);

}
