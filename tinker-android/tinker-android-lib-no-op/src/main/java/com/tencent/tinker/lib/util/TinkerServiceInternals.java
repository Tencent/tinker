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

import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

/**
 * Created by zhangshaowen on 16/3/10.
 */
public class TinkerServiceInternals extends ShareTinkerInternals {
    private static final String TAG = "Tinker.ServiceInternals";

    public static void killTinkerPatchServiceProcess(Context context) {
        TinkerLog.e(TAG, "[-] Ignore this invocation since I'm no-op version.");
    }

    public static boolean isTinkerPatchServiceRunning(Context context) {
        return false;
    }


    public static String getTinkerPatchServiceName(final Context context) {
        return null;
    }

    public static boolean isInTinkerPatchServiceProcess(Context context) {
        return false;
    }
}
