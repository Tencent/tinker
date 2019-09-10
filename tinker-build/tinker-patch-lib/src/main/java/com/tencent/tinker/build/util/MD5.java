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

package com.tencent.tinker.build.util;

import com.tencent.tinker.commons.util.IOHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * <div class="en">MD5 digest wrapper</div>
 * <div class="zh_CN">MD5计算封装</div>
 *
 * @author zhaoyuan
 */
public final class MD5 {

    private MD5() {

    }

    /**
     * get md5 string for input buffer
     *
     * @param buffer data to be calculated
     * @return md5 result in string format
     */
    public static String getMessageDigest(byte[] buffer) {
        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        try {
            MessageDigest mdTemp = MessageDigest.getInstance("MD5");
            mdTemp.update(buffer);
            byte[] md = mdTemp.digest();
            int j = md.length;
            char[] str = new char[j * 2];
            int k = 0;
            for (int i = 0; i < j; i++) {
                byte byte0 = md[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * get md5 in byte array
     *
     * @param buffer data to be calculated
     * @return md5 result in byte array format
     */
    public static byte[] getRawDigest(byte[] buffer) {
        try {
            MessageDigest mdTemp = MessageDigest.getInstance("MD5");
            mdTemp.update(buffer);
            return mdTemp.digest();

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the md5 for inputStream.
     * This method cost less memory. It read bufLen bytes from the FileInputStream once.
     *
     * @param is
     * @param bufLen bytes number read from the stream once.
     *               The less bufLen is the more times getMD5() method takes. Also the less bufLen is the less memory cost.
     */
    public static String getMD5(final InputStream is, final int bufLen, final int offset, final int length) {
        return getMD5ExtendBytes(is, bufLen, offset, length, null);
    }

    /**
     * Get the md5 for inputStream.
     * This method cost less memory. It read bufLen bytes from the FileInputStream once.
     *
     * @param is
     * @param bufLen
     * @param offset
     * @param length
     * @param extendBytes extend bytes which would be add to the end of input stream for MD5 calculating
     */
    public static String getMD5ExtendBytes(final InputStream is, final int bufLen, final int offset, final int length, final byte[] extendBytes) {
        if (is == null || bufLen <= 0 || offset < 0 || length <= 0) {
            return null;
        }
        try {
            long skipLen = is.skip(offset);
            if (skipLen < offset) { // reach the end
                return null;
            }

            MessageDigest md = MessageDigest.getInstance("MD5");
            StringBuilder md5Str = new StringBuilder(32);

            byte[] buf = new byte[bufLen];
            int readCount = 0;
            int totalRead = 0;
            while ((readCount = is.read(buf)) != -1 && totalRead < length) {
                if (totalRead + readCount <= length) {
                    md.update(buf, 0, readCount);
                    totalRead += readCount;

                } else {
                    md.update(buf, 0, length - totalRead);
                    totalRead = length;
                }
            }
            if (extendBytes != null && extendBytes.length > 0) {
                md.update(extendBytes);
            }

            byte[] hashValue = md.digest();

            for (int i = 0; i < hashValue.length; i++) {
                md5Str.append(Integer.toString((hashValue[i] & 0xff) + 0x100, 16).substring(1));
            }
            return md5Str.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the md5 for inputStream.
     * This method cost less memory. It read bufLen bytes from the FileInputStream once.
     *
     * @param is
     * @param bufLen bytes number read from the stream once.
     *               The less bufLen is the more times getMD5() method takes. Also the less bufLen is the less memory cost.
     */
    public static String getMD5(final InputStream is, final int bufLen) {
        if (is == null || bufLen <= 0) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            StringBuilder md5Str = new StringBuilder(32);

            byte[] buf = new byte[bufLen];
            int readCount = 0;
            while ((readCount = is.read(buf)) != -1) {
                md.update(buf, 0, readCount);
            }

            byte[] hashValue = md.digest();

            for (int i = 0; i < hashValue.length; i++) {
                md5Str.append(Integer.toString((hashValue[i] & 0xff) + 0x100, 16).substring(1));
            }
            return md5Str.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the md5 for the file, using less memory.
     */
    public static String getMD5(final String file) {
        if (file == null) {
            return null;
        }

        File f = new File(file);
        if (f.exists()) {
            return getMD5(f, 1024 * 100);
        }
        return null;
    }

    /**
     * Get the md5 for the file, using less memory.
     */
    public static String getMD5(final File file) {
        return getMD5(file, 1024 * 100);
    }

    /**
     * Get the md5 for the file. call getMD5(FileInputStream is, int bufLen) inside.
     *
     * @param file
     * @param bufLen bytes number read from the stream once.
     *               The less bufLen is the more times getMD5() method takes. Also the less bufLen cost less memory.
     */
    public static String getMD5(final File file, final int bufLen) {
        if (file == null || bufLen <= 0 || !file.exists()) {
            return null;
        }

        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            String md5 = MD5.getMD5(fin, (int) (bufLen <= file.length() ? bufLen : file.length()));
            fin.close();
            return md5;

        } catch (Exception e) {
            return null;

        } finally {
            IOHelper.closeQuietly(fin);
        }
    }

    /**
     * Get the md5 for the file, using less memory.
     */
    public static String getMD5(final String file, final int offset, final int length) {
        if (file == null) {
            return null;
        }

        File f = new File(file);
        if (f.exists()) {
            return getMD5(f, offset, length);
        }
        return null;
    }

    /**
     * Get the md5 for the file, using less memory.
     */
    public static String getMD5(final File file, final int offset, final int length) {
        if (file == null || !file.exists() || offset < 0 || length <= 0) {
            return null;
        }

        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            String md5 = MD5.getMD5(fin, 1024 * 100, offset, length);
            fin.close();
            return md5;

        } catch (Exception e) {
            return null;

        } finally {
            IOHelper.closeQuietly(fin);
        }
    }

    public static String getMD5ExtendBytes(final File file, final int offset, final int length, byte[] extend) {
        if (file == null || !file.exists() || offset < 0 || length <= 0) {
            return null;
        }

        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            String md5 = MD5.getMD5ExtendBytes(fin, 1024 * 100, offset, length, extend);
            fin.close();
            return md5;

        } catch (Exception e) {
            return null;

        } finally {
            IOHelper.closeQuietly(fin);
        }
    }
}
