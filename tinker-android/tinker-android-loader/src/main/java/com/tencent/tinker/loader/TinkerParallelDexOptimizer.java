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

import android.os.Build;
import android.util.Log;

import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dalvik.system.DexFile;

/**
 * Created by tangyinsheng on 2016/11/15.
 */

public final class TinkerParallelDexOptimizer {
    private static final String TAG = "Tinker.ParallelDex";

    private static final int DEFAULT_THREAD_COUNT = 2;

    /**
     * Optimize (trigger dexopt or dex2oat) dexes.
     *
     * @param dexFiles
     * @param optimizedDir
     * @param cb
     * @return If all dexes are optimized successfully, return true. Otherwise return false.
     */
    public static boolean optimizeAll(Collection<File> dexFiles, File optimizedDir, ResultCallback cb) {
        return optimizeAll(dexFiles, optimizedDir, false, null, cb);
    }

    public static boolean optimizeAll(Collection<File> dexFiles, File optimizedDir, boolean useInterpretMode, String targetISA, ResultCallback cb) {
        final AtomicInteger successCount = new AtomicInteger(0);
        return optimizeAllLocked(dexFiles, optimizedDir, useInterpretMode, targetISA, successCount, cb, DEFAULT_THREAD_COUNT);
    }

    private synchronized static boolean optimizeAllLocked(Collection<File> dexFiles, File optimizedDir,
                                             boolean useInterpretMode, String targetISA, AtomicInteger successCount, ResultCallback cb, int threadCount) {
        final CountDownLatch latch = new CountDownLatch(dexFiles.size());
        final ExecutorService threadPool = Executors.newFixedThreadPool(threadCount);
        long startTick = System.nanoTime();
        ArrayList<File> sortList = new ArrayList<>(dexFiles);
        // sort input dexFiles with its file length
        Collections.sort(sortList, new Comparator<File>() {
            @Override
            public int compare(File lhs, File rhs) {
                long diffSize = lhs.length() - rhs.length();
                if (diffSize > 0) {
                    return 1;
                } else if (diffSize == 0) {
                    return 0;
                } else {
                    return -1;
                }
            }
        });
        Collections.reverse(sortList);
        for (File dexFile : sortList) {
            OptimizeWorker worker = new OptimizeWorker(dexFile, optimizedDir, useInterpretMode, targetISA, successCount, latch, cb);
            threadPool.submit(worker);
        }
        try {
            latch.await();
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

        void onSuccess(File dexFile, File optimizedDir, File optimizedFile);

        void onFailed(File dexFile, File optimizedDir, Throwable thr);
    }

    private static class OptimizeWorker implements Runnable {
        private static       String   targetISA           = null;

        private final File           dexFile;
        private final File           optimizedDir;
        private final boolean        useInterpretMode;
        private final AtomicInteger  successCount;
        private final CountDownLatch waitingLatch;
        private final ResultCallback callback;

        OptimizeWorker(File dexFile, File optimizedDir, boolean useInterpretMode, String targetISA, AtomicInteger successCount, CountDownLatch latch, ResultCallback cb) {
            this.dexFile = dexFile;
            this.optimizedDir = optimizedDir;
            this.useInterpretMode = useInterpretMode;
            this.successCount = successCount;
            this.waitingLatch = latch;
            this.callback = cb;
            this.targetISA = targetISA;
        }

        @Override
        public void run() {
            try {
                if (!SharePatchFileUtil.isLegalFile(dexFile)) {
                    if (callback != null) {
                        callback.onFailed(dexFile, optimizedDir,
                            new IOException("dex file " + dexFile.getAbsolutePath() + " is not exist!"));
                    }
                }
                if (callback != null) {
                    callback.onStart(dexFile, optimizedDir);
                }
                String optimizedPath = SharePatchFileUtil.optimizedPathFor(this.dexFile, this.optimizedDir);
                if (useInterpretMode) {
                    interpretDex2Oat(dexFile.getAbsolutePath(), optimizedPath);
                } else {
                    DexFile.loadDex(dexFile.getAbsolutePath(), optimizedPath, 0);
                }
                successCount.incrementAndGet();
                if (callback != null) {
                    callback.onSuccess(dexFile, optimizedDir, new File(optimizedPath));
                }
            } catch (final Throwable e) {
                Log.e(TAG, "Failed to optimize dex: " + dexFile.getAbsolutePath(), e);
                if (callback != null) {
                    callback.onFailed(dexFile, optimizedDir, e);
                }
            } finally {
                this.waitingLatch.countDown();
            }
        }

        private void interpretDex2Oat(String dexFilePath, String oatFilePath) throws IOException {

            final File oatFile = new File(oatFilePath);
            if (!oatFile.exists()) {
                oatFile.getParentFile().mkdirs();
            }

            final List<String> commandAndParams = new ArrayList<>();
            commandAndParams.add("dex2oat");
            commandAndParams.add("--runtime-arg");
            commandAndParams.add("-classpath");
            commandAndParams.add("--runtime-arg");
            commandAndParams.add("&");
            commandAndParams.add("--dex-file=" + dexFilePath);
            commandAndParams.add("--oat-file=" + oatFilePath);
            commandAndParams.add("--instruction-set=" + targetISA);
            if (Build.VERSION.SDK_INT > 25) {
                commandAndParams.add("--compiler-filter=quicken");
            } else {
                commandAndParams.add("--compiler-filter=interpret-only");
            }

            final ProcessBuilder pb = new ProcessBuilder(commandAndParams);
            pb.redirectErrorStream(true);
            final Process dex2oatProcess = pb.start();
            StreamConsumer.consumeInputStream(dex2oatProcess.getInputStream());
            StreamConsumer.consumeInputStream(dex2oatProcess.getErrorStream());
            try {
                final int ret = dex2oatProcess.waitFor();
                if (ret != 0) {
                    throw new IOException("dex2oat works unsuccessfully, exit code: " + ret);
                }
            } catch (InterruptedException e) {
                throw new IOException("dex2oat is interrupted, msg: " + e.getMessage(), e);
            }
        }
    }

    private static class StreamConsumer {
        static final Executor STREAM_CONSUMER = Executors.newSingleThreadExecutor();

        static void consumeInputStream(final InputStream is) {
            STREAM_CONSUMER.execute(new Runnable() {
                @Override
                public void run() {
                    if (is == null) {
                        return;
                    }
                    final byte[] buffer = new byte[256];
                    try {
                        while ((is.read(buffer)) > 0) {
                            // To satisfy checkstyle rules.
                        }
                    } catch (IOException ignored) {
                        // Ignored.
                    } finally {
                        try {
                            is.close();
                        } catch (Exception ignored) {
                            // Ignored.
                        }
                    }
                }
            });
        }
    }
}
