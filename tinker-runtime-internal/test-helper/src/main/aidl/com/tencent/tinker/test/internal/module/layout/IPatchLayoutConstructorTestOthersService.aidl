package com.tencent.tinker.test.internal.module.layout;

interface IPatchLayoutConstructorTestOthersService {
    String processBaseDirectory();
    String construct(String baseDirectoryPath, String oatDirectoryPath);
    void assumeProcessIsRestarted();
}