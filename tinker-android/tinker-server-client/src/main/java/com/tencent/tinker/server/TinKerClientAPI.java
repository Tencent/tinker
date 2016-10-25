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

package com.tencent.tinker.server;

import android.content.Context;

import com.tencent.tinker.server.model.DataFetcher;

import java.io.File;

/**
 * Created by sun on 27/09/2016.
 */

public interface TinkerClientAPI {

    String REPORT_SUCCESS_URL = "http://stat.tinkerpatch.com/succ.php";
    String REPORT_FAIL_URL = "http://stat.tinkerpatch.com/err.php";
    /**
     * 每次启动或唤醒调请求 http://{Host}/{appKey}/{appVersion}?d={deviceId}&v={timestamp}
     */
    void sync(Context context, DataFetcher.DataCallback<String> callback);

    /**
     * 若本地未下载过这个补丁版本，则请求
     * http://{Host}/{appKey}/{appVersion}/file{patchVersion}?d={deviceId}&v={timestamp}
     */
    void download(
        Context context,
        String patchVersion,
        String filePath,
        DataFetcher.DataCallback<? super File> callback
    );

    /**
     * 用户补丁应用成功上报 http://stat.tinkerpatch.com/succ.php
     * k:  appKey
     * av: appVersion，当前app版本号
     * pv: patchVersion，应用的补丁版本号
     * t:  平台类型，填数字1
     */
    void reportSuccess(Context context, String patchVersion);

    /**
     * 应用补丁失败上报 http://stat.tinkerpatch.com/err.php
     * k:  appKey
     * av: appVersion，当前app版本号
     * pv: patchVersion，应用的补丁版本号
     * t:  平台类型，填数字1
     * code: 错误码
     */
    void reportFail(Context context, String patchVersion, Integer errCode);
}
