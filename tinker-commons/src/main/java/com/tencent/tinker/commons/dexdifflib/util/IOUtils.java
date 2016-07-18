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

package com.tencent.tinker.commons.dexdifflib.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public class IOUtils {
    private IOUtils() {
    }

    public static void closeQuietly(Closeable target) {
        if (target != null) {
            try {
                target.close();
            } catch (Throwable thr) {
            }
        }
    }

    public static int skipIndeed(InputStream in, int length) {
        if (length <= 0) {
            return 0;
        }

        int totalBytesSkipped = 0;

        try {
            while (true) {
                long bytesSkipped = in.skip(length - totalBytesSkipped);
                if (bytesSkipped <= 0) {
                    break;
                }
                totalBytesSkipped += bytesSkipped;
            }
            return totalBytesSkipped;
        } catch (IOException e) {
            return -1;
        }
    }
}
