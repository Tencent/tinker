package com.tencent.tinker.test.internal.module.oat;

import com.tencent.tinker.test.internal.ParcelableTinkerPatch;

interface ITinkerOatManagerTestMainService {
    String acquire(in ParcelableTinkerPatch patch, boolean skipGenerateIfMissing);
    void invalidGenerateIfNeeded(in ParcelableTinkerPatch patch);
    boolean invalidClean(String version);
    void release();
    void releaseGuard();
    void setCompilerIsInvalid();
    void useSuccessInterpreter();
    void useFailureInterpreter();
    void useExceptionInterpreter();
    boolean isInterpreterGenerated();
}