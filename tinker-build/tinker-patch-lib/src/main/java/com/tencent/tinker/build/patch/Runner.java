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

package com.tencent.tinker.build.patch;

import com.tencent.tinker.build.builder.PatchBuilder;
import com.tencent.tinker.build.decoder.ApkDecoder;
import com.tencent.tinker.build.info.PatchInfo;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.TinkerPatchException;

import java.io.IOException;

/**
 * Created by shwenzhang on 2/26/16.
 */
public class Runner {

    public static final int ERRNO_ERRORS = 1;
    public static final int ERRNO_USAGE  = 2;

    protected static long          mBeginTime;
    protected        Configuration config;

    public static void gradleRun(InputParam inputParam) {
        mBeginTime = System.currentTimeMillis();
        Runner m = new Runner();
        m.run(inputParam);
    }

    private void run(InputParam inputParam) {
        loadConfigFromGradle(inputParam);
        try {
            Logger.initLogger(config);
            tinkerPatch();
        } catch (IOException e) {
            e.printStackTrace();
            goToError();
        } finally {
            Logger.closeLogger();
        }
    }

    protected void tinkerPatch() {
        Logger.d("-----------------------tinker patch begin-----------------------");

        Logger.d(config.toString());
        //gen patch
        try {
            ApkDecoder decoder = new ApkDecoder(config);
            decoder.onAllPatchesStart();
            decoder.patch(config.mOldApkFile, config.mNewApkFile);
            decoder.onAllPatchesEnd();
        } catch (IOException | TinkerPatchException e) {
            e.printStackTrace();
            goToError();
        }

        //gen meta file and version file
        PatchInfo info = new PatchInfo(config);
        try {
            info.gen();
        } catch (Exception e) {
            e.printStackTrace();
            goToError();
        }

        //build patch
        PatchBuilder builder = new PatchBuilder(config);
        try {
            builder.buildPatch();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            goToError();
        }
        Logger.d("tinker patch done, total time cost: %fs", diffTimeFromBegin());
        Logger.d("tinker patch done, you can go to file to find the output %s", config.mOutFolder);
        Logger.d("-----------------------tinker patch end-------------------------");
    }

    private void loadConfigFromGradle(InputParam inputParam) {
        try {
            config = new Configuration(inputParam);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TinkerPatchException e) {
            e.printStackTrace();
        }
    }

    public void goToError() {
        System.exit(ERRNO_USAGE);
    }

    public double diffTimeFromBegin() {
        long end = System.currentTimeMillis();
        return (end - mBeginTime) / 1000.0;
    }

}
