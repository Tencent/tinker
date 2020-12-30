package com.aire.patchtestlib;

import com.tencent.tinker.build.dexpatcher.DexPatchGenerator;

import java.io.File;

/**
 * Created by ZhuPeipei on 2020/12/28 15:58.
 */
public class ApkPatchGenTest {
    public static void main(String[] args) throws Exception {
        System.out.println("abc");
//        File oldDexFile = new File("/Users/xmly/development/learn_test/tinker/dexStore/2/oldClass.dex");
//        File newDexFile = new File("/Users/xmly/development/learn_test/tinker/dexStore/2/newClass.dex");
        File oldDexFile = new File("/Users/xmly/Downloads/patch/old/classes7.dex");
        File newDexFile = new File("/Users/xmly/Downloads/patch/new/classes7.dex");
        DexPatchGenerator gen = new DexPatchGenerator(oldDexFile, newDexFile);
        File patchFile = new File("/Users/xmly/development/learn_test/tinker/dexStore/2/patch.zip");
        gen.executeAndSaveTo(patchFile);
    }
}
