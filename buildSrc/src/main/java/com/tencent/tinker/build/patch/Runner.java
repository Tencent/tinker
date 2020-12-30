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

package com.tencent.tinker.build.patch;

import com.tencent.tinker.build.builder.PatchBuilder;
import com.tencent.tinker.build.decoder.ApkDecoder;
import com.tencent.tinker.build.info.PatchInfo;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.TinkerPatchException;

import java.io.IOException;

/**
 * Created by zhangshaowen on 2/26/16.
 */
public class Runner {
    public static final int ERRNO_ERRORS = 1;
    public static final int ERRNO_USAGE  = 2;

    private final boolean mIsGradleEnv;

    protected static long          mBeginTime;
    protected        Configuration mConfig;

    public Runner(boolean isGradleEnv) {
        mIsGradleEnv = isGradleEnv;
    }

    public static void gradleRun(InputParam inputParam) {
        mBeginTime = System.currentTimeMillis();
        Runner m = new Runner(true);
        m.run(inputParam);
    }

    private void run(InputParam inputParam) {
        loadConfigFromGradle(inputParam);
        try {
            Logger.initLogger(mConfig);
            tinkerPatch();
        } catch (IOException e) {
            goToError(e, ERRNO_ERRORS);
        } finally {
            Logger.closeLogger();
        }
    }

    protected void tinkerPatch() {
        Logger.d("-----------------------Tinker patch begin-----------------------");

        Logger.d(mConfig.toString());
        try {
            //gen patch
            ApkDecoder decoder = new ApkDecoder(mConfig);
            decoder.onAllPatchesStart();
            decoder.patch(mConfig.mOldApkFile, mConfig.mNewApkFile);
            decoder.onAllPatchesEnd();

            //gen meta file and version file
            PatchInfo info = new PatchInfo(mConfig);
            info.gen();

            //build patch
            PatchBuilder builder = new PatchBuilder(mConfig);
            builder.buildPatch();

        } catch (Throwable e) {
            goToError(e, ERRNO_USAGE);
        }

        Logger.d("Tinker patch done, total time cost: %fs", diffTimeFromBegin());
        Logger.d("Tinker patch done, you can go to file to find the output %s", mConfig.mOutFolder);
        Logger.d("-----------------------Tinker patch end-------------------------");
    }

    private void loadConfigFromGradle(InputParam inputParam) {
        try {
            mConfig = new Configuration(inputParam);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TinkerPatchException e) {
            e.printStackTrace();
        }
    }

    public void goToError(Throwable thr, int errCode) {
        if (mIsGradleEnv) {
            throw new RuntimeException(thr);
        } else {
            thr.printStackTrace(System.err);
            System.exit(errCode);
        }
    }

    public double diffTimeFromBegin() {
        long end = System.currentTimeMillis();
        return (end - mBeginTime) / 1000.0;
    }

}
