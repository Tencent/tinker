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

package com.tencent.tinker.loader;

import android.content.Context;
import android.util.Log;

import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by zhangshaowen on 16/12/1.
 */

public class TinkerUncaughtHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "Tinker.UncaughtHandler";


    private final File crashFile;
    private final Context context;
    private final Thread.UncaughtExceptionHandler ueh;

    public TinkerUncaughtHandler(Context context) {
        this.context = context;
        ueh = Thread.getDefaultUncaughtExceptionHandler();
        crashFile = SharePatchFileUtil.getPatchLastCrashFile(context);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        ShareTinkerLog.e(TAG, "TinkerUncaughtHandler catch exception:" + Log.getStackTraceString(ex));
        ueh.uncaughtException(thread, ex);

        if (crashFile != null) {
            Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();

            //only catch real uncaught Exception
            if (handler instanceof TinkerUncaughtHandler) {
                File parentFile = crashFile.getParentFile();
                if (!parentFile.exists() && !parentFile.mkdirs()) {
                    ShareTinkerLog.e(TAG, "print crash file error: create directory fail!");
                    return;
                }
                PrintWriter pw = null;
                try {
                    pw = new PrintWriter(new FileWriter(crashFile, false));
                    pw.println("process:" + ShareTinkerInternals.getProcessName(this.context));
                    pw.println(ShareTinkerInternals.getExceptionCauseString(ex));
                } catch (IOException e) {
                    //ignore
                    ShareTinkerLog.e(TAG, "print crash file error:" + Log.getStackTraceString(e));
                } finally {
                    SharePatchFileUtil.closeQuietly(pw);
                }
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }
    }
}
