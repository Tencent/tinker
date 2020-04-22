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

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by zhangshaowen on 16/3/17.
 */


public class TinkerLog {
    private static final String TAG = "Tinker.TinkerLog";
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);

    private static final List<Object[]> sPendingLogs = Collections.synchronizedList(new ArrayList<Object[]>());

    private static final TinkerLogImp debugLog = new TinkerLogImp() {

        @Override
        public void v(final String tag, final String format, final Object... params) {
            String log = (params == null || params.length == 0) ? format : String.format(format, params);
            android.util.Log.v(tag, log);
        }

        @Override
        public void i(final String tag, final String format, final Object... params) {
            String log = (params == null || params.length == 0) ? format : String.format(format, params);
            android.util.Log.i(tag, log);

        }

        @Override
        public void d(final String tag, final String format, final Object... params) {
            String log = (params == null || params.length == 0) ? format : String.format(format, params);
            android.util.Log.d(tag, log);
        }

        @Override
        public void w(final String tag, final String format, final Object... params) {
            String log = (params == null || params.length == 0) ? format : String.format(format, params);
            android.util.Log.w(tag, log);
        }

        @Override
        public void e(final String tag, final String format, final Object... params) {
            String log = (params == null || params.length == 0) ? format : String.format(format, params);
            android.util.Log.e(tag, log);
        }

        @Override
        public void printErrStackTrace(String tag, Throwable tr, String format, Object... params) {
            String log = (params == null || params.length == 0) ? format : String.format(format, params);
            if (log == null) {
                log = "";
            }
            log += "  " + android.util.Log.getStackTraceString(tr);
            android.util.Log.e(tag, log);
        }
    };

    // Guarded by itself.
    private static final TinkerLogImp[] tinkerLogImpRef = {debugLog};

    public static void setTinkerLogImp(TinkerLogImp imp) {
        synchronized (tinkerLogImpRef) {
            tinkerLogImpRef[0] = imp;
        }
    }

    public static TinkerLogImp getImpl() {
        synchronized (tinkerLogImpRef) {
            return tinkerLogImpRef[0];
        }
    }

    public static void printPendingLogs() {
        TinkerLogImp imp;
        synchronized (tinkerLogImpRef) {
            imp = tinkerLogImpRef[0];
        }
        if (sPendingLogs.isEmpty()) {
            return;
        }
        if (imp == null) {
            return;
        }
        for (Object[] log : sPendingLogs) {
            final int level = (int) log[0];
            final String tag = (String) log[1];
            final String msg = (String) log[2];
            switch (level) {
                case Log.VERBOSE: {
                    imp.v(tag, msg);
                    break;
                }
                case Log.DEBUG: {
                    imp.d(tag, msg);
                    break;
                }
                case Log.INFO: {
                    imp.i(tag, msg);
                    break;
                }
                case Log.WARN: {
                    imp.w(tag, msg);
                    break;
                }
                case Log.ERROR: {
                    imp.e(tag, msg);
                    break;
                }
                default: {
                    break;
                }
            }
        }
        sPendingLogs.clear();
    }

    public static void v(final String tag, final String fmt, final Object... obj) {
        final TinkerLogImp imp = getImpl();
        if (imp != null) {
            imp.v(tag, fmt, obj);
        } else {
            final String msg = (obj != null && obj.length > 0) ? String.format(fmt, obj) : fmt;
            final String prefix = "[PendingLog@" + SIMPLE_DATE_FORMAT.format(new Date()) + "] ";
            sPendingLogs.add(new Object[] {Log.VERBOSE, tag, prefix + msg});
        }
    }

    public static void e(final String tag, final String fmt, final Object... obj) {
        final TinkerLogImp imp = getImpl();
        if (imp != null) {
            imp.e(tag, fmt, obj);
        } else {
            final String msg = (obj != null && obj.length > 0) ? String.format(fmt, obj) : fmt;
            final String prefix = "[PendingLog@" + SIMPLE_DATE_FORMAT.format(new Date()) + "] ";
            sPendingLogs.add(new Object[] {Log.ERROR, tag, prefix + msg});
        }
    }

    public static void w(final String tag, final String fmt, final Object... obj) {
        final TinkerLogImp imp = getImpl();
        if (imp != null) {
            imp.w(tag, fmt, obj);
        } else {
            final String msg = (obj != null && obj.length > 0) ? String.format(fmt, obj) : fmt;
            final String prefix = "[PendingLog@" + SIMPLE_DATE_FORMAT.format(new Date()) + "] ";
            sPendingLogs.add(new Object[] {Log.WARN, tag, prefix + msg});
        }
    }

    public static void i(final String tag, final String fmt, final Object... obj) {
        final TinkerLogImp imp = getImpl();
        if (imp != null) {
            imp.i(tag, fmt, obj);
        } else {
            final String msg = (obj != null && obj.length > 0) ? String.format(fmt, obj) : fmt;
            final String prefix = "[PendingLog@" + SIMPLE_DATE_FORMAT.format(new Date()) + "] ";
            sPendingLogs.add(new Object[] {Log.INFO, tag, prefix + msg});
        }
    }

    public static void d(final String tag, final String fmt, final Object... obj) {
        final TinkerLogImp imp = getImpl();
        if (imp != null) {
            imp.d(tag, fmt, obj);
        } else {
            final String msg = (obj != null && obj.length > 0) ? String.format(fmt, obj) : fmt;
            final String prefix = "[PendingLog@" + SIMPLE_DATE_FORMAT.format(new Date()) + "] ";
            sPendingLogs.add(new Object[] {Log.DEBUG, tag, prefix + msg});
        }
    }

    public static void printErrStackTrace(String tag, Throwable tr, final String format, final Object... obj) {
        final TinkerLogImp imp = getImpl();
        if (imp != null) {
            imp.printErrStackTrace(tag, tr, format, obj);
        } else {
            String msg = (obj == null || obj.length == 0) ? format : String.format(format, obj);
            msg += "  " + android.util.Log.getStackTraceString(tr);
            final String prefix = "[PendingLog@" + SIMPLE_DATE_FORMAT.format(new Date()) + "] ";
            sPendingLogs.add(new Object[] {Log.ERROR, tag, prefix + msg});
        }
    }

    public interface TinkerLogImp {

        void v(final String tag, final String fmt, final Object... obj);

        void i(final String tag, final String fmt, final Object... obj);

        void w(final String tag, final String fmt, final Object... obj);

        void d(final String tag, final String fmt, final Object... obj);

        void e(final String tag, final String fmt, final Object... obj);

        void printErrStackTrace(String tag, Throwable tr, final String format, final Object... obj);

    }

}
