package com.tencent.tinker.commons.util;

import java.io.Closeable;
import java.util.zip.ZipFile;

/**
 * Created by tomystang on 2017/11/16.
 */

public final class StreamUtil {

    /**
     * Closes the given {@code obj}. Suppresses any exceptions.
     */
    @SuppressWarnings("NewApi")
    public static void closeQuietly(Object obj) {
        if (obj == null) return;
        try {
            if (obj instanceof Closeable) {
                ((Closeable) obj).close();
            } else if (obj instanceof AutoCloseable) {
                ((AutoCloseable) obj).close();
            } else if (obj instanceof ZipFile) {
                ((ZipFile) obj).close();
            }
        } catch (Throwable ignored) {
            // ignored.
        }
    }
}
