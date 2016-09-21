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

/**
 * Created by zhangshaowen on 16/3/17.
 */


public class TinkerLog {
    private static final String TAG = "Tinker.TinkerLog";
    private static TinkerLogImp debugLog = new TinkerLogImp() {

        @Override
        public void v(final String tag, final String msg, final Object... obj) {
            String log = obj == null ? msg : String.format(msg, obj);
            android.util.Log.v(tag, log);
        }

        @Override
        public void i(final String tag, final String msg, final Object... obj) {
            String log = obj == null ? msg : String.format(msg, obj);
            android.util.Log.i(tag, log);

        }

        @Override
        public void d(final String tag, final String msg, final Object... obj) {
            String log = obj == null ? msg : String.format(msg, obj);
            android.util.Log.d(tag, log);
        }

        @Override
        public void w(final String tag, final String msg, final Object... obj) {
            String log = obj == null ? msg : String.format(msg, obj);
            android.util.Log.w(tag, log);
        }

        @Override
        public void e(final String tag, final String msg, final Object... obj) {
            String log = obj == null ? msg : String.format(msg, obj);
            android.util.Log.e(tag, log);
        }

        @Override
        public void printErrStackTrace(String tag, Throwable tr, String format, Object... obj) {
            String log = obj == null ? format : String.format(format, obj);
            if (log == null) {
                log = "";
            }
            log += "  " + android.util.Log.getStackTraceString(tr);
            android.util.Log.e(tag, log);
        }
    };
    private static TinkerLogImp tinkerLogImp = debugLog;

    public static void setTinkerLogImp(TinkerLogImp imp) {
        tinkerLogImp = imp;
    }

    public static TinkerLogImp getImpl() {
        return tinkerLogImp;
    }

    public static void v(final String tag, final String msg, final Object... obj) {
        if (tinkerLogImp != null) {
            tinkerLogImp.v(tag, msg, obj);
        }
    }

    public static void e(final String tag, final String msg, final Object... obj) {
        if (tinkerLogImp != null) {
            tinkerLogImp.e(tag, msg, obj);
        }
    }

    public static void w(final String tag, final String msg, final Object... obj) {
        if (tinkerLogImp != null) {
            tinkerLogImp.w(tag, msg, obj);
        }
    }

    public static void i(final String tag, final String msg, final Object... obj) {
        if (tinkerLogImp != null) {
            tinkerLogImp.i(tag, msg, obj);
        }
    }

    public static void d(final String tag, final String msg, final Object... obj) {
        if (tinkerLogImp != null) {
            tinkerLogImp.d(tag, msg, obj);
        }
    }

    public static void printErrStackTrace(String tag, Throwable tr, final String format, final Object... obj) {
        if (tinkerLogImp != null) {
            tinkerLogImp.printErrStackTrace(tag, tr, format, obj);
        }
    }

    public interface TinkerLogImp {

        void v(final String tag, final String msg, final Object... obj);

        void i(final String tag, final String msg, final Object... obj);

        void w(final String tag, final String msg, final Object... obj);

        void d(final String tag, final String msg, final Object... obj);

        void e(final String tag, final String msg, final Object... obj);

        void printErrStackTrace(String tag, Throwable tr, final String format, final Object... obj);

    }

}
