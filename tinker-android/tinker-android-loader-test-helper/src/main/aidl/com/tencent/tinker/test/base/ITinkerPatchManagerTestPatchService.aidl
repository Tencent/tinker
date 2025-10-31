package com.tencent.tinker.test.base;

import com.tencent.tinker.test.base.ParcelableTinkerPatch;

interface ITinkerPatchManagerTestPatchService {
    void create(String version, String patchPath);
    String[] cleanAll();
    String[] cleanObsolete();
    ParcelableTinkerPatch invalidAcquire();
    void invalidRequestUnavailable(String version);
}