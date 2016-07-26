package tinker.sample.android.crash;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.Toast;

import com.tencent.tinker.lib.tinker.TinkerApplicationHelper;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.app.TinkerApplication;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import tinker.sample.android.util.TinkerManager;
import tinker.sample.android.util.Utils;

/**
 * optional, use dynamic configuration is better way
 * <p/>
 * Created by shwenzhang on 16/7/3.
 * tinker's crash is caught by {@code LoadReporter.onLoadException}
 * use {@code TinkerApplicationHelper} api, no need to install tinker!
 */
public class SampleUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "SampleUncaughtExceptionHandler";

    private final Thread.UncaughtExceptionHandler ueh;
    private static final long QUICK_CRASH_ELAPSE = 10 * 1000;
    public static final  int  MAX_CRASH_COUNT    = 3;

    public SampleUncaughtExceptionHandler() {
        ueh = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        TinkerLog.e(TAG, "uncaughtException:" + ex.getMessage());
        tinkerFastCrashProtect();
        tinkerPreVerifiedCrashHandler(ex);
        ueh.uncaughtException(thread, ex);
    }

    /**
     * Such as Xposed, if it try to load some class before we load from patch files.
     * With dalvik, it will crash with "Class ref in pre-verified class resolved to unexpected implementation".
     * With art, it may crash at some times. But we can't know the actual crash type.
     * If it use Xposed, we can just clean patch or mention user to uninstall it.
     */
    private void tinkerPreVerifiedCrashHandler(Throwable ex) {
        if (Utils.isXposedExists(ex)) {
            //method 1
            TinkerApplication tinkerApplication = TinkerManager.getTinkerApplication();
            if (tinkerApplication == null) {
                return;
            }

            if (!TinkerApplicationHelper.isTinkerLoadSuccess(tinkerApplication)) {
                return;
            }
            TinkerLog.e(TAG, "have xposed: just clean tinker");
            TinkerApplicationHelper.cleanPatch(tinkerApplication);
            ShareTinkerInternals.setTinkerDisableWithSharedPreferences(tinkerApplication);
            //method 2
            //or you can mention user to uninstall Xposed!
            Toast.makeText(tinkerApplication, "please uninstall Xposed, illegal modify the app", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * if tinker is load, and it crash more than MAX_CRASH_COUNT, then we just clean patch.
     */
    private boolean tinkerFastCrashProtect() {
        TinkerApplication tinkerApplication = TinkerManager.getTinkerApplication();

        if (tinkerApplication == null) {
            return false;
        }

        if (!TinkerApplicationHelper.isTinkerLoadSuccess(tinkerApplication)) {
            return false;
        }

        final long elapsedTime = SystemClock.elapsedRealtime() - tinkerApplication.getApplicationStartElapsedTime();
        //this process may not install tinker, so we use TinkerApplicationHelper api
        if (elapsedTime < QUICK_CRASH_ELAPSE) {
            String currentVersion = TinkerApplicationHelper.getCurrentVersion(tinkerApplication);
            if (ShareTinkerInternals.isNullOrNil(currentVersion)) {
                return false;
            }

            SharedPreferences sp = tinkerApplication.getSharedPreferences(ShareConstants.TINKER_PREFERENCE_CONFIG, Context.MODE_MULTI_PROCESS);
            int fastCrashCount = sp.getInt(currentVersion, 0);
            if (fastCrashCount >= MAX_CRASH_COUNT) {
                TinkerApplicationHelper.cleanPatch(tinkerApplication);
                TinkerLog.e(TAG, "tinker has fast crash more than %d, we just clean patch!", fastCrashCount);
                return true;
            } else {
                sp.edit().putInt(currentVersion, ++fastCrashCount).commit();
                TinkerLog.e(TAG, "tinker has fast crash %d times", fastCrashCount);
            }
        }

        return false;
    }
}
