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

package com.tencent.tinker.bsdiff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class BSUtil {

    // JBDiff extensions by Stefan.Liebig@compeople.de:
    //
    // - introduced a HEADER_SIZE constant here

    /**
     * Length of the diff file header.
     */
    public static final int HEADER_SIZE = 32;
    public static final int BUFFER_SIZE = 8192;


    /**
     * Read from input stream and fill the given buffer from the given offset up
     * to length len.
     */
    public static final boolean readFromStream(InputStream in, byte[] buf, int offset, int len) throws IOException {

        int totalBytesRead = 0;
        while (totalBytesRead < len) {
            int bytesRead = in.read(buf, offset + totalBytesRead, len - totalBytesRead);
            if (bytesRead < 0) {
                return false;
            }
            totalBytesRead += bytesRead;
        }
        return true;
    }

    /**
     * input stream to byte
     * @param in InputStream
     * @return byte[]
     * @throws IOException
     */
    public static byte[] inputStreamToByte(InputStream in) throws IOException {

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] data = new byte[BUFFER_SIZE];
        int count = -1;
        while ((count = in.read(data, 0, BUFFER_SIZE)) != -1) {
            outStream.write(data, 0, count);
        }

        data = null;
        return outStream.toByteArray();
    }
}