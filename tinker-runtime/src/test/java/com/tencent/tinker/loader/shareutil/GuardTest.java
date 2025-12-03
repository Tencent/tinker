package com.tencent.tinker.loader.shareutil;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;

public class GuardTest {

    @Test
    public void testAcquireClean() throws IOException {
        final File guardLockFile = Files.createTempFile("tinker_guard_", ".lock").toFile();
        final CountDownLatch latchJoin = new CountDownLatch(2);
        final CountDownLatch[] latches = new CountDownLatch[] {
                new CountDownLatch(1),
                new CountDownLatch(1),
                new CountDownLatch(1)
        };
        new Thread(() -> {
            final Guard useGuard = Guard.acquireUse(guardLockFile);
            Assert.assertNotNull(useGuard);
            latches[0].countDown();
            try {
                latches[1].await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            useGuard.close();
            latches[2].countDown();
            latchJoin.countDown();
        }).start();
        new Thread(() -> {
            try {
                latches[0].await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            final Guard cleanGuardWhileUse = Guard.acquireClean(guardLockFile);
            Assert.assertNull(cleanGuardWhileUse);
            latches[1].countDown();
            try {
                latches[2].await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            final Guard cleanGuardWhileNotUse = Guard.acquireClean(guardLockFile);
            Assert.assertNotNull(cleanGuardWhileNotUse);
            cleanGuardWhileNotUse.close();
            latchJoin.countDown();
        }).start();
        try {
            latchJoin.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testAcquireUse() throws IOException {
        final File guardLockFile = Files.createTempFile("tinker_guard_", ".lock").toFile();
        final CountDownLatch latchJoin = new CountDownLatch(2);
        final CountDownLatch[] latches = new CountDownLatch[] {
                new CountDownLatch(1),
                new CountDownLatch(1),
                new CountDownLatch(1),
                new CountDownLatch(1)
        };
        new Thread(() -> {
            final Guard useGuardBeforeClean = Guard.acquireUse(guardLockFile);
            Assert.assertNotNull(useGuardBeforeClean);
            useGuardBeforeClean.close();
            latches[0].countDown();
            try {
                latches[1].await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            final Guard useGuardWhileClean = Guard.acquireUse(guardLockFile);
            Assert.assertNull(useGuardWhileClean);
            latches[2].countDown();
            try {
                latches[3].await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            final Guard useGuardAfterClean = Guard.acquireUse(guardLockFile);
            Assert.assertNull(useGuardAfterClean);
            latchJoin.countDown();
        }).start();
        new Thread(() -> {
            try {
                latches[0].await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            final Guard cleanGuard = Guard.acquireClean(guardLockFile);
            Assert.assertNotNull(cleanGuard);
            latches[1].countDown();
            try {
                latches[2].await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            cleanGuard.close();
            latches[3].countDown();
            latchJoin.countDown();
        }).start();
        try {
            latchJoin.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
