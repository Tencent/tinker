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

package tinker.sample.android.Log;

import android.util.Log;

import com.tencent.tinker.lib.util.TinkerLog;

/**
 * Created by zhangshaowen on 16/6/3.
 */
public class MyLogImp implements TinkerLog.TinkerLogImp {
    private static final String TAG = "Tinker.MyLogImp";

    public static final int LEVEL_VERBOSE = 0;
    public static final int LEVEL_DEBUG   = 1;
    public static final int LEVEL_INFO    = 2;
    public static final int LEVEL_WARNING = 3;
    public static final int LEVEL_ERROR   = 4;
    public static final int LEVEL_NONE    = 5;
    private static int level = LEVEL_VERBOSE;

    public static int getLogLevel() {
        return level;
    }

    public static void setLevel(final int level) {
        MyLogImp.level = level;
        android.util.Log.w(TAG, "new log level: " + level);

    }

    @Override
    public void v(String s, String s1, Object... objects) {
        if (level <= LEVEL_VERBOSE) {
            final String log = objects == null ? s1 : String.format(s1, objects);
            android.util.Log.v(s, log);
        }
    }

    @Override
    public void i(String s, String s1, Object... objects) {
        if (level <= LEVEL_INFO) {
            final String log = objects == null ? s1 : String.format(s1, objects);
            android.util.Log.i(s, log);
        }
    }

    @Override
    public void w(String s, String s1, Object... objects) {
        if (level <= LEVEL_WARNING) {
            final String log = objects == null ? s1 : String.format(s1, objects);
            android.util.Log.w(s, log);
        }
    }

    @Override
    public void d(String s, String s1, Object... objects) {
        if (level <= LEVEL_DEBUG) {
            final String log = objects == null ? s1 : String.format(s1, objects);
            android.util.Log.d(s, log);
        }
    }

    @Override
    public void e(String s, String s1, Object... objects) {
        if (level <= LEVEL_ERROR) {
            final String log = objects == null ? s1 : String.format(s1, objects);
            android.util.Log.e(s, log);
        }
    }

    @Override
    public void printErrStackTrace(String s, Throwable throwable, String s1, Object... objects) {
        String log = objects == null ? s1 : String.format(s1, objects);
        if (log == null) {
            log = "";
        }
        log = log + "  " + Log.getStackTraceString(throwable);
        android.util.Log.e(s, log);
    }
}
