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

package tinker.sample.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.tencent.tinker.lib.service.DefaultTinkerResultService;
import com.tencent.tinker.lib.service.PatchResult;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.lib.util.TinkerServiceInternals;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;

import java.io.File;

import tinker.sample.android.util.Utils;

/**
 * optional, you can just use DefaultTinkerResultService
 * we can restart process when we are at background or screen off
 * Created by shwenzhang on 16/4/13.
 */
public class SampleResultService extends DefaultTinkerResultService {
    private static final String TAG = "SampleResultService";


    @Override
    public void onPatchResult(final PatchResult result) {
        TinkerLog.i(TAG, "SampleResultService receive result: %s", result.toString());

        //first, we want to kill the recover process
        TinkerServiceInternals.killTinkerPatchServiceProcess(getApplicationContext());

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (result.isSuccess) {
                    Toast.makeText(getApplicationContext(), "patch success, please restart process", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "patch fail, please check reason", Toast.LENGTH_LONG).show();
                }
            }
        });
        // is success and newPatch, it is nice to delete the raw file, and restart at once
        // for old patch, you can't delete the patch file
        if (result.isSuccess && result.isUpgradePatch) {
            File rawFile = new File(result.rawPatchFilePath);
            if (rawFile.exists()) {
                TinkerLog.i(TAG, "save delete raw patch file");
                SharePatchFileUtil.safeDeleteFile(rawFile);
            }
            //not like TinkerResultService, I want to restart just when I am at background!
            if (Utils.isBackground()) {
                restartProcess();
            } else {
                //we can wait process at background, such as onAppBackground
                //or we can restart when the screen off
                new ScreenState(getApplicationContext(), new ScreenState.IOnScreenOff() {
                    @Override
                    public void onScreenOff() {
                        restartProcess();
                    }
                });
            }

        }

        //repair current patch fail, just clean!
        if (!result.isSuccess && !result.isUpgradePatch) {
            Tinker.with(getApplicationContext()).cleanPatch();
        }
    }

    private void restartProcess() {
        TinkerLog.i(TAG, "app is background now, i can kill quietly");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    static class ScreenState {
        interface IOnScreenOff {
            void onScreenOff();
        }

        ScreenState(Context context, final IOnScreenOff onScreenOffInterface) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            context.registerReceiver(new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent in) {
                    String action = in == null ? "" : in.getAction();
                    TinkerLog.i(TAG, "ScreenReceiver action [%s] ", action);
                    if (Intent.ACTION_SCREEN_OFF.equals(action)) {

                        context.unregisterReceiver(this);

                        if (onScreenOffInterface != null) {
                            onScreenOffInterface.onScreenOff();
                        }
                    }
                }
            }, filter);
        }
    }

}
