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

package com.tencent.tinker.build.info;

import com.tencent.tinker.build.patch.Configuration;

/**
 * Created by shwenzhang on 16/3/8.
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
