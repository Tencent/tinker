package com.tencent.tinker.test.base;

import com.tencent.tinker.test.base.ParcelableTinkerPatch;

interface ITinkerPatchManagerTestOthersService {
    ParcelableTinkerPatch acquire();
    void requestUnavailable(String version);
    void assumeProcessIsDead();
}