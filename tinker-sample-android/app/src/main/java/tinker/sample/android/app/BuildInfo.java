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

package tinker.sample.android.app;

import tinker.sample.android.BuildConfig;

/**
 * Created by zhangshaowen on 16/6/30.
 * we use BuildInfo instead of {@link BuildInfo} to make less change
 */
public class BuildInfo {
    /**
     * they are not final, so they won't change with the BuildConfig values!
     */
    public static boolean DEBUG        = BuildConfig.DEBUG;
    public static String  VERSION_NAME = BuildConfig.VERSION_NAME;
    public static int     VERSION_CODE = BuildConfig.VERSION_CODE;

    public static String MESSAGE       = BuildConfig.MESSAGE;
    public static String TINKER_ID     = BuildConfig.TINKER_ID;
    public static String PLATFORM      = BuildConfig.PLATFORM;

}
