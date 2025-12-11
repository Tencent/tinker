package com.tencent.tinker.test.internal.module.patch;

import com.tencent.tinker.test.internal.module.patch.ParcelableRawPatch;

interface IPatchManagerTestMainService {
    ParcelableRawPatch acquire();
    String baseDirectory();
    String latestVersionFile();
    String patchDirectory(String version);
    void invalidCreate(String version, String patchPath);
    void invalidCleanAll();
    void invalidCleanObsolete();
    void requestUnavailable(String version);
    void assumeProcessIsDead();
    boolean isRequestUnavailableListenerInvoked();
}