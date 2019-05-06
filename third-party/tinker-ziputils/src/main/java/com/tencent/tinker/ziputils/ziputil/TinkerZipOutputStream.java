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

// import libcore.util.CountingOutputStream;
// import libcore.util.EmptyArray;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

// import java.nio.charset.StandardCharsets;
// import java.util.Arrays;

/**
 * modify by zhangshaowen on 16/6/7.
 * remove zip64
 * const time, modDate
 * remove entry extra
 * remove entry comment
 *
 * Used to write (compress) data into zip files.
 *
 * <p>{@code ZipOutputStream} is used to write {@link TinkerZipEntry}s to the underlying
 * stream. Output from {@code ZipOutputStream} can be read using {@link TinkerZipFile}
 * or {@link ZipInputStream}.
 *
 * <p>While {@code DeflaterOutputStream} can write compressed zip file
 * entries, this extension can write uncompressed entries as well.
 * Use {@link TinkerZipEntry#setMethod} or @link #setMethod with the {@link TinkerZipEntry#STORED} flag.
 *
 * <h3>Example</h3>
 * <p>Using {@code ZipOutputStream} is a little more complicated than {@link GZIPOutputStream}
 * because zip files are containers that can contain multiple files. This code creates a zip
 * file containing several files, similar to the {@code zip(1)} utility.
 * <pre>
 * OutputStream os = ...
 * ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));
 * try {
 *     for (int i = 0; i < fileCount; ++i) {
 *         String filename = ...
 *         byte[] bytes = ...
 *         ZipEntry entry = new ZipEntry(filename);
 *         zos.putNextEntry(entry);
 *         zos.write(bytes);
 *         zos.closeEntry();
 *     }
 * } finally {
 *     zos.close();
 * }
 * </pre>
 */
public class TinkerZipOutputStream extends FilterOutputStream implements ZipConstants {
    /**
     * Indicates deflated entries.
     */
    public static final int       DEFLATED                 = 8;
    /**
     * Indicates uncompressed entries.
     */
    public static final int       STORED                   = 0;
    public static final byte[]    BYTE                     = new byte[0];
    //zhangshaowen edit here, we just want the same time and modDate
    //remove random fields
    final static int              TIME_CONST               = 40691;
    final static int              MOD_DATE_CONST           = 18698;
    private static final int      ZIP_VERSION_2_0          = 20; // Zip specification version 2.0.
    private static final byte[] ZIP64_PLACEHOLDER_BYTES =
            new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
    private final HashSet<String> entries                  = new HashSet<String>();
    /**
     * Whether we force all entries in this archive to have a zip64 extended info record.
     * This of course implies that the {@code currentEntryNeedsZip64} and
     * {@code archiveNeedsZip64EocdRecord} are always {@code true}.
     */
    private final boolean forceZip64;
    private byte[]                commentBytes             = BYTE;
    private int                   defaultCompressionMethod = DEFLATED;
    // private int compressionLevel = Deflater.DEFAULT_COMPRESSION;
    private ByteArrayOutputStream cDir                     = new ByteArrayOutputStream();
    private TinkerZipEntry currentEntry;
    // private final CRC32 crc = new CRC32();
    private long offset = 0;
    /** The charset-encoded name for the current entry. */
    private byte[] nameBytes;
    /** The charset-encoded comment for the current entry. */
    private byte[] entryCommentBytes;
    /**
     * Whether this zip file needs a Zip64 EOCD record / zip64 EOCD record locator. This
     * will be true if we wrote an entry whose size or compressed size was too large for
     * the standard zip format or if we exceeded the maximum number of entries allowed
     * in the standard format.
     */
    private boolean archiveNeedsZip64EocdRecord;
    /**
     * Whether the current entry being processed needs a zip64 extended info record. This
     * will be true if the entry is too large for the standard zip format or if the offset
     * to the start of the current entry header is greater than 0xFFFFFFFF.
     */
    private boolean currentEntryNeedsZip64;
    /**
     * Constructs a new {@code ZipOutputStream} that writes a zip file to the given
     * {@code OutputStream}.
     *
     * <p>UTF-8 will be used to encode the file comment, entry names and comments.
     */
    public TinkerZipOutputStream(OutputStream os) {
        this(os, false /* forceZip64 */);
    }
    /**
     * @hide for testing only.
     */
    public TinkerZipOutputStream(OutputStream os, boolean forceZip64) {
        super(os);
        this.forceZip64 = forceZip64;
    }

    /**
     * Sets the default compression method to be used when a {@code ZipEntry} doesn't
     * explicitly specify a method. See {@link TinkerZipEntry#setMethod} for more details.
     */
    /*public void setMethod(int method) {
        if (method != STORED && method != DEFLATED) {
            throw new IllegalArgumentException("Bad method: " + method);
        }
        defaultCompressionMethod = method;
    }*/
    static long writeLongAsUint32(OutputStream os, long i) throws IOException {
        // Write out the long value as an unsigned int
        os.write((int) (i & 0xFF));
        os.write((int) (i >> 8) & 0xFF);
        os.write((int) (i >> 16) & 0xFF);
        os.write((int) (i >> 24) & 0xFF);
        return i;
    }

    static long writeLongAsUint64(OutputStream os, long i) throws IOException {
        int i1 = (int) i;
        os.write(i1 & 0xFF);
        os.write((i1 >> 8) & 0xFF);
        os.write((i1 >> 16) & 0xFF);
        os.write((i1 >> 24) & 0xFF);
        int i2 = (int) (i >> 32);
        os.write(i2 & 0xFF);
        os.write((i2 >> 8) & 0xFF);
        os.write((i2 >> 16) & 0xFF);
        os.write((i2 >> 24) & 0xFF);
        return i;
    }

    static int writeIntAsUint16(OutputStream os, int i) throws IOException {
        os.write(i & 0xFF);
        os.write((i >> 8) & 0xFF);
        return i;
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
        if (out != null) {
            finish();
            // def.end();
            out.close();
            out = null;
        }
    }
    /*private void checkAndSetZip64Requirements(ZipEntry entry) {
        final long totalBytesWritten = getBytesWritten();
        final long entriesWritten = entries.size();
        currentEntryNeedsZip64 = false;
        if (forceZip64) {
            currentEntryNeedsZip64 = true;
            archiveNeedsZip64EocdRecord = true;
            return;
        }
        // In this particular case, we'll write a zip64 eocd record locator and a zip64 eocd
        // record but we won't actually need zip64 extended info records for any of the individual
        // entries (unless they trigger the checks below).
        if (entriesWritten == 64*1024 - 1) {
            archiveNeedsZip64EocdRecord = true;
        }
        // Check whether we'll need to write out a zip64 extended info record in both the local file header
        // and the central directory. In addition, we will need a zip64 eocd record locator
        // and record to mark this archive as zip64.
        //
        // TODO: This is an imprecise check. When method != STORED it's possible that the compressed
        // size will be (slightly) larger than the actual size. How can we improve this ?
        if (totalBytesWritten > Zip64.MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE ||
                (entry.getSize() > Zip64.MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE)) {
            currentEntryNeedsZip64 = true;
            archiveNeedsZip64EocdRecord = true;
        }
    }*/

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
        /*if (currentEntry.getMethod() == DEFLATED) {
            super.finish();
        }
        // Verify values for STORED types
        if (currentEntry.getMethod() == STORED) {
            if (crc.getValue() != currentEntry.crc) {
                throw new ZipException("CRC mismatch");
            }
            if (currentEntry.size != crc.tbytes) {
                throw new ZipException("Size mismatch");
            }
        }*/
        long curOffset = LOCHDR;
        // Write the DataDescriptor
        if (currentEntry.getMethod() != STORED) {
            curOffset += EXTHDR;
            // Data descriptor signature and CRC are 4 bytes each for both zip and zip64.
            writeLongAsUint32(out, EXTSIG);
            /*writeLongAsUint32(out, currentEntry.crc = crc.getValue());
            currentEntry.compressedSize = def.getBytesWritten();
            currentEntry.size = def.getBytesRead();*/
            writeLongAsUint32(out, currentEntry.crc);
            /*if (currentEntryNeedsZip64) {
                // We need an additional 8 bytes to store 8 byte compressed / uncompressed
                // sizes.
                curOffset += 8;
                writeLongAsUint64(out, currentEntry.compressedSize);
                writeLongAsUint64(out, currentEntry.size);
            } else {
                writeLongAsUint32(out, currentEntry.compressedSize);
                writeLongAsUint32(out, currentEntry.size);
            }*/
            writeLongAsUint32(out, currentEntry.compressedSize);
            writeLongAsUint32(out, currentEntry.size);
        }
        // Update the CentralDirectory
        // http://www.pkware.com/documents/casestudies/APPNOTE.TXT
        int flags = currentEntry.getMethod() == STORED ? 0 : TinkerZipFile.GPBF_DATA_DESCRIPTOR_FLAG;
        // Since gingerbread, we always set the UTF-8 flag on individual files if appropriate.
        // Some tools insist that the central directory have the UTF-8 flag.
        // http://code.google.com/p/android/issues/detail?id=20214
        flags |= TinkerZipFile.GPBF_UTF8_FLAG;
        writeLongAsUint32(cDir, CENSIG);
        writeIntAsUint16(cDir, ZIP_VERSION_2_0); // Version this file was made by.
        writeIntAsUint16(cDir, ZIP_VERSION_2_0); // Minimum version needed to extract.
        writeIntAsUint16(cDir, flags);
        writeIntAsUint16(cDir, currentEntry.getMethod());
        writeIntAsUint16(cDir, currentEntry.time);
        writeIntAsUint16(cDir, currentEntry.modDate);
        // writeLongAsUint32(cDir, crc.getValue());
        writeLongAsUint32(cDir, currentEntry.crc);
        if (currentEntry.getMethod() == DEFLATED) {
            /*currentEntry.setCompressedSize(def.getBytesWritten());
            currentEntry.setSize(def.getBytesRead());*/
            curOffset += currentEntry.getCompressedSize();
        } else {
            /*currentEntry.setCompressedSize(crc.tbytes);
            currentEntry.setSize(crc.tbytes);*/
            curOffset += currentEntry.getSize();
        }
        /*if (currentEntryNeedsZip64) {
            // Refresh the extended info with the compressed size / size before
            // writing it to the central directory.
            Zip64.refreshZip64ExtendedInfo(currentEntry);
            // NOTE: We would've written out the zip64 extended info locator to the entry
            // extras while constructing the local file header. There's no need to do it again
            // here. If we do, there will be a size mismatch since we're calculating offsets
            // based on the *current* size of the extra data and not based on the size
            // at the point of writing the LFH.
            writeLongAsUint32(cDir, Zip64.MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE);
            writeLongAsUint32(cDir, Zip64.MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE);
        } else {
            writeLongAsUint32(cDir, currentEntry.getCompressedSize());
            writeLongAsUint32(cDir, currentEntry.getSize());
        }*/
        writeLongAsUint32(cDir, currentEntry.getCompressedSize());
        writeLongAsUint32(cDir, currentEntry.getSize());
        curOffset += writeIntAsUint16(cDir, nameBytes.length);
        if (currentEntry.extra != null) {
            curOffset += writeIntAsUint16(cDir, currentEntry.extra.length);
        } else {
            writeIntAsUint16(cDir, 0);
        }
        writeIntAsUint16(cDir, entryCommentBytes.length); // Comment length.
        writeIntAsUint16(cDir, 0); // Disk Start
        writeIntAsUint16(cDir, 0); // Internal File Attributes
        writeLongAsUint32(cDir, 0); // External File Attributes
        /*if (currentEntryNeedsZip64) {
            writeLongAsUint32(cDir, Zip64.MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE);
        } else {
            writeLongAsUint32(cDir, currentEntry.localHeaderRelOffset);
        }*/
        writeLongAsUint32(cDir, currentEntry.localHeaderRelOffset);
        cDir.write(nameBytes);
        nameBytes = null;
        if (currentEntry.extra != null) {
            cDir.write(currentEntry.extra);
        }
        offset += curOffset;
        if (entryCommentBytes.length > 0) {
            cDir.write(entryCommentBytes);
            entryCommentBytes = BYTE;
        }
        currentEntry = null;
        /*crc.reset();
        def.reset();
        done = false;*/
    }
    /**
     * Sets the <a href="Deflater.html#compression_level">compression level</a> to be used
     * for writing entry data.
     */
    /*public void setLevel(int level) {
        if (level < Deflater.DEFAULT_COMPRESSION || level > Deflater.BEST_COMPRESSION) {
            throw new IllegalArgumentException("Bad level: " + level);
        }
        compressionLevel = level;
    }*/

    /**
     * Indicates that all entries have been written to the stream. Any terminal
     * information is written to the underlying stream.
     *
     * @throws IOException
     *             if an error occurs while terminating the stream.
     */
    // @Override
    public void finish() throws IOException {
        // TODO: is there a bug here? why not checkOpen?
        if (out == null) {
            throw new IOException("Stream is closed");
        }
        if (cDir == null) {
            return;
        }
        if (entries.isEmpty()) {
            throw new ZipException("No entries");
        }
        if (currentEntry != null) {
            closeEntry();
        }
        int cdirEntriesSize = cDir.size();
        /*if (archiveNeedsZip64EocdRecord) {
            Zip64.writeZip64EocdRecordAndLocator(cDir, entries.size(), offset, cdirEntriesSize);
        }*/
        // Write Central Dir End
        writeLongAsUint32(cDir, ENDSIG);
        writeIntAsUint16(cDir, 0); // Disk Number
        writeIntAsUint16(cDir, 0); // Start Disk
        // Instead of trying to figure out *why* this archive needed a zip64 eocd record,
        // just delegate all these values to the zip64 eocd record.
        if (archiveNeedsZip64EocdRecord) {
            writeIntAsUint16(cDir, 0xFFFF); // Number of entries
            writeIntAsUint16(cDir, 0xFFFF); // Number of entries
            writeLongAsUint32(cDir, 0xFFFFFFFF); // Size of central dir
            writeLongAsUint32(cDir, 0xFFFFFFFF); // Offset of central dir;
        } else {
            writeIntAsUint16(cDir, entries.size()); // Number of entries
            writeIntAsUint16(cDir, entries.size()); // Number of entries
            writeLongAsUint32(cDir, cdirEntriesSize); // Size of central dir
            writeLongAsUint32(cDir, offset); // Offset of central dir
        }
        writeIntAsUint16(cDir, commentBytes.length);
        if (commentBytes.length > 0) {
            cDir.write(commentBytes);
        }
        // Write the central directory.
        cDir.writeTo(out);
        cDir = null;
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
    public void putNextEntry(TinkerZipEntry ze) throws IOException {
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
            if (ze.size != ze.compressedSize) {
                throw new ZipException("STORED entry size/compressed size mismatch");
            }
        }
        checkOpen();
        // checkAndSetZip64Requirements(ze);

        //zhangshaowen edit here, we just want the same time and modDate
        ze.comment = null;
        ze.extra = null;
        ze.time = TIME_CONST;
        ze.modDate = MOD_DATE_CONST;

        nameBytes = ze.name.getBytes(StandardCharsets.UTF_8);
        checkSizeIsWithinShort("Name", nameBytes);
        entryCommentBytes = BYTE;
        if (ze.comment != null) {
            entryCommentBytes = ze.comment.getBytes(StandardCharsets.UTF_8);
            // The comment is not written out until the entry is finished, but it is validated here
            // to fail-fast.
            checkSizeIsWithinShort("Comment", entryCommentBytes);
        }
        // def.setLevel(compressionLevel);
        ze.setMethod(method);
        currentEntry = ze;

        currentEntry.localHeaderRelOffset = offset;
        entries.add(currentEntry.name);
        // Local file header.
        // http://www.pkware.com/documents/casestudies/APPNOTE.TXT
        int flags = (method == STORED) ? 0 : TinkerZipFile.GPBF_DATA_DESCRIPTOR_FLAG;
        // Java always outputs UTF-8 filenames. (Before Java 7, the RI didn't set this flag and used
        // modified UTF-8. From Java 7, when using UTF_8 it sets this flag and uses normal UTF-8.)
        flags |= TinkerZipFile.GPBF_UTF8_FLAG;
        writeLongAsUint32(out, LOCSIG); // Entry header
        writeIntAsUint16(out, ZIP_VERSION_2_0); // Minimum version needed to extract.
        writeIntAsUint16(out, flags);
        writeIntAsUint16(out, method);

        // zhangshaowen edit here, we just want the same time and modDate
        // if (currentEntry.getTime() == -1) {
        //     currentEntry.setTime(System.currentTimeMillis());
        // }
        writeIntAsUint16(out, currentEntry.time);
        writeIntAsUint16(out, currentEntry.modDate);
        if (method == STORED) {
            writeLongAsUint32(out, currentEntry.crc);
            /*if (currentEntryNeedsZip64) {
                // NOTE: According to the spec, we're allowed to use these fields under zip64
                // as long as the sizes are <= 4G (and omit writing the zip64 extended information header).
                //
                // For simplicity, we write the zip64 extended info here even if we only need it
                // in the central directory (i.e, the case where we're turning on zip64 because the
                // offset to this entries LFH is > 0xFFFFFFFF).
                out.write(ZIP64_PLACEHOLDER_BYTES);  // compressed size
                out.write(ZIP64_PLACEHOLDER_BYTES);  // uncompressed size
            } else {
                writeLongAsUint32(out, currentEntry.size);
                writeLongAsUint32(out, currentEntry.size);
            }*/
            writeLongAsUint32(out, currentEntry.size);
            writeLongAsUint32(out, currentEntry.size);
        } else {
            writeLongAsUint32(out, 0);
            writeLongAsUint32(out, 0);
            writeLongAsUint32(out, 0);
        }
        writeIntAsUint16(out, nameBytes.length);
        /*if (currentEntryNeedsZip64) {
            Zip64.insertZip64ExtendedInfoToExtras(currentEntry);
        }*/
        if (currentEntry.extra != null) {
            writeIntAsUint16(out, currentEntry.extra.length);
        } else {
            writeIntAsUint16(out, 0);
        }
        out.write(nameBytes);
        if (currentEntry.extra != null) {
            out.write(currentEntry.extra);
        }
    }

    /**
     * Sets the comment associated with the file being written. See {@link TinkerZipFile#getComment}.
     * @throws IllegalArgumentException if the comment is >= 64 Ki encoded bytes.
     */
    public void setComment(String comment) {
        if (comment == null) {
            this.commentBytes = BYTE;
            return;
        }
        byte[] newCommentBytes = comment.getBytes(StandardCharsets.UTF_8);
        checkSizeIsWithinShort("Comment", newCommentBytes);
        this.commentBytes = newCommentBytes;
    }

    /**
     * Writes data for the current entry to the underlying stream.
     *
     * @throws IOException
     *                If an error occurs writing to the stream
     */
    @Override
    public void write(byte[] buffer, int offset, int byteCount) throws IOException {
        Arrays.checkOffsetAndCount(buffer.length, offset, byteCount);
        if (currentEntry == null) {
            throw new ZipException("No active entry");
        }
        /*final long totalBytes = crc.tbytes + byteCount;
        if ((totalBytes > Zip64.MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE) && !currentEntryNeedsZip64) {
            throw new IOException("Zip entry size (" + totalBytes +
                    " bytes) cannot be represented in the zip format (needs Zip64)." +
                    " Set the entry length using ZipEntry#setLength to use Zip64 where necessary.");
        }*/
        if (currentEntry.getMethod() == STORED) {
            out.write(buffer, offset, byteCount);
        } else {
            out.write(buffer, offset, byteCount);
        }
        // crc.update(buffer, offset, byteCount);
    }
    private void checkOpen() throws IOException {
        if (cDir == null) {
            throw new IOException("Stream is closed");
        }
    }
    private void checkSizeIsWithinShort(String property, byte[] bytes) {
        if (bytes.length > 0xffff) {
            throw new IllegalArgumentException(property
                + " too long in UTF-8:"
                + bytes.length
                + " bytes");
        }
    }
    /*private long getBytesWritten() {
        // This cast is somewhat messy but less error prone than keeping an
        // CountingOutputStream reference around in addition to the FilterOutputStream's
        // out.
        return ((CountingOutputStream) out).getCount();
    }*/
}
