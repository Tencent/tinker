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

package com.tencent.tinker.lib.tinker;

import android.content.Context;

import com.tencent.tinker.entry.ApplicationLike;
import com.tencent.tinker.lib.listener.PatchListener;
import com.tencent.tinker.lib.patch.AbstractPatch;
import com.tencent.tinker.lib.reporter.LoadReporter;
import com.tencent.tinker.lib.reporter.PatchReporter;
import com.tencent.tinker.lib.service.AbstractResultService;
import com.tencent.tinker.lib.util.TinkerLog;

/**
 * Created by zhangshaowen on 16/3/19.
 */
public class TinkerInstaller {
    private static final String TAG = "Tinker.TinkerInstaller";

    /**
     * install tinker with default config, you must install tinker before you use their api
     * or you can just use {@link TinkerApplicationHelper}'s api
     *
     * @param applicationLike
     */
    public static Tinker install(ApplicationLike applicationLike) {
        Tinker tinker = new Tinker.Builder(applicationLike.getApplication()).build();
        Tinker.create(tinker);
        tinker.install(applicationLike.getTinkerResultIntent());
        return tinker;
    }

    /**
     * install tinker with custom config, you must install tinker before you use their api
     * or you can just use {@link TinkerApplicationHelper}'s api
     *
     * @param applicationLike
     * @param loadReporter
     * @param patchReporter
     * @param listener
     * @param resultServiceClass
     * @param upgradePatchProcessor
     */
    public static Tinker install(ApplicationLike applicationLike, LoadReporter loadReporter, PatchReporter patchReporter,
                                 PatchListener listener, Class<? extends AbstractResultService> resultServiceClass,
                                 AbstractPatch upgradePatchProcessor) {

        Tinker tinker = new Tinker.Builder(applicationLike.getApplication())
            .tinkerFlags(applicationLike.getTinkerFlags())
            .loadReport(loadReporter)
            .listener(listener)
            .patchReporter(patchReporter)
            .tinkerLoadVerifyFlag(applicationLike.getTinkerLoadVerifyFlag()).build();

        Tinker.create(tinker);
        tinker.install(applicationLike.getTinkerResultIntent(), resultServiceClass, upgradePatchProcessor);
        return tinker;
    }

    /**
     * clean all patch files!
     *
     * @param context
     */
    public static void cleanPatch(Context context) {
        Tinker.with(context).cleanPatch();
    }

    /**
     * new patch file to install, try install them with :patch process
     *
     * @param context
     * @param patchLocation
     */
    public static void onReceiveUpgradePatch(Context context, String patchLocation) {
        Tinker.with(context).getPatchListener().onPatchReceived(patchLocation);
    }

    /**
     * set logIml for TinkerLog
     *
     * @param imp
     */
    public static void setLogIml(TinkerLog.TinkerLogImp imp) {
        TinkerLog.setTinkerLogImp(imp);
    }
}
