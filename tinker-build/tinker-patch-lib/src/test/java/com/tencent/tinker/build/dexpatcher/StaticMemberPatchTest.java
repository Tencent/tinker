package com.tencent.tinker.build.dexpatcher;

import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.commons.dexpatcher.DexPatchApplier;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StaticMemberPatchTest {

    private static final String CLASS_C_DESCRIPTOR = "LC;";
    private static final String FIXTURE_DIR = "/dexpatcher/staticmember/";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void addedStaticFieldAndMethodArePreservedAfterPatch() throws Exception {
        Dex merged = generateAndApplyPatch("base.dex", "added.dex");
        assertTrue(hasField(merged, CLASS_C_DESCRIPTOR, "A"));
        assertTrue(hasField(merged, CLASS_C_DESCRIPTOR, "B"));
        assertTrue(hasMethod(merged, CLASS_C_DESCRIPTOR, "m"));
        assertTrue(hasMethod(merged, CLASS_C_DESCRIPTOR, "n"));
    }

    @Test
    public void removedStaticFieldAndMethodAreAbsentAfterPatch() throws Exception {
        Dex merged = generateAndApplyPatch("added.dex", "base.dex");
        assertTrue(hasField(merged, CLASS_C_DESCRIPTOR, "A"));
        assertFalse(hasField(merged, CLASS_C_DESCRIPTOR, "B"));
        assertTrue(hasMethod(merged, CLASS_C_DESCRIPTOR, "m"));
        assertFalse(hasMethod(merged, CLASS_C_DESCRIPTOR, "n"));
    }

    @Test
    public void changedStaticFieldInitialValueIsAppliedAfterPatch() throws Exception {
        Dex merged = generateAndApplyPatch("base.dex", "changed.dex");
        assertTrue(hasField(merged, CLASS_C_DESCRIPTOR, "A"));
        assertTrue(hasMethod(merged, CLASS_C_DESCRIPTOR, "m"));
    }

    private Dex generateAndApplyPatch(String oldFixture, String newFixture) throws Exception {
        File oldDexFile = copyFixture(oldFixture);
        File newDexFile = copyFixture(newFixture);
        File patchFile = tempFolder.newFile(oldFixture + "-to-" + newFixture + ".patch");

        Dex oldDex = new Dex(oldDexFile);
        Dex newDex = new Dex(newDexFile);

        DexPatchGenerator generator = new DexPatchGenerator(oldDex, newDex);
        generator.executeAndSaveTo(patchFile);

        ByteArrayOutputStream mergedOut = new ByteArrayOutputStream();
        new DexPatchApplier(oldDex, new Dex(patchFile)).executeAndSaveTo(mergedOut);

        return new Dex(mergedOut.toByteArray());
    }

    private File copyFixture(String name) throws Exception {
        InputStream in = getClass().getResourceAsStream(FIXTURE_DIR + name);
        if (in == null) {
            fail("Missing test fixture: " + FIXTURE_DIR + name);
        }
        File file = tempFolder.newFile(name);
        FileOutputStream out = new FileOutputStream(file);
        try {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            out.close();
            in.close();
        }
        return file;
    }

    private boolean hasField(Dex dex, String classDescriptor, String fieldName) {
        for (FieldId fieldId : dex.fieldIds()) {
            String type = dex.typeNames().get(fieldId.declaringClassIndex);
            String name = dex.strings().get(fieldId.nameIndex);
            if (classDescriptor.equals(type) && fieldName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMethod(Dex dex, String classDescriptor, String methodName) {
        for (MethodId methodId : dex.methodIds()) {
            String type = dex.typeNames().get(methodId.declaringClassIndex);
            String name = dex.strings().get(methodId.nameIndex);
            if (classDescriptor.equals(type) && methodName.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
