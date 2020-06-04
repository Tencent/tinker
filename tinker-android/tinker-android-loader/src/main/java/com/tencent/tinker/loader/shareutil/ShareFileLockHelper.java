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

package com.tencent.tinker.loader.shareutil;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;

/**
 * Created by zhangshaowen on 16/6/3.
 */
public class ShareFileLockHelper implements Closeable {
    public static final int MAX_LOCK_ATTEMPTS   = 3;
    public static final int LOCK_WAIT_EACH_TIME = 10;
    private static final String TAG = "Tinker.FileLockHelper";
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

            } catch (Exception e) {
                saveException = e;
                ShareTinkerLog.e(TAG, "getInfoLock Thread failed time:" + LOCK_WAIT_EACH_TIME);
            }

            //it can just sleep 0, afraid of cpu scheduling
            try {
                Thread.sleep(LOCK_WAIT_EACH_TIME);
            } catch (Exception ignore) {
                ShareTinkerLog.e(TAG, "getInfoLock Thread sleep exception", ignore);
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
