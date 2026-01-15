package com.tencent.tinker.test.internal.module.oat;

interface IOatManagerTestDeployService {
    String invalidAcquire(String directoryPath, boolean skipGenerateIfMissing);
    void invalidRelease();
    void generateIfNeeded(String directoryPath);
    boolean clean(String directoryPath);
    void reset();
    void useFailureGenerator();
    void useExceptionGenerator();
    boolean isCompilerGenerated();
}