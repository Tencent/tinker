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

import android.content.Context;
import android.content.Intent;

import com.tencent.tinker.lib.reporter.DefaultPatchReporter;

import java.io.File;

import tinker.sample.android.util.UpgradePatchRetry;

/**
 * optional, you can just use DefaultPatchReporter
 * Created by shwenzhang on 16/4/8.
 */
public class SamplePatchReporter extends DefaultPatchReporter {
    public SamplePatchReporter(Context context) {
        super(context);
    }

    @Override
    public void onPatchServiceStart(Intent intent) {
        super.onPatchServiceStart(intent);
        UpgradePatchRetry.getInstance(context).onPatchServiceStart(intent);
    }

    @Override
    public void onPatchResult(File patchFile, boolean success, long cost, boolean isUpgradePatch) {
        super.onPatchResult(patchFile, success, cost, isUpgradePatch);
        UpgradePatchRetry.getInstance(context).onPatchServiceResult(isUpgradePatch);

    }
}
