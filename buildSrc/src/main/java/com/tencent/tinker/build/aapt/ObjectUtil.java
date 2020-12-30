/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.tencent.tinker.build.aapt;

/**
 * reflect the object property and invoke the method
 *
 * @author Dandelion
 * @since 2008-04-??
 */
public final class ObjectUtil {

    private ObjectUtil() {
    }

    /**
     * when object is null return blank,when the object is not null it return object;
     *
     * @param object
     * @return Object
     */
    public static Object nullToBlank(Object object) {
        if (object == null) {
            return StringUtil.BLANK;
        }
        return object;
    }

    /**
     * equal
     *
     * @param a
     * @param b
     * @return boolean
     */
    public static boolean equal(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    /**
     * field name to method name
     *
     * @param methodPrefix
     * @param fieldName
     * @return methodName
     */
    public static String fieldNameToMethodName(String methodPrefix, String fieldName) {
        return fieldNameToMethodName(methodPrefix, fieldName, false);
    }

    /**
     * field name to method name
     *
     * @param methodPrefix
     * @param fieldName
     * @param ignoreFirstLetterCase
     * @return methodName
     */
    public static String fieldNameToMethodName(String methodPrefix, String fieldName, boolean ignoreFirstLetterCase) {
        String methodName = null;
        if (fieldName != null && fieldName.length() > 0) {
            if (ignoreFirstLetterCase) {
                methodName = methodPrefix + fieldName;
            } else {
                methodName = methodPrefix + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            }
        } else {
            methodName = methodPrefix;
        }
        return methodName;
    }

    /**
     * method name to field name
     *
     * @param methodPrefix
     * @param methodName
     * @return fieldName
     */
    public static String methodNameToFieldName(String methodPrefix, String methodName) {
        return methodNameToFieldName(methodPrefix, methodName, false);
    }

    /**
     * method name to field name
     *
     * @param methodPrefix
     * @param methodName
     * @param ignoreFirstLetterCase
     * @return fieldName
     */
    public static String methodNameToFieldName(String methodPrefix, String methodName, boolean ignoreFirstLetterCase) {
        String fieldName = null;
        if (methodName != null && methodName.length() > methodPrefix.length()) {
            int front = methodPrefix.length();
            if (ignoreFirstLetterCase) {
                fieldName = methodName.substring(front, front + 1) + methodName.substring(front + 1);
            } else {
                fieldName = methodName.substring(front, front + 1).toLowerCase() + methodName.substring(front + 1);
            }
        }
        return fieldName;
    }
}