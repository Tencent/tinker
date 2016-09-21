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

package com.tencent.tinker.build.info;

import com.tencent.tinker.build.patch.Configuration;

/**
 * Created by zhangshaowen on 16/3/8.
 */
public class PatchInfo {

    private final Configuration config;

    private final PatchInfoGen infoGen;


    public PatchInfo(Configuration config) {
        this.config = config;
        infoGen = new PatchInfoGen(config);
    }


    /**
     * gen the meta file txt
     * such as rev, version ...
     * file version, hotpatch version class
     */
    public void gen() throws Exception {
        infoGen.gen();
    }
}
