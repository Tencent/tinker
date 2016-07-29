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

package com.tencent.tinker.build.dexpatcher.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by tomystang on 2016/7/29.
 */
public final class DexPatcherLogManager {
    private static Map<String, LogWritter> logWrittersMap = new HashMap<>();

    public static void setLogWritter(String scopeName, LogWritter logWritter) {
        DexPatcherLogManager.logWrittersMap.put(scopeName, logWritter);
    }

    public static void writeLog(String scopeName, String message) {
        LogWritter logWritter = logWrittersMap.get(scopeName);
        if (logWritter != null) {
            logWritter.write(message);
        }
    }

    public static void writeLog(String scopeName, String format, Object...vals) {
        LogWritter logWritter = logWrittersMap.get(scopeName);
        if (logWritter != null) {
            logWritter.write(
                    String.format(
                            "%s",
                            (vals != null && vals.length > 0 ? String.format(format, vals) : format)
                    )
            );
        }
    }

    public interface LogWritter {
        void write(String message);
    }
}
