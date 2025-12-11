package com.tencent.tinker.test.internal.module.patch;

import com.tencent.tinker.test.internal.module.patch.ParcelableRawPatch;

interface IPatchManagerTestOthersService {
    ParcelableRawPatch acquire();
    void requestUnavailable(String version);
    void assumeProcessIsDead();
}