/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.ziputils.ziputil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.zip.ZipException;

/**
 * modify by zhangshaowen on 16/6/7.
 * remove zip64
 *
 * An entry within a zip file.
 * An entry has attributes such as its name (which is actually a path) and the uncompressed size
 * of the corresponding data. An entry does not contain the data itself, but can be used as a key
 * with {@link TinkerZipFile#getInputStream}. The class documentation for {@code ZipInputStream} and
 * {@link TinkerZipOutputStream} shows how {@code ZipEntry} is used in conjunction with those two classes.
 */
public class TinkerZipEntry implements ZipConstants, Cloneable {
    /**
     * Zip entry state: Deflated.
     */
    public static final int DEFLATED = 8;
    /**
     * Zip entry state: Stored.
     */
    public static final int STORED = 0;
    String name;
    String comment;
    long crc = -1; // Needs to be a long to distinguish -1 ("not set") from the 0xffffffff CRC32.
    long compressedSize = -1;
    long size = -1;
    int compressionMethod = -1;
    int time = -1;
    int modDate = -1;
    byte[] extra;
    long localHeaderRelOffset = -1;
    long dataOffset = -1;
    /** @hide - for testing only */
    public TinkerZipEntry(String name, String comment, long crc, long compressedSize,
                          long size, int compressionMethod, int time, int modDate, byte[] extra,
                          long localHeaderRelOffset, long dataOffset) {
        this.name = name;
        this.comment = comment;
        this.crc = crc;
        this.compressedSize = compressedSize;
        this.size = size;
        this.compressionMethod = compressionMethod;
        this.time = time;
        this.modDate = modDate;
        this.extra = extra;
        this.localHeaderRelOffset = localHeaderRelOffset;
        this.dataOffset = dataOffset;
    }
    /**
     * Constructs a new {@code ZipEntry} with the specified name. The name is actually a path,
     * and may contain {@code /} characters.
     *
     * @throws IllegalArgumentException
     *             if the name length is outside the range (> 0xFFFF).
     */
    public TinkerZipEntry(String name) {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        validateStringLength("Name", name);
        this.name = name;
    }
    /**
     * Constructs a new {@code ZipEntry} using the values obtained from {@code
     * ze}.
     *
     * @param ze
     *            the {@code ZipEntry} from which to obtain values.
     */
    public TinkerZipEntry(TinkerZipEntry ze) {
        name = ze.name;
        comment = ze.comment;
        time = ze.time;
        size = ze.size;
        compressedSize = ze.compressedSize;
        crc = ze.crc;
        compressionMethod = ze.compressionMethod;
        modDate = ze.modDate;
        extra = ze.extra;
        localHeaderRelOffset = ze.localHeaderRelOffset;
        dataOffset = ze.dataOffset;
    }

    public TinkerZipEntry(TinkerZipEntry ze, String name) {
        this.name = name;
        comment = ze.comment;
        time = ze.time;
        size = ze.size;
        compressedSize = ze.compressedSize;
        crc = ze.crc;
        compressionMethod = ze.compressionMethod;
        modDate = ze.modDate;
        extra = ze.extra;
        localHeaderRelOffset = ze.localHeaderRelOffset;
        dataOffset = ze.dataOffset;
    }
    /*
     * Internal constructor.  Creates a new ZipEntry by reading the
     * Central Directory Entry (CDE) from "in", which must be positioned
     * at the CDE signature. If the GPBF_UTF8_FLAG is set in the CDE then
     * UTF-8 is used to decode the string information, otherwise the
     * defaultCharset is used.
     *
     * On exit, "in" will be positioned at the start of the next entry
     * in the Central Directory.
     */
    TinkerZipEntry(byte[] cdeHdrBuf, InputStream cdStream, Charset defaultCharset, boolean isZip64) throws IOException {
        Streams.readFully(cdStream, cdeHdrBuf, 0, cdeHdrBuf.length);
        BufferIterator it = HeapBufferIterator.iterator(cdeHdrBuf, 0, cdeHdrBuf.length,
                ByteOrder.LITTLE_ENDIAN);
        int sig = it.readInt();
        if (sig != CENSIG) {
            TinkerZipFile.throwZipException("unknown", cdStream.available(), "unknown", 0, "Central Directory Entry", sig);
        }
        it.seek(8);
        int gpbf = it.readShort() & 0xffff;
        if ((gpbf & TinkerZipFile.GPBF_UNSUPPORTED_MASK) != 0) {
            throw new ZipException("Invalid General Purpose Bit Flag: " + gpbf);
        }
        // If the GPBF_UTF8_FLAG is set then the character encoding is UTF-8 whatever the default
        // provided.
        Charset charset = defaultCharset;
        if ((gpbf & TinkerZipFile.GPBF_UTF8_FLAG) != 0) {
            charset = Charset.forName("UTF-8");
        }
        compressionMethod = it.readShort() & 0xffff;
        time = it.readShort() & 0xffff;
        modDate = it.readShort() & 0xffff;
        // These are 32-bit values in the file, but 64-bit fields in this object.
        crc = ((long) it.readInt()) & 0xffffffffL;
        compressedSize = ((long) it.readInt()) & 0xffffffffL;
        size = ((long) it.readInt()) & 0xffffffffL;
        int nameLength = it.readShort() & 0xffff;
        int extraLength = it.readShort() & 0xffff;
        int commentByteCount = it.readShort() & 0xffff;
        // This is a 32-bit value in the file, but a 64-bit field in this object.
        it.seek(42);
        localHeaderRelOffset = ((long) it.readInt()) & 0xffffffffL;
        byte[] nameBytes = new byte[nameLength];
        Streams.readFully(cdStream, nameBytes, 0, nameBytes.length);
        if (containsNulByte(nameBytes)) {
            throw new ZipException("Filename contains NUL byte: " + Arrays.toString(nameBytes));
        }
        name = new String(nameBytes, 0, nameBytes.length, charset);
        if (extraLength > 0) {
            extra = new byte[extraLength];
            Streams.readFully(cdStream, extra, 0, extraLength);
        }
        if (commentByteCount > 0) {
            byte[] commentBytes = new byte[commentByteCount];
            Streams.readFully(cdStream, commentBytes, 0, commentByteCount);
            comment = new String(commentBytes, 0, commentBytes.length, charset);
        }
        /*if (isZip64) {
            Zip64.parseZip64ExtendedInfo(this, true *//* from central directory *//*);
        }*/
    }

    private static boolean containsNulByte(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static void validateStringLength(String argument, String string) {
        // This check is not perfect: the character encoding is determined when the entry is
        // written out. UTF-8 is probably a worst-case: most alternatives should be single byte per
        // character.
        byte[] bytes = string.getBytes(Charset.forName("UTF-8"));
        if (bytes.length > 0xffff) {
            throw new IllegalArgumentException(argument + " too long: " + bytes.length);
        }
    }

    /**
     * Returns the comment for this {@code ZipEntry}, or {@code null} if there is no comment.
     * If we're reading a zip file using {@code ZipInputStream}, the comment is not available.
     */
    public String getComment() {
        return comment;
    }

    /**
     * Sets the comment for this {@code ZipEntry}.
     * @throws IllegalArgumentException if the comment is >= 64 Ki UTF-8 bytes.
     */
    public void setComment(String comment) {
        if (comment == null) {
            this.comment = null;
            return;
        }
        validateStringLength("Comment", comment);
        this.comment = comment;
    }

    /**
     * Gets the compressed size of this {@code ZipEntry}.
     *
     * @return the compressed size, or -1 if the compressed size has not been
     *         set.
     */
    public long getCompressedSize() {
        return compressedSize;
    }

    /**
     * Sets the compressed size for this {@code ZipEntry}.
     *
     * @param value
     *            the compressed size (in bytes).
     */
    public void setCompressedSize(long value) {
        compressedSize = value;
    }

    /**
     * Gets the checksum for this {@code ZipEntry}.
     *
     * @return the checksum, or -1 if the checksum has not been set.
     */
    public long getCrc() {
        return crc;
    }

    /**
     * Sets the checksum for this {@code ZipEntry}.
     *
     * @param value
     *            the checksum for this entry.
     * @throws IllegalArgumentException
     *             if {@code value} is < 0 or > 0xFFFFFFFFL.
     */
    public void setCrc(long value) {
        if (value >= 0 && value <= 0xFFFFFFFFL) {
            crc = value;
        } else {
            throw new IllegalArgumentException("Bad CRC32: " + value);
        }
    }

    /**
     * Gets the extra information for this {@code ZipEntry}.
     *
     * @return a byte array containing the extra information, or {@code null} if
     *         there is none.
     */
    public byte[] getExtra() {
        return extra;
    }

    /**
     * Sets the extra information for this {@code ZipEntry}.
     *
     * @throws IllegalArgumentException if the data length >= 64 KiB.
     */
    public void setExtra(byte[] data) {
        if (data != null && data.length > 0xffff) {
            throw new IllegalArgumentException("Extra data too long: " + data.length);
        }
        extra = data;
    }

    /**
     * Gets the compression method for this {@code ZipEntry}.
     *
     * @return the compression method, either {@code DEFLATED}, {@code STORED}
     *         or -1 if the compression method has not been set.
     */
    public int getMethod() {
        return compressionMethod;
    }

    /**
     * Sets the compression method for this entry to either {@code DEFLATED} or {@code STORED}.
     * The default is {@code DEFLATED}, which will cause the size, compressed size, and CRC to be
     * set automatically, and the entry's data to be compressed. If you switch to {@code STORED}
     * note that you'll have to set the size (or compressed size; they must be the same, but it's
     * okay to only set one) and CRC yourself because they must appear <i>before</i> the user data
     * in the resulting zip file. See {@link #setSize} and {@link #setCrc}.
     * @throws IllegalArgumentException
     *             when value is not {@code DEFLATED} or {@code STORED}.
     */
    public void setMethod(int value) {
        if (value != STORED && value != DEFLATED) {
            throw new IllegalArgumentException("Bad method: " + value);
        }
        compressionMethod = value;
    }

    /**
     * Gets the name of this {@code ZipEntry}.
     *
     * <p><em>Security note:</em> Entry names can represent relative paths. {@code foo/../bar} or
     * {@code ../bar/baz}, for example. If the entry name is being used to construct a filename
     * or as a path component, it must be validated or sanitized to ensure that files are not
     * written outside of the intended destination directory.
     *
     * @return the entry name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the uncompressed size of this {@code ZipEntry}.
     *
     * @return the uncompressed size, or {@code -1} if the size has not been
     *         set.
     */
    public long getSize() {
        return size;
    }

    /**
     * Sets the uncompressed size of this {@code ZipEntry}.
     *
     * @param value the uncompressed size for this entry.
     * @throws IllegalArgumentException if {@code value < 0}.
     */
    public void setSize(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Bad size: " + value);
        }
        size = value;
    }

    /**
     * Gets the last modification time of this {@code ZipEntry}.
     *
     * @return the last modification time as the number of milliseconds since
     *         Jan. 1, 1970.
     */
    public long getTime() {
        if (time != -1) {
            GregorianCalendar cal = new GregorianCalendar();
            cal.set(Calendar.MILLISECOND, 0);
            cal.set(1980 + ((modDate >> 9) & 0x7f), ((modDate >> 5) & 0xf) - 1,
                    modDate & 0x1f, (time >> 11) & 0x1f, (time >> 5) & 0x3f,
                    (time & 0x1f) << 1);
            return cal.getTime().getTime();
        }
        return -1;
    }

    /**
     * Sets the modification time of this {@code ZipEntry}.
     *
     * @param value
     *            the modification time as the number of milliseconds since Jan.
     *            1, 1970.
     */
    public void setTime(long value) {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(new Date(value));
        int year = cal.get(Calendar.YEAR);
        if (year < 1980) {
            modDate = 0x21;
            time = 0;
        } else {
            modDate = cal.get(Calendar.DATE);
            modDate = (cal.get(Calendar.MONTH) + 1 << 5) | modDate;
            modDate = ((cal.get(Calendar.YEAR) - 1980) << 9) | modDate;
            time = cal.get(Calendar.SECOND) >> 1;
            time = (cal.get(Calendar.MINUTE) << 5) | time;
            time = (cal.get(Calendar.HOUR_OF_DAY) << 11) | time;
        }
    }

    /**
     * Determine whether or not this {@code ZipEntry} is a directory.
     *
     * @return {@code true} when this {@code ZipEntry} is a directory, {@code
     *         false} otherwise.
     */
    public boolean isDirectory() {
        return name.charAt(name.length() - 1) == '/';
    }

    /** @hide */
    public long getDataOffset() {
        return dataOffset;
    }

    /** @hide */
    public void setDataOffset(long value) {
        dataOffset = value;
    }

    /**
     * Returns the string representation of this {@code ZipEntry}.
     *
     * @return the string representation of this {@code ZipEntry}.
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("name:" + name);
        sb.append("\ncomment:" + comment);
        sb.append("\ntime:" + time);
        sb.append("\nsize:" + size);
        sb.append("\ncompressedSize:" + compressedSize);
        sb.append("\ncrc:" + crc);
        sb.append("\ncompressionMethod:" + compressionMethod);
        sb.append("\nmodDate:" + modDate);
        sb.append("\nextra length:" + extra.length);
        sb.append("\nlocalHeaderRelOffset:" + localHeaderRelOffset);
        sb.append("\ndataOffset:" + dataOffset);
        return sb.toString();
    }

    /**
     * Returns a deep copy of this zip entry.
     */
    @Override public Object clone() {
        try {
            TinkerZipEntry result = (TinkerZipEntry) super.clone();
            result.extra = extra != null ? extra.clone() : null;
            return result;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns the hash code for this {@code ZipEntry}.
     *
     * @return the hash code of the entry.
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TinkerZipEntry)) {
            return false;
        }
        return name.equals(((TinkerZipEntry) obj).name);
    }
}
