package com.tencent.tinker.commons.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

/**
 * Created by tangyinsheng on 2019-09-18.
 */
public final class DigestUtil {
    public static long getCRC32(File file) throws IOException {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(file));
            return getCRC32(is);
        } finally {
            IOHelper.closeQuietly(is);
        }
    }

    public static long getCRC32(byte[] data, int off, int length) {
        final CRC32 crc32 = new CRC32();
        crc32.update(data, off, length);
        return crc32.getValue();
    }

    public static long getCRC32(InputStream is) throws IOException {
        final CRC32 crc32 = new CRC32();
        final byte[] buffer = new byte[4096];
        int bytesRead = 0;
        while ((bytesRead = is.read(buffer)) > 0) {
            crc32.update(buffer, 0, bytesRead);
        }
        return crc32.getValue();
    }

    private DigestUtil() {
        throw new UnsupportedOperationException();
    }
}
