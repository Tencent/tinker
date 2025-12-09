package com.tencent.tinker.test.internal.modules.patch;

import com.tencent.tinker.test.internal.ParcelableTinkerPatch;

interface ITinkerPatchManagerTestPatchService {
    void create(String version, String patchPath);
    String[] cleanAll();
    String[] cleanObsolete();
    ParcelableTinkerPatch invalidAcquire();
    void invalidRequestUnavailable(String version);
}