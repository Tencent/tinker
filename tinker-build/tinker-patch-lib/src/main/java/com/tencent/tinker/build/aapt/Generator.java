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

import com.tencent.tinker.commons.util.IOHelper;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public final class Generator {

    private static final char[] CHARACTERS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};
    private static final String FONT_FAMILY_TIMES_NEW_ROMAN = "Times New Roman";

    /**
     * md5 file
     *
     * @param fullFilename
     * @return String
     */
    public static String md5File(String fullFilename) {
        String result = null;
        if (fullFilename != null) {
            InputStream is = null;
            try {
                is = new BufferedInputStream(new FileInputStream(fullFilename));
                result = md5File(is);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                IOHelper.closeQuietly(is);
            }
        }
        return result;
    }

    /**
     * md5 file
     *
     * @param inputStream
     * @return String
     */
    public static String md5File(final InputStream inputStream) {
        String result = null;
        if (inputStream != null) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] buffer = new byte[Constant.Capacity.BYTES_PER_KB];
                int readCount = 0;
                while ((readCount = inputStream.read(buffer, 0, buffer.length)) != -1) {
                    md.update(buffer, 0, readCount);
                }
                result = StringUtil.byteToHexString(md.digest());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                IOHelper.closeQuietly(inputStream);
            }
        }
        return result;
    }
}
