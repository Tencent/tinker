package com.tencent.tinker.test.base;

import com.tencent.tinker.test.base.ParcelableTinkerPatch;

interface ITinkerPatchManagerTestMainService {
    ParcelableTinkerPatch acquire();
    void requestUnavailable(String version);
    void assumeProcessIsDead();
    boolean isRequestUnavailableListenerInvoked();
}