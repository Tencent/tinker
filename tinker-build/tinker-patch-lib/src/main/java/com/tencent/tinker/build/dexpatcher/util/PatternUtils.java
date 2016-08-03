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

/**
 * Created by tangyinsheng on 2016/4/8.
 */
public class PatternUtils {

    public static String dotClassNamePatternToDescriptorRegEx(String dotPattern) {
        if (dotPattern.startsWith("L") && dotPattern.endsWith(";") || dotPattern.startsWith("[")) {
            return dotPattern.replace('.', '/').replace("[", "\\[");
        }

        String descriptor = dotPattern.replace('.', '/');

        StringBuilder sb = new StringBuilder();

        int i;
        for (i = dotPattern.length() - 1; i >= 1; i -= 2) {
            char ch = dotPattern.charAt(i);
            char prevCh = dotPattern.charAt(i - 1);
            if (prevCh == '[' && ch == ']') {
                sb.append("\\[");
            } else {
                break;
            }
        }

        descriptor = descriptor.substring(0, i + 1);

        if ("void".equals(descriptor)) {
            descriptor = "V";
            sb.append(descriptor);
        } else if ("boolean".equals(descriptor)) {
            descriptor = "Z";
            sb.append(descriptor);
        } else if ("byte".equals(descriptor)) {
            descriptor = "B";
            sb.append(descriptor);
        } else if ("short".equals(descriptor)) {
            descriptor = "S";
            sb.append(descriptor);
        } else if ("char".equals(descriptor)) {
            descriptor = "C";
            sb.append(descriptor);
        } else if ("int".equals(descriptor)) {
            descriptor = "I";
            sb.append(descriptor);
        } else if ("long".equals(descriptor)) {
            descriptor = "J";
            sb.append(descriptor);
        } else if ("float".equals(descriptor)) {
            descriptor = "F";
            sb.append(descriptor);
        } else if ("double".equals(descriptor)) {
            descriptor = "D";
            sb.append(descriptor);
        } else {
            sb.append('L').append(descriptor);

            if (!descriptor.endsWith(";")) {
                sb.append(';');
            }
        }

        String regEx = sb.toString();
        regEx = regEx.replace("*", ".*");
        regEx = regEx.replace("?", ".?");
        regEx = regEx.replace("$", "\\$");
        regEx = '^' + regEx + '$';

        return regEx;
    }
}
