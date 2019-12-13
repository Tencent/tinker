package com.tencent.tinker.ziputils.ziputil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

/**
 * Created by tangyinsheng on 2018/11/20.
 */
public class AlignedZipOutputStream extends DeflaterOutputStream {
    /**
     * Constants migrated from ZipConstants
     */
    private static final long
            LOCSIG = 0x4034b50, EXTSIG = 0x8074b50, CENSIG = 0x2014b50, ENDSIG = 0x6054b50;

    /**
     * Constants migrated from ZipConstants
     */
    private static final int
            LOCHDR = 30, EXTHDR = 16;

    // 1980-01-01 00:00:00
    private static final int MOD_DATE_CONST = 0x21;
    private static final int TIME_CONST = 0;

    /**
     * General Purpose Bit Flags, Bit 3.
     * If this bit is set, the fields crc-32, compressed
     * size and uncompressed size are set to zero in the
     * local header.  The correct values are put in the
     * data descriptor immediately following the compressed
     * data.  (Note: PKZIP version 2.04g for DOS only
     * recognizes this bit for method 8 compression, newer
     * versions of PKZIP recognize this bit for any
     * compression method.)
     */
    private static final int GPBF_DATA_DESCRIPTOR_FLAG = 1 << 3;

    /**
     * General Purpose Bit Flags, Bit 11.
     * Language encoding flag (EFS).  If this bit is set,
     * the filename and comment fields for this file
     * must be encoded using UTF-8.
     */
    private static final int GPBF_UTF8_FLAG = 1 << 11;

    private static final byte[] EMPTY_BYTE_ARRAY = {};

    private static final byte[] ONE_ELEM_BYTE_ARRAY = {0};

    /**
     * Indicates deflated entries.
     */
    public static final int DEFLATED = ZipEntry.DEFLATED;

    /**
     * Indicates uncompressed entries.
     */
    public static final int STORED = ZipEntry.STORED;

    private static final int ZIPLocalHeaderVersionNeeded = 20;

    private byte[] commentBytes = EMPTY_BYTE_ARRAY;

    private final HashSet<String> entries = new HashSet<>();

    private int defaultCompressionMethod = DEFLATED;

    private int compressionLevel = Deflater.DEFAULT_COMPRESSION;

    private ByteArrayOutputStream cDir = new ByteArrayOutputStream();

    private ZipEntry currentEntry;

    private final CRC32 crc = new CRC32();

    private long crcDataSize = 0;

    private int offset = 0;
    private int nameLength;

    private byte[] nameBytes;

    private boolean finished = false;

    private boolean closed = false;

    private final int alignBytes;

    private int padding = 0;

    /**
     * Constructs a new {@code ZipOutputStream} that writes a zip file
     * to the given {@code OutputStream}.
     */
    public AlignedZipOutputStream(OutputStream os) {
        this(os, 4);
    }

    /**
     * Constructs a new {@code ZipOutputStream} that writes a zip file
     * to the given {@code OutputStream}.
     */
    public AlignedZipOutputStream(OutputStream os, int alignBytes) {
        super(os, new Deflater(Deflater.DEFAULT_COMPRESSION, true));
        this.alignBytes = alignBytes;
    }

    /**
     * Closes the current {@code ZipEntry}, if any, and the underlying output
     * stream. If the stream is already closed this method does nothing.
     *
     * @throws IOException
     *             If an error occurs closing the stream.
     */
    @Override
    public void close() throws IOException {
        // don't call super.close() because that calls finish() conditionally
        if (!closed) {
            finish();
            def.end();
            out.close();
            out = null;
            closed = true;
        }
    }

    /**
     * Closes the current {@code ZipEntry}. Any entry terminal data is written
     * to the underlying stream.
     *
     * @throws IOException
     *             If an error occurs closing the entry.
     */
    public void closeEntry() throws IOException {
        checkOpen();
        if (currentEntry == null) {
            return;
        }
        if (currentEntry.getMethod() == DEFLATED) {
            super.finish();
        }

        // Verify values for STORED types
        if (currentEntry.getMethod() == STORED) {
            if (crc.getValue() != currentEntry.getCrc()) {
                throw new ZipException("CRC mismatch");
            }
            if (currentEntry.getSize() != crcDataSize) {
                throw new ZipException("Size mismatch");
            }
        }

        int curOffset = LOCHDR;

        // Write the DataDescriptor
        if (currentEntry.getMethod() != STORED) {
            curOffset += EXTHDR;
            writeLong(out, EXTSIG);
            currentEntry.setCrc(crc.getValue());
            writeLong(out, currentEntry.getCrc());
            currentEntry.setCompressedSize(def.getTotalOut());
            writeLong(out, currentEntry.getCompressedSize());
            currentEntry.setSize(def.getTotalIn());
            writeLong(out, currentEntry.getSize());
        }
        // Update the CentralDirectory
        // http://www.pkware.com/documents/casestudies/APPNOTE.TXT
        int flags = currentEntry.getMethod() == STORED ? 0 : GPBF_DATA_DESCRIPTOR_FLAG;
        // Since gingerbread, we always set the UTF-8 flag on individual files.
        // Some tools insist that the central directory also have the UTF-8 flag.
        // http://code.google.com/p/android/issues/detail?id=20214
        flags |= GPBF_UTF8_FLAG;
        writeLong(cDir, CENSIG);
        writeShort(cDir, ZIPLocalHeaderVersionNeeded); // Version created
        writeShort(cDir, ZIPLocalHeaderVersionNeeded); // Version to extract
        writeShort(cDir, flags);
        writeShort(cDir, currentEntry.getMethod());
        writeShort(cDir, TIME_CONST);
        writeShort(cDir, MOD_DATE_CONST);
        writeLong(cDir, crc.getValue());
        if (currentEntry.getMethod() == DEFLATED) {
            curOffset += writeLong(cDir, def.getTotalOut());
            writeLong(cDir, def.getTotalIn());
        } else {
            curOffset += writeLong(cDir, crcDataSize);
            writeLong(cDir, crcDataSize);
        }
        curOffset += writeShort(cDir, nameLength);
        if (currentEntry.getExtra() != null) {
            curOffset += writeShort(cDir, currentEntry.getExtra().length);
        } else {
            writeShort(cDir, 0);
        }

        String comment = currentEntry.getComment();
        byte[] commentBytes = EMPTY_BYTE_ARRAY;
        if (comment != null) {
            commentBytes = comment.getBytes(Charset.forName("UTF-8"));
        }
        writeShort(cDir, commentBytes.length); // Comment length.
        writeShort(cDir, 0); // Disk Start
        writeShort(cDir, 0); // Internal File Attributes
        writeLong(cDir, 0); // External File Attributes
        writeLong(cDir, offset); // Relative Offset of Local File Header
        cDir.write(nameBytes);
        nameBytes = null;
        if (currentEntry.getExtra() != null) {
            cDir.write(currentEntry.getExtra());
        }
        offset += curOffset + padding;
        padding = 0;
        if (commentBytes.length > 0) {
            cDir.write(commentBytes);
        }
        currentEntry = null;
        crc.reset();
        crcDataSize = 0;
        def.reset();
    }

    /**
     * Indicates that all entries have been written to the stream. Any terminal
     * information is written to the underlying stream.
     *
     * @throws IOException
     *             if an error occurs while terminating the stream.
     */
    @Override
    public void finish() throws IOException {
        checkOpen();
        if (finished) {
            return;
        }
        if (entries.isEmpty()) {
            throw new ZipException("No entries");
        }
        if (currentEntry != null) {
            closeEntry();
        }
        int cdirSize = cDir.size();
        // Write Central Dir End
        writeLong(cDir, ENDSIG);
        writeShort(cDir, 0); // Disk Number
        writeShort(cDir, 0); // Start Disk
        writeShort(cDir, entries.size()); // Number of entries
        writeShort(cDir, entries.size()); // Number of entries
        writeLong(cDir, cdirSize); // Size of central dir
        writeLong(cDir, offset + padding); // Offset of central dir
        writeShort(cDir, commentBytes.length);
        if (commentBytes.length > 0) {
            cDir.write(commentBytes);
        }
        // Write the central directory.
        cDir.writeTo(out);
        cDir = null;
        finished = true;
    }

    private int getPaddingByteCount(ZipEntry entry, int entryFileOffset) {
        if (entry.getMethod() != STORED || alignBytes == 0) {
            return 0;
        }
        return (alignBytes - (entryFileOffset % alignBytes)) % alignBytes;
    }

    private void makePaddingToStream(OutputStream os, int padding) throws IOException {
        if (padding <= 0) {
            return;
        }
        while (padding-- > 0) {
            os.write(0);
        }
    }

    /**
     * Writes entry information to the underlying stream. Data associated with
     * the entry can then be written using {@code write()}. After data is
     * written {@code closeEntry()} must be called to complete the writing of
     * the entry to the underlying stream.
     *
     * @param ze
     *            the {@code ZipEntry} to store.
     * @throws IOException
     *             If an error occurs storing the entry.
     * @see #write
     */
    public void putNextEntry(ZipEntry ze) throws IOException {
        if (currentEntry != null) {
            closeEntry();
        }

        // Did this ZipEntry specify a method, or should we use the default?
        int method = ze.getMethod();
        if (method == -1) {
            method = defaultCompressionMethod;
        }

        // If the method is STORED, check that the ZipEntry was configured appropriately.
        if (method == STORED) {
            if (ze.getCompressedSize() == -1) {
                ze.setCompressedSize(ze.getSize());
            } else if (ze.getSize() == -1) {
                ze.setSize(ze.getCompressedSize());
            }
            if (ze.getCrc() == -1) {
                throw new ZipException("STORED entry missing CRC");
            }
            if (ze.getSize() == -1) {
                throw new ZipException("STORED entry missing size");
            }
            if (ze.getSize() != ze.getCompressedSize()) {
                throw new ZipException("STORED entry size/compressed size mismatch");
            }
        }

        checkOpen();

        if (entries.contains(ze.getName())) {
            throw new ZipException("Entry already exists: " + ze.getName());
        }
        if (entries.size() == 64*1024-1) {
            throw new ZipException("Too many entries for the zip file format's 16-bit entry count");
        }
        nameBytes = ze.getName().getBytes(Charset.forName("UTF-8"));
        nameLength = nameBytes.length;
        if (nameLength > 0xffff) {
            throw new IllegalArgumentException("Name too long: " + nameLength + " UTF-8 bytes");
        }

        def.setLevel(compressionLevel);
        ze.setMethod(method);

        currentEntry = ze;
        entries.add(currentEntry.getName());

        // Local file header.
        // http://www.pkware.com/documents/casestudies/APPNOTE.TXT
        int flags = (method == STORED) ? 0 : GPBF_DATA_DESCRIPTOR_FLAG;
        // Java always outputs UTF-8 filenames. (Before Java 7, the RI didn't set this flag and used
        // modified UTF-8. From Java 7, it sets this flag and uses normal UTF-8.)
        flags |= GPBF_UTF8_FLAG;
        writeLong(out, LOCSIG); // Entry header
        writeShort(out, ZIPLocalHeaderVersionNeeded); // Extraction version
        writeShort(out, flags);
        writeShort(out, method);
        if (currentEntry.getTime() == -1) {
            currentEntry.setTime(System.currentTimeMillis());
        }
        writeShort(out, TIME_CONST);
        writeShort(out, MOD_DATE_CONST);

        if (method == STORED) {
            writeLong(out, currentEntry.getCrc());
            writeLong(out, currentEntry.getSize());
            writeLong(out, currentEntry.getSize());
        } else {
            writeLong(out, 0);
            writeLong(out, 0);
            writeLong(out, 0);
        }
        writeShort(out, nameLength);
        final int currDataOffset = offset + LOCHDR + nameLength + (currentEntry.getExtra() != null ? currentEntry.getExtra().length : 0);
        padding = getPaddingByteCount(currentEntry, currDataOffset);
        if (currentEntry.getExtra() != null) {
            writeShort(out, currentEntry.getExtra().length + padding);
        } else {
            writeShort(out, padding);
        }
        out.write(nameBytes);
        if (currentEntry.getExtra() != null) {
            out.write(currentEntry.getExtra());
        }
        makePaddingToStream(out, padding);
    }

    /**
     * Sets the comment associated with the file being written.
     * @throws IllegalArgumentException if the comment is >= 64 Ki UTF-8 bytes.
     */
    public void setComment(String comment) {
        if (comment == null) {
            this.commentBytes = null;
            return;
        }

        byte[] newCommentBytes = comment.getBytes(Charset.forName("UTF-8"));
        if (newCommentBytes.length > 0xffff) {
            throw new IllegalArgumentException("Comment too long: " + newCommentBytes.length + " bytes");
        }
        this.commentBytes = newCommentBytes;
    }

    /**
     * Sets the <a href="Deflater.html#compression_level">compression level</a> to be used
     * for writing entry data.
     */
    public void setLevel(int level) {
        if (level < Deflater.DEFAULT_COMPRESSION || level > Deflater.BEST_COMPRESSION) {
            throw new IllegalArgumentException("Bad level: " + level);
        }
        compressionLevel = level;
    }

    /**
     * Sets the default compression method to be used when a {@code ZipEntry} doesn't
     * explicitly specify a method. See {@link ZipEntry#setMethod} for more details.
     */
    public void setMethod(int method) {
        if (method != STORED && method != DEFLATED) {
            throw new IllegalArgumentException("Bad method: " + method);
        }
        defaultCompressionMethod = method;
    }

    private long writeLong(OutputStream os, long i) throws IOException {
        // Write out the long value as an unsigned int
        os.write((int) (i & 0xFF));
        os.write((int) (i >> 8) & 0xFF);
        os.write((int) (i >> 16) & 0xFF);
        os.write((int) (i >> 24) & 0xFF);
        return i;
    }

    private int writeShort(OutputStream os, int i) throws IOException {
        if (i > 0xFFFF) {
            throw new IllegalArgumentException("value " + i + " is too large for type 'short'.");
        }
        os.write(i & 0xFF);
        os.write((i >> 8) & 0xFF);
        return i;
    }

    @Override
    public void write(int b) throws IOException {
        // Use static pre-allocated byte array to avoid memory fragment.
        final byte[] buf = ONE_ELEM_BYTE_ARRAY;
        buf[0] = (byte)(b & 0xff);
        write(buf, 0, 1);
    }

    /**
     * Writes data for the current entry to the underlying stream.
     *
     * @exception IOException
     *                If an error occurs writing to the stream
     */
    @Override
    public void write(byte[] buffer, int offset, int byteCount) throws IOException {
        checkOffsetAndCount(buffer.length, offset, byteCount);
        if (currentEntry == null) {
            throw new ZipException("No active entry");
        }

        if (currentEntry.getMethod() == STORED) {
            out.write(buffer, offset, byteCount);
        } else {
            super.write(buffer, offset, byteCount);
        }
        crc.update(buffer, offset, byteCount);
        crcDataSize += byteCount;
    }

    private void checkOffsetAndCount(int arrayLength, int offset, int count) {
        if ((offset | count) < 0 || offset > arrayLength || arrayLength - offset < count) {
            throw new ArrayIndexOutOfBoundsException("length=" + arrayLength + "; regionStart=" + offset
                    + "; regionLength=" + count);
        }
    }

    private void checkOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
    }
}
