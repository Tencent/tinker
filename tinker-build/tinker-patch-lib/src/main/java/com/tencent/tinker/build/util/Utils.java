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

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Created by sun on 1/9/16.
 */
public class Utils {
    public static boolean isPresent(String str) {
        return str != null && str.length() > 0;
    }

    public static boolean isBlank(String str) {
        return !isPresent(str);
    }

    public static boolean isPresent(Iterator iterator) {
        return iterator != null && iterator.hasNext();
    }

    public static boolean isBlank(Iterator iterator) {
        return !isPresent(iterator);
    }

    public static String convetToPatternString(String input) {
        //convert \\.
        if (input.contains(".")) {
            input = input.replaceAll("\\.", "\\\\.");
        }
        //convert ？to .
        if (input.contains("?")) {
            input = input.replaceAll("\\?", "\\.");
        }
        //convert * to.*
        if (input.contains("*")) {
            input = input.replace("*", ".*");
        }
        return input;
    }

    public static void cleanDir(File dir) {
        if (dir.exists()) {
            FileOperation.deleteDir(dir);
            dir.mkdirs();
        }
    }

    public static boolean isStringMatchesPatterns(String str, Collection<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(str).matches()) {
                return true;
            }
        }
        return false;
    }

    public static <T> String collectionToString(Collection<T> collection) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean isFirstElement = true;
        for (T element : collection) {
            if (isFirstElement) {
                isFirstElement = false;
            } else {
                sb.append(',');
            }
            sb.append(element);
        }
        sb.append('}');
        return sb.toString();
    }
}
