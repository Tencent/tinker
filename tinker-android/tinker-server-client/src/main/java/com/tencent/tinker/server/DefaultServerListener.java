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

import com.tencent.tinker.lib.tinker.Tinker;

import java.io.File;

/**
 * Created by zhangshaowen on 16/5/30.
 */
public class DefaultServerListener implements TinkerServerListener {
    @Override
    public TinkerCheckResult checkTinkerUpdate(Context context, Tinker tinker) {
        return null;
    }

    @Override
    public File downloadTinkerPatch(Context context, Tinker tinker, TinkerCheckResult checkResult) {
        return null;
    }

    @Override
    public void onPatchDownloaded(Context context, Tinker tinker, File patchFile) {

    }
}
