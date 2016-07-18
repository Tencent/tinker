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

package com.tencent.tinker.lib.service;

import java.io.Serializable;

/**
 * Created by shwenzhang on 16/3/19.
 */
public class PatchResult implements Serializable {
    public final boolean isUpgradePatch;

    public final boolean isSuccess;

    public final String rawPatchFilePath;

    public final long costTime;

    public final Throwable e;

    public PatchResult(boolean isUpgradePatch, boolean isSuccess, String rawPatchFilePath, long costTime, Throwable e) {
        this.isUpgradePatch = isUpgradePatch;
        this.isSuccess = isSuccess;
        this.rawPatchFilePath = rawPatchFilePath;
        this.costTime = costTime;
        this.e = e;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("PatchResult: \n");
        sb.append("isUpgradePatch:" + isUpgradePatch + "\n");
        sb.append("isSuccess:" + isSuccess + "\n");
        sb.append("rawPatchFilePath:" + rawPatchFilePath + "\n");
        sb.append("costTime:" + costTime + "\n");
        if (e != null) {
            sb.append("Throwable:" + e.getMessage() + "\n");
        } else {
            sb.append("Throwable: null" + "\n");
        }
        return sb.toString();
    }
}
