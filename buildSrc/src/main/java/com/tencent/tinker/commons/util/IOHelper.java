package com.tencent.tinker.commons.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipFile;

/**
 * Created by tangyinsheng on 2017/11/16.
 */

public final class IOHelper {
    public static void copyStream(InputStream is, OutputStream os) throws IOException {
        final byte[] buffer = new byte[4096];
        int bytesRead = 0;
        while ((bytesRead = is.read(buffer)) > 0) {
            os.write(buffer, 0, bytesRead);
        }
        os.flush();
    }

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
