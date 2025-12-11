package com.tencent.tinker.test.internal.module.layout;

interface IPatchLayoutConstructorTestMainService {
    String processBaseDirectory();
    String construct(String baseDirectoryPath, String oatDirectoryPath);
    void assumeProcessIsRestarted();
}