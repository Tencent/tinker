package com.tencent.tinker.loader;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.tencent.tinker.loader.app.TinkerApplication;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import java.lang.reflect.Field;

/**
 * Created by tangyinsheng on 2020/5/10.
 * <p>
 * Some situations may cause our resource modification to be ineffective,
 * for example, an APPLICATION_INFO_CHANGED message will reset LoadedApk#mResDir
 * to default value, then a relaunch activity which using tinker resources may
 * throw an Resources$NotFoundException.
 * <p>
 * Monitor and handle them.
 * <p>
 *
 */
public final class AppInfoChangedBlocker {
    private static final String TAG = "Tinker.AppInfoChangedBlocker";

    public static boolean tryStart(Application app) {
        if (Build.VERSION.SDK_INT < 26) {
            Log.i(TAG, "tryStart: SDK_INT is less than 26, skip rest logic.");
            return true;
        }
        try {
            ShareTinkerLog.i(TAG, "tryStart called.");
            interceptHandler(fetchMHObject(app));
            ShareTinkerLog.i(TAG, "tryStart done.");
            return true;
        } catch (Throwable e) {
            ShareTinkerLog.e(TAG, "AppInfoChangedBlocker start failed, simply ignore.", e);
            return false;
        }
    }

    private static Handler fetchMHObject(Context context) throws Exception {
        final Object activityThread = ShareReflectUtil.getActivityThread(context, null);
        final Field mHField = ShareReflectUtil.findField(activityThread, "mH");
        return (Handler) mHField.get(activityThread);
    }

    private static void interceptHandler(Handler mH) throws Exception {
        final Field mCallbackField = ShareReflectUtil.findField(Handler.class, "mCallback");
        final Handler.Callback originCallback = (Handler.Callback) mCallbackField.get(mH);
        if (!(originCallback instanceof HackerCallback)) {
            HackerCallback hackerCallback = new HackerCallback(originCallback, mH.getClass());
            mCallbackField.set(mH, hackerCallback);
        } else {
            ShareTinkerLog.w(TAG, "Already intercepted, skip rest logic.");
        }
    }

    private static class HackerCallback implements Handler.Callback {

        private final int APPLICATION_INFO_CHANGED;

        private Handler.Callback origin;

        HackerCallback(Handler.Callback ori, Class $H) {
            this.origin = ori;
            int appInfoChanged;
            try {
                appInfoChanged = ShareReflectUtil.findField($H, "APPLICATION_INFO_CHANGED").getInt(null);
            } catch (Throwable e) {
                appInfoChanged = 156; // default value
            }
            APPLICATION_INFO_CHANGED = appInfoChanged;
        }

        @Override
        public boolean handleMessage(Message msg) {
            boolean consume = false;
            if (hackMessage(msg)) {
                consume = true;
            } else if (origin != null) {
                consume = origin.handleMessage(msg);
            }
            return consume;
        }

        private boolean hackMessage(Message msg) {
            if (msg.what == APPLICATION_INFO_CHANGED) {
                // We are generally in the background this moment(signal trigger is
                // in front of user), and the signal was going to relaunch all our
                // activities to apply new overlay resources. So we could simply kill
                // ourselves, or ignore this signal, or reload tinker resources.
                ShareTinkerLog.w(TAG, "Suicide now.");
                Process.killProcess(Process.myPid());
                return true;
            }
            return false;
        }

    }
}
