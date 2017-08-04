package com.tencent.tinker.loader.hotplug;

import android.content.pm.ActivityInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class ActivityStubManager {
    private static final String TAG = "Tinker.ActivityStubManager";

    private static Map<String, String> sTargetToStubClassNameMap = new HashMap<>();
    private static int nextAvailStandardStubIndex = 0;
    private static int nextAvailSingleTopStubIndex = 0;
    private static int nextAvailSingleTaskIndex = 0;
    private static int nextAvailSingleInstanceStubIndex = 0;

    public static String assignStub(String targetClassName, int launchMode) {
        String stubClassName = sTargetToStubClassNameMap.get(targetClassName);
        if (stubClassName != null) {
            return stubClassName;
        }
        switch (launchMode) {
            case ActivityInfo.LAUNCH_SINGLE_TOP: {
                final int stubIndex = nextAvailSingleTopStubIndex++;
                if (nextAvailSingleTopStubIndex > ActivityStubs.SINGLETOP_STUB_COUNT) {
                    nextAvailSingleTopStubIndex = 0;
                }
                stubClassName = String.format(ActivityStubs.SINGLETOP_STUB_CLASSNAME_FORMAT, stubIndex);
                break;
            }
            case ActivityInfo.LAUNCH_SINGLE_TASK: {
                final int stubIndex = nextAvailSingleTaskIndex++;
                if (nextAvailSingleTaskIndex > ActivityStubs.SINGLETASK_STUB_COUNT) {
                    nextAvailSingleTaskIndex = 0;
                }
                stubClassName = String.format(ActivityStubs.SINGLETASK_STUB_CLASSNAME_FORMAT, stubIndex);
                break;
            }
            case ActivityInfo.LAUNCH_SINGLE_INSTANCE: {
                final int stubIndex = nextAvailSingleInstanceStubIndex++;
                if (nextAvailSingleInstanceStubIndex > ActivityStubs.SINGLEINSTANCE_STUB_COUNT) {
                    nextAvailSingleInstanceStubIndex = 0;
                }
                stubClassName = String.format(ActivityStubs.SINGLEINSTANCE_STUB_CLASSNAME_FORMAT, stubIndex);
                break;
            }
            case ActivityInfo.LAUNCH_MULTIPLE:
            default: {
                final int stubIndex = nextAvailStandardStubIndex++;
                if (nextAvailStandardStubIndex > ActivityStubs.STANDARD_STUB_COUNT) {
                    nextAvailStandardStubIndex = 0;
                }
                stubClassName = String.format(ActivityStubs.STARDARD_STUB_CLASSNAME_FORMAT, stubIndex);
                break;
            }
        }
        sTargetToStubClassNameMap.put(targetClassName, stubClassName);
        return stubClassName;
    }

    private ActivityStubManager() {
        throw new UnsupportedOperationException();
    }
}
