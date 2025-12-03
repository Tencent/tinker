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

    private static final int[] STANDARD_STUB_COUNT_SLOTS
            = {ActivityStubs.STANDARD_STUB_COUNT, ActivityStubs.STANDARD_TRSNAPARENT_STUB_COUNT};
    private static final int[] SINGLETOP_STUB_COUNT_SLOTS
            = {ActivityStubs.SINGLETOP_STUB_COUNT, ActivityStubs.SINGLETOP_TRSNAPARENT_STUB_COUNT};
    private static final int[] SINGLETASK_STUB_COUNT_SLOTS
            = {ActivityStubs.SINGLETASK_STUB_COUNT, ActivityStubs.SINGLETASK_TRSNAPARENT_STUB_COUNT};
    private static final int[] SINGLEINSTANCE_STUB_COUNT_SLOTS
            = {ActivityStubs.SINGLEINSTANCE_STUB_COUNT, ActivityStubs.SINGLEINSTANCE_TRSNAPARENT_STUB_COUNT};

    private static final int[] NEXT_STANDARD_STUB_IDX_SLOTS = {0, 0};
    private static final int[] NEXT_SINGLETOP_STUB_IDX_SLOTS = {0, 0};
    private static final int[] NEXT_SINGLETASK_STUB_IDX_SLOTS = {0, 0};
    private static final int[] NEXT_SINGLEINSTANCE_STUB_IDX_SLOTS = {0, 0};

    private static final int NOTRANSPARENT_SLOT_INDEX = 0;
    private static final int TRANSPARENT_SLOT_INDEX = 1;

    public static String assignStub(String targetClassName, int launchMode, boolean isTransparent) {
        String stubClassName = sTargetToStubClassNameMap.get(targetClassName);
        if (stubClassName != null) {
            return stubClassName;
        }

        String stubNameFormat;
        final int[] nextStubIdxSlots;
        final int[] countSlots;
        final int slotIdx;
        switch (launchMode) {
            case ActivityInfo.LAUNCH_SINGLE_TOP: {
                stubNameFormat = ActivityStubs.SINGLETOP_STUB_CLASSNAME_FORMAT;
                nextStubIdxSlots = NEXT_SINGLETOP_STUB_IDX_SLOTS;
                countSlots = SINGLETOP_STUB_COUNT_SLOTS;
                break;
            }
            case ActivityInfo.LAUNCH_SINGLE_TASK: {
                stubNameFormat = ActivityStubs.SINGLETASK_STUB_CLASSNAME_FORMAT;
                nextStubIdxSlots = NEXT_SINGLETASK_STUB_IDX_SLOTS;
                countSlots = SINGLETASK_STUB_COUNT_SLOTS;
                break;
            }
            case ActivityInfo.LAUNCH_SINGLE_INSTANCE: {
                stubNameFormat = ActivityStubs.SINGLEINSTANCE_STUB_CLASSNAME_FORMAT;
                nextStubIdxSlots = NEXT_SINGLEINSTANCE_STUB_IDX_SLOTS;
                countSlots = SINGLEINSTANCE_STUB_COUNT_SLOTS;
                break;
            }
            case ActivityInfo.LAUNCH_MULTIPLE:
            default: {
                stubNameFormat = ActivityStubs.STARDARD_STUB_CLASSNAME_FORMAT;
                nextStubIdxSlots = NEXT_STANDARD_STUB_IDX_SLOTS;
                countSlots = STANDARD_STUB_COUNT_SLOTS;
                break;
            }
        }
        if (isTransparent) {
            stubNameFormat += ActivityStubs.TRANSPARENT_STUB_FORMAT_SUFFIX;
            slotIdx = TRANSPARENT_SLOT_INDEX;
        } else {
            slotIdx = NOTRANSPARENT_SLOT_INDEX;
        }

        int stubIndex = nextStubIdxSlots[slotIdx]++;
        if (stubIndex >= countSlots[slotIdx]) {
            stubIndex = nextStubIdxSlots[slotIdx] = 0;
        }

        stubClassName = String.format(stubNameFormat, stubIndex);
        sTargetToStubClassNameMap.put(targetClassName, stubClassName);

        return stubClassName;
    }

    private ActivityStubManager() {
        throw new UnsupportedOperationException();
    }
}
