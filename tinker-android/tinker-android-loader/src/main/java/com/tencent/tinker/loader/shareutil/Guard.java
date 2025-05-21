package com.tencent.tinker.loader.shareutil;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;

/**
 * Guard lock is a mechanism based on file lock to protect used patch files not be deleted by
 * cleaner.
 * <p>
 * Each patch version has its independent guard lock file. When a process is trying to apply a patch
 * version, it have to acquire a use guard (based on shared lock) of the guard lock file first, and
 * check the guard file content is not {@link com.tencent.tinker.loader.shareutil.Guard#CLEANING_FLAG} (its meaning is
 * described below). While cleaner is cleaning obsolete patch version directories, it tries to
 * acquire a clean guard (based on exclusive lock), If failed, which means there are other processes
 * using this patch version, the cleaner must skip cleaning this patch version.
 * <p>
 * The content of the guard lock file is empty or a byte with value
 * {@link com.tencent.tinker.loader.shareutil.Guard#CLEANING_FLAG}. The content is set only if the cleaner already acquired
 * the exclusive lock, and prepares to clean the guard lock file itself after cleaning the patch
 * version directory. Even if a process acquires the shared lock successfully, if the content is
 * {@link com.tencent.tinker.loader.shareutil.Guard#CLEANING_FLAG}, the patch version is still invalid.
 */
public class Guard implements Closeable {

    private static final int CLEANING_FLAG = 1;

    private final Closeable mStream;
    private final FileLock mLock;

    private Guard(Closeable stream, FileLock lock) {
        mStream = stream;
        mLock = lock;
    }

    @Override
    public void close() {
        if (mLock != null) {
            try {
                mLock.release();
            } catch (IOException exception) {
                // ignore
            }
        }
        if (mStream != null) {
            try {
                mStream.close();
            } catch (IOException exception) {
                // ignore
            }
        }
    }

    /**
     * Try to acquire use guard, or return null if any clean guard is holding by cleaner or the
     * guard file marked as cleaning.
     *
     * @param guardLockFile the guard lock file
     * @return acquired guard, or null if failed
     */
    public static Guard acquireUse(File guardLockFile) {
        FileInputStream stream;
        try {
            stream = new FileInputStream(guardLockFile);
        } catch (IOException exception) {
            return null;
        }
        FileLock lock = null;
        try {
            lock = stream.getChannel().tryLock(0, Long.MAX_VALUE, true);
            final int flag = stream.read();
            if (flag == CLEANING_FLAG) {
                lock.release();
                stream.close();
            }
        } catch (IOException exception) {
            if (lock != null) {
                try {
                    lock.release();
                } catch (IOException ignored) {
                }
            }
            try {
                stream.close();
            } catch (IOException ignored) {
            }
            return null;
        }
        return new Guard(stream, lock);
    }

    /**
     * Try to acquire clean guard, or return null if any use guard is holding by other process.
     * <p>
     * The method will mark the guard lock file as cleaning. It should only be called by cleaner.
     *
     * @param guardLockFile the guard lock file
     * @return acquired guard, or null if failed
     */
    public static Guard acquireClean(File guardLockFile) {
        FileOutputStream stream;
        try {
            stream = new FileOutputStream(guardLockFile);
        } catch (IOException exception) {
            return null;
        }
        FileLock lock = null;
        try {
            lock = stream.getChannel().tryLock();
            stream.write(CLEANING_FLAG);
        } catch (IOException exception) {
            if (lock != null) {
                try {
                    lock.release();
                } catch (IOException ignored) {
                }
            }
            try {
                stream.close();
            } catch (IOException ignored) {
            }
            return null;
        }
        return new Guard(stream, lock);
    }
}
