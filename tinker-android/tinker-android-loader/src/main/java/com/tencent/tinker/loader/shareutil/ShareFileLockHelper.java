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

package com.tencent.tinker.loader.shareutil;

import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;

/**
 * Created by zhangshaowen on 16/6/3.
 */
public class ShareFileLockHelper implements Closeable {
    private static final String TAG = "FileLockHelper";

    public static final int MAX_LOCK_ATTEMPTS   = 3;
    public static final int LOCK_WAIT_EACH_TIME = 10;
    private final FileOutputStream outputStream;
    private final FileLock         fileLock;

    private ShareFileLockHelper(File lockFile) throws IOException {
        outputStream = new FileOutputStream(lockFile);

        int numAttempts = 0;
        boolean isGetLockSuccess;
        FileLock localFileLock = null;
        //just wait twice,
        Exception saveException = null;
        while (numAttempts < MAX_LOCK_ATTEMPTS) {
            numAttempts++;
            try {
                localFileLock = outputStream.getChannel().lock();
                isGetLockSuccess = (localFileLock != null);
                if (isGetLockSuccess) {
                    break;
                }
                //it can just sleep 0, afraid of cpu scheduling
                Thread.sleep(LOCK_WAIT_EACH_TIME);

            } catch (Exception e) {
//                e.printStackTrace();
                saveException = e;
                Log.e(TAG, "getInfoLock Thread failed time:" + LOCK_WAIT_EACH_TIME);
            }
        }

        if (localFileLock == null) {
            throw new IOException("Tinker Exception:FileLockHelper lock file failed: " + lockFile.getAbsolutePath(), saveException);
        }
        fileLock = localFileLock;
    }

    public static ShareFileLockHelper getFileLock(File lockFile) throws IOException {
        return new ShareFileLockHelper(lockFile);
    }

    @Override
    public void close() throws IOException {
        try {
            if (fileLock != null) {
                fileLock.release();
            }
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }
}
