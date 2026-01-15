package com.tencent.tinker.test.internal.module.patch;

import com.tencent.tinker.test.internal.module.patch.ParcelableRawPatch;

interface IPatchManagerTestDeployService {
    ParcelableRawPatch invalidAcquire();
    void invalidRequestUnavailable(String version);
    void create(String version, String patchPath);
    String[] cleanAll();
    String[] cleanObsolete();
}