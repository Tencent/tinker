package com.tencent.tinker.test.internal.modules.patch;

import com.tencent.tinker.test.internal.ParcelableTinkerPatch;

interface ITinkerPatchManagerTestOthersService {
    ParcelableTinkerPatch acquire();
    void requestUnavailable(String version);
    void assumeProcessIsDead();
}