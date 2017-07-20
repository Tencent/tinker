package com.tencent.tinker.loader.shareutil;

import java.nio.ByteBuffer;

/**
 * Hexadecimal encoding where each byte is represented by two hexadecimal digits.
 */
class ShareHexEncoding {

    /** Hidden constructor to prevent instantiation. */
    private ShareHexEncoding() {}

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * Encodes the provided data as a hexadecimal string.
     */
    public static String encode(byte[] data, int offset, int length) {
        StringBuilder result = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            byte b = data[offset + i];
            result.append(HEX_DIGITS[(b >>> 4) & 0x0f]);
            result.append(HEX_DIGITS[b & 0x0f]);
        }
        return result.toString();
    }

    /**
     * Encodes the provided data as a hexadecimal string.
     */
    public static String encode(byte[] data) {
        return encode(data, 0, data.length);
    }

    /**
     * Encodes the remaining bytes of the provided {@link ByteBuffer} as a hexadecimal string.
     */
    public static String encodeRemaining(ByteBuffer data) {
        return encode(data.array(), data.arrayOffset() + data.position(), data.remaining());
    }
}