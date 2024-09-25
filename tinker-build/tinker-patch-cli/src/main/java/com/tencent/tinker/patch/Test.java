package com.tencent.tinker.patch;

import com.tencent.tinker.build.dexpatcher.DexPatchGenerator;
import com.tencent.tinker.build.util.DexClassesComparator;
import com.tencent.tinker.commons.dexpatcher.DexPatchApplier;

import java.io.File;

public class Test {
    public static void main(String[] args) throws Throwable {
        final File oldDexFile = new File("/Users/tomystang/dexdiff_test/base.dex");
        final File newDexFile = new File("/Users/tomystang/dexdiff_test/new.dex");
        final File patchFile = new File("/Users/tomystang/dexdiff_test/patch.dexdiff");
        final File patchedDexFile = new File("/Users/tomystang/dexdiff_test/new_patched.dex");

        final DexPatchGenerator dg = new DexPatchGenerator(oldDexFile, newDexFile);
        dg.addAdditionalRemovingClassPattern("com.tencent.tinker.loader.*");
        dg.executeAndSaveTo(patchFile);

        final DexPatchApplier da = new DexPatchApplier(oldDexFile, patchFile);
        da.executeAndSaveTo(patchedDexFile);

        final DexClassesComparator dcc = new DexClassesComparator("*");
        dcc.setIgnoredRemovedClassDescPattern("com.tencent.tinker.loader.*");
        dcc.setCompareMode(DexClassesComparator.COMPARE_MODE_NORMAL);
        dcc.startCheck(newDexFile, patchedDexFile);
        System.out.println("add classes: " + dcc.getAddedClassInfos());
        System.out.println("del classes: " + dcc.getDeletedClassInfos());
        System.out.println("changed classes: " + dcc.getChangedClassDescToInfosMap());
    }
}
