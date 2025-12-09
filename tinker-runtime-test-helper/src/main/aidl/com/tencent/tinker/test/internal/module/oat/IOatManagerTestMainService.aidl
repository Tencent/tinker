package com.tencent.tinker.test.internal.module.oat;

interface IOatManagerTestMainService {
    String baseDirectory();
    String metadataFile(String directoryPath);
    String contentBaseDirectory(String directoryPath);
    void invalidGenerateIfNeeded(String directoryPath);
    boolean invalidClean(String directoryPath);
    String acquire(String directoryPath, boolean skipGenerateIfMissing);
    void release();
    void releaseGuard();
    void reset();
    void useFailureGenerator();
    void useExceptionGenerator();
    boolean isInterpreterGenerated();
}