/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package tinker.sample.android.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

/**
 * Created by sunpengfei on 2016/10/11.
 */

public class TinkerInstrumentation extends Instrumentation {

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (className.equals("tinker.sample.PluginActivity") && intent != null) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                String realActivity = bundle.getString("real-activity");
                if (!TextUtils.isEmpty(realActivity)) {
                    return super.newActivity(cl, realActivity, intent);
                }
            }
        }
        return super.newActivity(cl, className, intent);
    }
}
