package com.tencent.tinker.test.internal.modules.oat;

import com.tencent.tinker.test.internal.ParcelableTinkerPatch;

interface ITinkerOatManagerTestPatchService {
    void generateIfNeeded(in ParcelableTinkerPatch patch);
    boolean clean(String version);
    String invalidAcquire(in ParcelableTinkerPatch patch, boolean skipGenerateIfMissing);
    void invalidRelease();
    void setInterpreterIsInvalid();
    void useSuccessCompiler();
    void useFailureCompiler();
    void useExceptionCompiler();
    boolean isCompilerGenerated();
}