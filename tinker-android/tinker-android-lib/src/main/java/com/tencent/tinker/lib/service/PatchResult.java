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

package com.tencent.tinker.lib.service;

import java.io.Serializable;

/**
 * Created by zhangshaowen on 16/3/19.
 */
public class PatchResult implements Serializable {
    public boolean isUpgradePatch;

    public boolean isSuccess;

    public String rawPatchFilePath;

    public long costTime;

    public Throwable e;

    //@Nullable
    public String patchVersion;

    //@Nullable
    public String patchTinkerID;

    //@Nullable
    public String baseTinkerID;

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("\nPatchResult: \n");
        sb.append("isUpgradePatch:" + isUpgradePatch + "\n");
        sb.append("isSuccess:" + isSuccess + "\n");
        sb.append("rawPatchFilePath:" + rawPatchFilePath + "\n");
        sb.append("costTime:" + costTime + "\n");
        sb.append("patchVersion:" + patchVersion + "\n");
        sb.append("patchTinkerID:" + patchTinkerID + "\n");
        sb.append("baseTinkerID:" + baseTinkerID + "\n");

        if (e != null) {
            sb.append("Throwable:" + e.getMessage() + "\n");
        } else {
            sb.append("Throwable: null" + "\n");
        }
        return sb.toString();
    }
}
