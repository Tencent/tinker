package com.tencent.tinker.test.internal.modules.patch;

import com.tencent.tinker.test.internal.ParcelableTinkerPatch;

interface ITinkerPatchManagerTestMainService {
    ParcelableTinkerPatch acquire();
    void requestUnavailable(String version);
    void assumeProcessIsDead();
    boolean isRequestUnavailableListenerInvoked();
}