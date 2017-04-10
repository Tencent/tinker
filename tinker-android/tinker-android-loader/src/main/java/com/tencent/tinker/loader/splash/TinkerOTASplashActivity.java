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

package com.tencent.tinker.loader.splash;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.tencent.tinker.loader.R;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

/**
 * Created by zhangshaowen on 17/2/13.
 */

public class TinkerOTASplashActivity extends Activity {
    private static final String TAG = "Tinker.SplashActivity";

    /**
     * if dex count is 0, the base max time
     */
    private static final long BASE_MAX_WAIT_TIME    = 40 * 1000;
    /**
     * can wait max than total max wait time
     */
    private static final long TOTAL_MAX_WAIT_TIME   = 100 * 1000;
    private static final long MAX_WAIT_TIME_PER_DEX = 20 * 1000;

    private static final long FADE_ANIMATION_TIME = 2 * 1000;
    private static FrameLayout rootView;
    /**
     * activity may destroy on the background
     */
    private static int sDexCount = 0;
    private        TextView    textView;
    private long maxWaitTime;
    private long costTime = 0;
    private Handler handler = new Handler(Looper.getMainLooper());

    public static void dismissActivityAndKillProcess() {
        if (rootView == null) {
            Log.e(TAG, "rootView is null, just kill process");
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        }
        try {
            AlphaAnimation fadeOutAnimation = new AlphaAnimation(1.0f, 0.0f);
            fadeOutAnimation.setDuration(FADE_ANIMATION_TIME);
            fadeOutAnimation.setInterpolator(new AccelerateInterpolator());
            fadeOutAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    Log.i(TAG, "dismiss activity start fade animation");
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    Log.i(TAG, "dismiss activity animation end, just kill process");
                    android.os.Process.killProcess(android.os.Process.myPid());
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            rootView.startAnimation(fadeOutAnimation);
        } catch (Throwable e) {
            Log.e(TAG, "dismiss activity occur error:" + e);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private void getIntentData() {
        Intent intent = getIntent();
        if (intent == null) {
            Log.i(TAG, "getIntentData, intent is null, just return!");
            return;
        }
        final long begin = ShareIntentUtil.getLongExtra(intent, ShareTinkerInternals.INTENT_SPLASH_BEGIN, 0);
        if (begin != 0) {
            costTime = SystemClock.elapsedRealtime() - begin;
            Log.i(TAG, "quick launch activity visible cost: " + costTime + "ms");
        }
        sDexCount = ShareIntentUtil.getIntExtra(intent, ShareTinkerInternals.INTENT_SPLASH_DEX_SIZE, 0);
        Log.i(TAG, "quick launch activity dex count: " + sDexCount);
    }

    private void calculateMaxWaitTime() {
        maxWaitTime = BASE_MAX_WAIT_TIME;
        if (sDexCount > 0) {
            maxWaitTime = sDexCount * MAX_WAIT_TIME_PER_DEX;
            maxWaitTime = maxWaitTime > TOTAL_MAX_WAIT_TIME ? TOTAL_MAX_WAIT_TIME : maxWaitTime;
        }
        final long delayTime = maxWaitTime - costTime;
        Log.i(TAG, "final max wait time:" + maxWaitTime + ", delay time:" + delayTime);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismissActivityAndKillProcess();
            }
        }, delayTime);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate@" + Integer.toHexString(hashCode()));
        getIntentData();
        initUI();
        calculateMaxWaitTime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume@" + Integer.toHexString(hashCode()));
    }

    protected void initUI() {
        rootView = (FrameLayout) LayoutInflater.from(this).inflate(R.layout.splash_layout, null);
        textView = (TextView) rootView.findViewById(R.id.text_view);
        textView.setText(getAppName() + " " + getString(R.string.ota_notice_update));
        setContentView(rootView);
    }

    private String getAppName() {
        int id = getResources().getIdentifier("app_name", "string", getPackageName());
        if (id > 0) {
            return getString(id);
        }
        return "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy@" + Integer.toHexString(hashCode()));
        rootView = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop@" + Integer.toHexString(hashCode()));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
