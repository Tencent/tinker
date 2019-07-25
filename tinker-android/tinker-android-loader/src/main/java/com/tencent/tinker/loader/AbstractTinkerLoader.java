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

import android.content.Intent;

import com.tencent.tinker.loader.app.TinkerApplication;


/**
 * Created by zhangshaowen on 16/4/30.
 */
public abstract class AbstractTinkerLoader {

    abstract public Intent tryLoad(TinkerApplication app);

    /**
     * Should we install a new patch?
     *
     * Usually we load the fresh one as soon as the main process
     * starts, and this will get others killed. However, there are
     * some scenarios where a not-main process is too important to
     * be killed, such as a music player process.
     *
     * If those process exists (or for other reasons), we return a
     * FALSE to avoid main process loading the new patch.
     */
    abstract public boolean greetNewPatch();
}
