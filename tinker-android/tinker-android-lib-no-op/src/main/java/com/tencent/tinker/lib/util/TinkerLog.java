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

import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

/**
 * Created by zhangshaowen on 16/3/17.
 *
 * DEPRECATED. Use {@link com.tencent.tinker.loader.shareutil.ShareTinkerLog} instead.
 */
@Deprecated
public class TinkerLog {
    private static final String TAG = "Tinker.TinkerLog";

    public static void setTinkerLogImp(TinkerLogImp imp) {
        ShareTinkerLog.setTinkerLogImp(imp);
    }

    public static ShareTinkerLog.TinkerLogImp getImpl() {
        return ShareTinkerLog.getImpl();
    }

    public static void v(final String tag, final String msg, final Object... obj) {
        ShareTinkerLog.v(tag, msg, obj);
    }

    public static void e(final String tag, final String msg, final Object... obj) {
        ShareTinkerLog.v(tag, msg, obj);
    }

    public static void w(final String tag, final String msg, final Object... obj) {
        ShareTinkerLog.v(tag, msg, obj);
    }

    public static void i(final String tag, final String msg, final Object... obj) {
        ShareTinkerLog.v(tag, msg, obj);
    }

    public static void d(final String tag, final String msg, final Object... obj) {
        ShareTinkerLog.v(tag, msg, obj);
    }

    public static void printErrStackTrace(String tag, Throwable tr, final String format, final Object... obj) {
        ShareTinkerLog.printErrStackTrace(tag, tr, format, obj);
    }

    public static void printPendingLogs() {
        ShareTinkerLog.printPendingLogs();
    }

    public interface TinkerLogImp extends ShareTinkerLog.TinkerLogImp {}
}
