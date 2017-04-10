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

/**
 * Created by tangyinsheng on 17/3/15.
 *
 * This class is used to test if our AndroidNClassLoader can load classes in base.apk
 * after its pathList is updated.
 *
 * <b> DO NOT touch this class in any places !! </b>
 *
 * <b>
 *     If you change name of this class, you should also make such change in these places:
 *      TinkerProguardConfigTask.groovy
 *      TinkerMultidexConfigTask.groovy
 *      AndroidNClassLoader.java
 * </b>
 */
public final class TinkerTestAndroidNClassLoader {

    private TinkerTestAndroidNClassLoader() {
        throw new UnsupportedOperationException();
    }
}
