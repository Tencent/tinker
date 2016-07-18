package tinker.sample.android.util;

import com.tencent.tinker.lib.listener.PatchListener;
import com.tencent.tinker.lib.patch.AbstractPatch;
import com.tencent.tinker.lib.patch.RepairPatch;
import com.tencent.tinker.lib.patch.UpgradePatch;
import com.tencent.tinker.lib.reporter.LoadReporter;
import com.tencent.tinker.lib.reporter.PatchReporter;
import com.tencent.tinker.lib.tinker.TinkerInstaller;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.app.TinkerApplication;

import tinker.sample.android.crash.SampleUncaughtExceptionHandler;
import tinker.sample.android.reporter.SampleLoadReporter;
import tinker.sample.android.reporter.SamplePatchListener;
import tinker.sample.android.reporter.SamplePatchReporter;
import tinker.sample.android.service.SampleResultService;

/**
 * Created by shwenzhang on 16/7/3.
 */
public class TinkerManager {
    private static final String TAG = "TinkerManager";

    private static TinkerApplication tinkerApplication;
    private static SampleUncaughtExceptionHandler uncaughtExceptionHandler;
    private static boolean isInstalled = false;

    public static void setTinkerApplication(TinkerApplication app) {
        tinkerApplication = app;
    }

    public static TinkerApplication getTinkerApplication() {
        return tinkerApplication;
    }

    public static void initFastCrashProtect() {
        if (uncaughtExceptionHandler == null) {
            uncaughtExceptionHandler = new SampleUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler);
        }
    }

    public static void setUpgradeRetryEnable(boolean enable) {
        UpgradePatchRetry.getInstance(tinkerApplication).setRetryEnable(enable);
    }


    /**
     * all use default class, simply Tinker install method
     */
    public static void sampleInstallTinker(TinkerApplication app) {
        if (isInstalled) {
            TinkerLog.w(TAG, "install tinker, but has installed, ignore");
            return;
        }
        TinkerInstaller.install(app);
        isInstalled = true;

    }

    /**
     * you can specify all class you want.
     * sometimes, you can only install tinker in some process you want!
     *
     * @param app
     */
    public static void installTinker(TinkerApplication app) {
        if (isInstalled) {
            TinkerLog.w(TAG, "install tinker, but has installed, ignore");
            return;
        }
        //or you can just use DefaultLoadReporter
        LoadReporter loadReporter = new SampleLoadReporter(app);
        //or you can just use DefaultPatchReporter
        PatchReporter patchReporter = new SamplePatchReporter(app);
        //or you can just use DefaultPatchListener
        PatchListener patchListener = new SamplePatchListener(app);
        //you can set your own upgrade patch if you need
        AbstractPatch upgradePatchProcessor = new UpgradePatch();
        //you can set your own repair patch if you need
        AbstractPatch repairPatchProcessor = new RepairPatch();

        TinkerInstaller.install(app,
            loadReporter, patchReporter, patchListener,
            SampleResultService.class, upgradePatchProcessor, repairPatchProcessor);

        isInstalled = true;
    }
}
