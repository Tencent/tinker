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

package com.tencent.tinker.build.util;

import com.tencent.tinker.build.info.InfoWriter;
import com.tencent.tinker.build.patch.Configuration;

import java.io.File;
import java.io.IOException;

/**
 * Created by zhangshaowen on 16/4/7.
 */
public class Logger {
    private static InfoWriter logWriter;

    public static void initLogger(Configuration config) throws IOException {
        String logPath = config.mOutFolder + File.separator + TypedValue.FILE_LOG;
        logWriter = new InfoWriter(config, logPath);
    }

    public static void closeLogger() {
        logWriter.close();
    }

    public static void d(final String msg) {
        Logger.d(msg, new Object[]{});
    }

    public static void d(final String format, final Object... obj) {

        String log = obj.length == 0 ? format : String.format(format, obj);
        if (log == null) {
            log = "";
        }
        //add \n
        System.out.printf(log + "\n");

        logWriter.writeLineToInfoFile(log);
    }

    public static void e(final String msg) {
        Logger.e(msg, new Object[]{});
    }

    public static void e(final String format, final Object... obj) {
        String log = obj.length == 0 ? format : String.format(format, obj);
        if (log == null) {
            log = "";
        }
        //add \n
        System.err.printf(log + "\n");
        logWriter.writeLineToInfoFile(log);

    }

}
