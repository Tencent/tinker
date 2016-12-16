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

package com.tencent.tinker.loader;

import android.util.Log;

import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;

import dalvik.system.DexFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by tangyinsheng on 2016/11/15.
 */

public final class TinkerParallelDexOptimizer {
    private static final String TAG = "ParallelDexOptimizer";

    /**
     * Optimize (trigger dexopt or dex2oat) dexes.
     *
     * @param dexFiles
     * @param optimizedDir
     * @param cb
     *
     * @return
     *  If all dexes are optimized successfully, return true. Otherwise return false.
     */
    public synchronized static boolean optimizeAll(File[] dexFiles, File optimizedDir, ResultCallback cb) {
        final AtomicInteger successCount = new AtomicInteger(0);
        return optimizeAllLocked(Arrays.asList(dexFiles), optimizedDir, successCount, cb);
    }

    /**
     * Optimize (trigger dexopt or dex2oat) dexes.
     *
     * @param dexFiles
     * @param optimizedDir
     * @param cb
     *
     * @return
     *  If all dexes are optimized successfully, return true. Otherwise return false.
     */
    public synchronized static boolean optimizeAll(Collection<File> dexFiles, File optimizedDir, ResultCallback cb) {
        final AtomicInteger successCount = new AtomicInteger(0);
        return optimizeAllLocked(dexFiles, optimizedDir, successCount, cb);
    }

    private static boolean optimizeAllLocked(Collection<File> dexFiles, File optimizedDir, AtomicInteger successCount, ResultCallback cb) {
        final CountDownLatch lauch = new CountDownLatch(dexFiles.size());
        final ExecutorService threadPool = Executors.newCachedThreadPool();
        long startTick = System.nanoTime();
        for (File dexFile : dexFiles) {
            OptimizeWorker worker = new OptimizeWorker(dexFile, optimizedDir, successCount, lauch, cb);
            threadPool.submit(worker);
        }
        try {
            lauch.await();
            long timeCost = (System.nanoTime() - startTick) / 1000000;
            if (successCount.get() == dexFiles.size()) {
                Log.i(TAG, "All dexes are optimized successfully, cost: " + timeCost + " ms.");
                return true;
            } else {
                Log.e(TAG, "Dexes optimizing failed, some dexes are not optimized.");
                return false;
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Dex optimizing was interrupted.", e);
            return false;
        } finally {
            threadPool.shutdown();
        }
    }

    public interface ResultCallback {
        void onStart(File dexFile, File optimizedDir);
        void onSuccess(File dexFile, File optimizedDir);
        void onFailed(File dexFile, File optimizedDir, Throwable thr);
    }

    private static class OptimizeWorker implements Runnable {
        private final File dexFile;
        private final File optimizedDir;
        private final AtomicInteger successCount;
        private final CountDownLatch waitingLauch;
        private final ResultCallback callback;

        OptimizeWorker(File dexFile, File optimizedDir, AtomicInteger successCount, CountDownLatch lauch, ResultCallback cb) {
            this.dexFile = dexFile;
            this.optimizedDir = optimizedDir;
            this.successCount = successCount;
            this.waitingLauch = lauch;
            this.callback = cb;
        }

        @Override
        public void run() {
            try {
                if (dexFile == null || !dexFile.exists()) {
                    if (callback != null) {
                        callback.onFailed(dexFile, optimizedDir,
                            new IOException("dex file " + dexFile.getAbsolutePath() + " is not exist!"));
                    }
                }
                if (callback != null) {
                    callback.onStart(dexFile, optimizedDir);
                }
                DexFile.loadDex(dexFile.getAbsolutePath(), SharePatchFileUtil.optimizedPathFor(this.dexFile, this.optimizedDir), 0);
                successCount.incrementAndGet();
                if (callback != null) {
                    callback.onSuccess(dexFile, optimizedDir);
                }
            } catch (final Exception e) {
                Log.e(TAG, "Failed to optimize dex: " + dexFile.getAbsolutePath(), e);
                if (callback != null) {
                    callback.onFailed(dexFile, optimizedDir, e);
                }
            } finally {
                this.waitingLauch.countDown();
            }
        }
    }
}
