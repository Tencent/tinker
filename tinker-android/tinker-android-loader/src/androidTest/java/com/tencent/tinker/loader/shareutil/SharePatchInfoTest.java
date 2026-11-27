package com.tencent.tinker.loader.shareutil;

import com.tencent.tinker.loader.TinkerRuntimeException;

import org.junit.Assert;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

public final class SharePatchInfoTest {
    /** Reading from a secondary process must preserve its active version. */
    @Test
    public void readWithLock_pendingRevocation_preservesCurrentVersion() throws Exception {
        try (final Helper fixture = new Helper()) {
            fixture.store("11111111111111111111111111111111", "");
            final byte[] contents = fixture.readBytes();
            final SharePatchInfo result = SharePatchInfo.readAndCheckPropertyWithLock(fixture.mInfo, fixture.mLock);
            fixture.assertVersions(result, "11111111111111111111111111111111", "");
            Assert.assertArrayEquals(contents, fixture.readBytes());
        }
    }

    /** Process shutdown precedes committing the Base selection. */
    @Test
    public void readAndCommitBase_pendingRevocation_stopsProcessesBeforeWrite() throws Exception {
        try (final Helper fixture = new Helper()) {
            fixture.store("11111111111111111111111111111111", "");
            final SharePatchInfo result = SharePatchInfo.readAndCommitBaseVersionWithLock(
                    fixture.mInfo,
                    fixture.mLock,
                    () -> {
                        try {
                            fixture.assertStoredVersions("11111111111111111111111111111111", "");
                        } catch (IOException exception) {
                            throw new AssertionError(exception);
                        }
                    }
            );
            fixture.assertVersions(result, "", "");
            fixture.assertStoredVersions("", "");
        }
    }

    /** Ordinary patch loads and pending upgrades do not enter revocation handling. */
    @Test
    public void readAndCommitBase_activeOrPendingUpgrade_remainsUnchanged() throws Exception {
        try (final Helper fixture = new Helper()) {
            for (final String target : new String[]{"11111111111111111111111111111111", "22222222222222222222222222222222"}) {
                fixture.store("11111111111111111111111111111111", target);
                final byte[] contents = fixture.readBytes();
                final SharePatchInfo result = SharePatchInfo.readAndCommitBaseVersionWithLock(
                        fixture.mInfo,
                        fixture.mLock,
                        () -> Assert.fail("An active or upgrading patch must not trigger a Base switch")
                );
                fixture.assertVersions(result, "11111111111111111111111111111111", target);
                Assert.assertArrayEquals(contents, fixture.readBytes());
            }
        }
    }

    /** The first patch stays pending until its normal main-process loading path succeeds. */
    @Test
    public void readAndCommitBase_firstPatchPending_doesNotCommitBase() throws Exception {
        try (final Helper fixture = new Helper()) {
            fixture.store("", "22222222222222222222222222222222");
            final byte[] contents = fixture.readBytes();
            final SharePatchInfo result = SharePatchInfo.readAndCommitBaseVersionWithLock(
                    fixture.mInfo,
                    fixture.mLock,
                    () -> Assert.fail("A pending first patch is not a revocation")
            );
            fixture.assertVersions(result, "", "22222222222222222222222222222222");
            Assert.assertArrayEquals(contents, fixture.readBytes());
        }
    }

    /** Repeated Base startup must not keep terminating unrelated secondary work. */
    @Test
    public void readAndCommitBase_alreadyBase_doesNotStopProcesses() throws Exception {
        try (final Helper fixture = new Helper()) {
            fixture.store("", "");
            final byte[] contents = fixture.readBytes();
            final SharePatchInfo result = SharePatchInfo.readAndCommitBaseVersionWithLock(
                    fixture.mInfo,
                    fixture.mLock,
                    () -> Assert.fail("A stable Base selection must not trigger process termination")
            );
            fixture.assertVersions(result, "", "");
            Assert.assertArrayEquals(contents, fixture.readBytes());
        }
    }

    /** Shutdown runs while the metadata lock excludes competing readers and writers. */
    @Test
    public void readAndCommitBase_pendingRevocation_holdsLockDuringProcessStop() throws Exception {
        try (final Helper fixture = new Helper()) {
            fixture.store("11111111111111111111111111111111", "");
            SharePatchInfo.readAndCommitBaseVersionWithLock(
                    fixture.mInfo,
                    fixture.mLock,
                    () -> {
                        try (final FileOutputStream output = new FileOutputStream(fixture.mLock)) {
                            try (final FileLock competingLock = output.getChannel().tryLock()) {
                                Assert.assertNull(competingLock);
                            } catch (OverlappingFileLockException ignored) {
                                // A JVM-local contender reports overlap instead of blocking.
                            }
                        } catch (IOException exception) {
                            throw new AssertionError(exception);
                        }
                    }
            );
            fixture.assertVersions(SharePatchInfo.readAndCheckPropertyWithLock(fixture.mInfo, fixture.mLock), "", "");
        }
    }

    /** A failed shutdown keeps the prior selection and does not leak the metadata lock. */
    @Test
    public void readAndCommitBase_failedProcessStop_preservesMetadataAndReleasesLock() throws Exception {
        try (final Helper fixture = new Helper()) {
            fixture.store("11111111111111111111111111111111", "");
            final byte[] contents = fixture.readBytes();
            final IllegalStateException failure = new IllegalStateException("Process enumeration failed");
            try {
                SharePatchInfo.readAndCommitBaseVersionWithLock(fixture.mInfo, fixture.mLock, () -> { throw failure; });
                Assert.fail("A failed process stop must abort the Base switch");
            } catch (TinkerRuntimeException exception) {
                Assert.assertSame(failure, exception.getCause());
                Assert.assertTrue(exception.getMessage().contains(fixture.mInfo.toString()));
            }
            Assert.assertArrayEquals(contents, fixture.readBytes());
            fixture.assertVersions(SharePatchInfo.readAndCheckPropertyWithLock(fixture.mInfo, fixture.mLock), "11111111111111111111111111111111", "");
        }
    }

    /** Revocation changes the target slot only, preserving the active version and metadata. */
    @Test
    public void revokePatch_pendingUpgrade_preservesCurrentVersionAndOtherFields() throws Exception {
        try (final Helper fixture = new Helper()) {
            fixture.store("11111111111111111111111111111111", "22222222222222222222222222222222");
            final Properties expected = fixture.readProperties();
            expected.setProperty("new", "");
            expected.setProperty("version_to_remove", "");
            Assert.assertTrue(SharePatchInfo.revokePatchInfoWithLock(fixture.mInfo, fixture.mLock));
            Assert.assertEquals(expected, fixture.readProperties());
            fixture.assertVersions(SharePatchInfo.readAndCheckPropertyWithLock(fixture.mInfo, fixture.mLock), "11111111111111111111111111111111", "");
        }
    }

    /** Revoking a committed Base selection leaves it stable. */
    @Test
    public void revokePatch_alreadyBase_isIdempotent() throws Exception {
        try (final Helper fixture = new Helper()) {
            fixture.store("", "");
            Assert.assertTrue(SharePatchInfo.revokePatchInfoWithLock(fixture.mInfo, fixture.mLock));
            Assert.assertTrue(SharePatchInfo.revokePatchInfoWithLock(fixture.mInfo, fixture.mLock));
            fixture.assertStoredVersions("", "");
        }
    }

    /** Repeated revocation must not clear the active slot before main-process startup. */
    @Test
    public void revokePatch_repeatedPendingRevocation_retainsCurrentVersion() throws Exception {
        try (final Helper fixture = new Helper()) {
            fixture.store("11111111111111111111111111111111", "11111111111111111111111111111111");
            Assert.assertTrue(SharePatchInfo.revokePatchInfoWithLock(fixture.mInfo, fixture.mLock));
            Assert.assertTrue(SharePatchInfo.revokePatchInfoWithLock(fixture.mInfo, fixture.mLock));
            fixture.assertVersions(SharePatchInfo.readAndCheckPropertyWithLock(fixture.mInfo, fixture.mLock), "11111111111111111111111111111111", "");
        }
    }

    /** Invalid metadata is not replaced with a fabricated patch selection. */
    @Test
    public void revokePatch_corruptedMetadata_isNotOverwritten() throws Exception {
        try (final Helper fixture = new Helper()) {
            fixture.store("broken", "11111111111111111111111111111111");
            final byte[] contents = fixture.readBytes();
            Assert.assertFalse(SharePatchInfo.revokePatchInfoWithLock(fixture.mInfo, fixture.mLock));
            Assert.assertArrayEquals(contents, fixture.readBytes());
            Assert.assertNull(SharePatchInfo.readAndCheckPropertyWithLock(fixture.mInfo, fixture.mLock));
        }
    }

    /** Missing metadata stays missing and the lock remains available. */
    @Test
    public void revokePatch_missingMetadata_doesNotCreateSelection() throws Exception {
        try (final Helper fixture = new Helper()) {
            Assert.assertFalse(SharePatchInfo.revokePatchInfoWithLock(fixture.mInfo, fixture.mLock));
            Assert.assertFalse(fixture.mInfo.exists());
            try (final ShareFileLockHelper lock = ShareFileLockHelper.getFileLock(fixture.mLock)) {
                Assert.assertNotNull(lock);
            }
        }
    }

    private static final class Helper implements AutoCloseable {
        private final File mDirectory;
        private final File mInfo;
        private final File mLock;

        private Helper() throws IOException {
            mDirectory = File.createTempFile("tinker_consistency_", ".directory");
            Assert.assertTrue(mDirectory.delete());
            Assert.assertTrue(mDirectory.mkdir());
            mInfo = new File(mDirectory, "patch_meta.info");
            mLock = new File(mDirectory, "info.lock");
        }

        private void store(
                final String currentVersion,
                final String targetVersion
        ) throws IOException {
            final Properties properties = new Properties();
            properties.setProperty("old", currentVersion);
            properties.setProperty("new", targetVersion);
            properties.setProperty("is_protected_app", "0");
            properties.setProperty("use_custom_file_patch", "0");
            properties.setProperty("version_to_remove", "");
            properties.setProperty("print", "test-fingerprint");
            properties.setProperty("dir", "odex");
            properties.setProperty("is_remove_interpret_oat_dir", "0");
            try (final FileOutputStream output = new FileOutputStream(mInfo)) {
                properties.store(output, "test selection");
            }
        }

        private void assertStoredVersions(
                final String currentVersion,
                final String targetVersion
        ) throws IOException {
            final Properties properties = readProperties();
            Assert.assertEquals(currentVersion, properties.getProperty("old"));
            Assert.assertEquals(targetVersion, properties.getProperty("new"));
        }

        private Properties readProperties() throws IOException {
            final Properties properties = new Properties();
            try (final FileInputStream input = new FileInputStream(mInfo)) {
                properties.load(input);
            }
            return properties;
        }

        private byte[] readBytes() throws IOException {
            final byte[] contents = new byte[(int) mInfo.length()];
            try (final DataInputStream input = new DataInputStream(new FileInputStream(mInfo))) {
                input.readFully(contents);
            }
            return contents;
        }

        private void assertVersions(
                final SharePatchInfo info,
                final String currentVersion,
                final String targetVersion
        ) {
            Assert.assertNotNull(info);
            Assert.assertEquals(currentVersion, info.oldVersion);
            Assert.assertEquals(targetVersion, info.newVersion);
            Assert.assertFalse(info.isProtectedApp);
            Assert.assertFalse(info.useCustomPatch);
            Assert.assertEquals("", info.versionToRemove);
            Assert.assertEquals("test-fingerprint", info.fingerPrint);
            Assert.assertEquals("odex", info.oatDir);
            Assert.assertFalse(info.isRemoveInterpretOATDir);
        }

        @Override
        public void close() {
            if (mInfo.exists()) {
                Assert.assertTrue(mInfo.delete());
            }
            if (mLock.exists()) {
                Assert.assertTrue(mLock.delete());
            }
            Assert.assertTrue(mDirectory.delete());
        }
    }
}
