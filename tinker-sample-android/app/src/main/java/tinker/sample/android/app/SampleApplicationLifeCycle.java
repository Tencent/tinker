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

package tinker.sample.android.app;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.support.multidex.MultiDex;

import com.tencent.tinker.anno.DefaultLifeCycle;
import com.tencent.tinker.lib.tinker.TinkerInstaller;
import com.tencent.tinker.loader.app.ApplicationLifeCycle;
import com.tencent.tinker.loader.app.DefaultApplicationLifeCycle;
import com.tencent.tinker.loader.app.TinkerApplication;

import tinker.sample.android.Log.MyLogImp;
import tinker.sample.android.util.TinkerManager;

/**
 * because you can not use any other class in your application, we need to
 * move your implement of Application to {@link ApplicationLifeCycle}
 *
 * We use tinker-android-anno to make sure all your classes can be patched.
 *
 * application: if it is start with '.', we will add SampleApplicationLifeCycle's package name
 *
 * flags:
 * TINKER_ABLE_ALL: support dex and lib
 * TINKER_DEX_MASK: just support dex
 * TINKER_NATIVE_LIBRARY_MASK: just support lib
 *
 * loaderClass: define the tinker loader class, we can just use the default TinkerLoader
 *
 * loadVerifyFlag: whether check files' md5 on the load time, defualt it is false.
 *
 * Created by shwenzhang on 16/3/17.
 */
@SuppressWarnings("unused")
@DefaultLifeCycle(application = ".SampleApplication", flags = TinkerApplication.TINKER_ENABLE_ALL)
public class SampleApplicationLifeCycle extends DefaultApplicationLifeCycle {
    private static final String TAG = "SampleApplicationLifeCycle";

    public SampleApplicationLifeCycle(TinkerApplication application) {
        super(application);
    }

    /**
     * install multiDex before install tinker
     * so we don't need to put the tinker lib classes in the main dex
     *
     * @param base
     */
    @Override
    public void onBaseContextAttached(Context base) {
        super.onBaseContextAttached(base);
        TinkerManager.setTinkerApplication(application);
        TinkerManager.initFastCrashProtect();
        //should set before tinker is installed
        TinkerManager.setUpgradeRetryEnable(true);

        MultiDex.install(base);

        //optional set logIml, or you can use default debug log
        TinkerInstaller.setLogIml(new MyLogImp());

        //installTinker after load multiDex
        //or you can put com.tencent.tinker.** to main dex
        TinkerManager.installTinker(application);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public void registerActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks callback) {
        application.registerActivityLifecycleCallbacks(callback);
    }


}
