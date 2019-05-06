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

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

// import libcore.io.IoUtils;

/**
 * modify by zhangshaowen on 16/6/7.
 *
 * This class provides random read access to a zip file. You pay more to read
 * the zip file's central directory up front (from the constructor), but if you're using
 * {@link #getEntry} to look up multiple files by name, you get the benefit of this index.
 *
 * <p>If you only want to iterate through all the files (using {@link #entries()}, you should
 * consider {@link ZipInputStream}, which provides stream-like read access to a zip file and
 * has a lower up-front cost because you don't pay to build an in-memory index.
 *
 * <p>If you want to create a zip file, use {@link ZipOutputStream}. There is no API for updating
 * an existing zip file.
 */
public class TinkerZipFile implements Closeable, ZipConstants {
    /**
     * Open zip file for reading.
     */
    public static final int OPEN_READ = 1;
    /**
     * Delete zip file when closed.
     */
    public static final int OPEN_DELETE = 4;
    /**
     * General Purpose Bit Flags, Bit 0.
     * If set, indicates that the file is encrypted.
     */
    static final int GPBF_ENCRYPTED_FLAG = 1 << 0;
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
    static final int GPBF_DATA_DESCRIPTOR_FLAG = 1 << 3;
    /**
     * General Purpose Bit Flags, Bit 11.
     * Language encoding flag (EFS).  If this bit is set,
     * the filename and comment fields for this file
     * must be encoded using UTF-8.
     */
    static final int GPBF_UTF8_FLAG = 1 << 11;
    /**
     * Supported General Purpose Bit Flags Mask.
     * Bit mask of bits not supported.
     * Note: The only bit that we will enforce at this time
     * is the encrypted bit. Although other bits are not supported,
     * we must not enforce them as this could break some legitimate
     * use cases (See http://b/8617715).
     */
    static final int GPBF_UNSUPPORTED_MASK = GPBF_ENCRYPTED_FLAG;
    private final String filename;
    private final LinkedHashMap<String, TinkerZipEntry> entries = new LinkedHashMap<String, TinkerZipEntry>();
    private File fileToDeleteOnClose;
    private RandomAccessFile raf;
    private String comment;

    /**
     * Constructs a new {@code ZipFile} allowing read access to the contents of the given file.
     *
     * <p>UTF-8 is used to decode all comments and entry names in the file.
     *
     * @throws ZipException if a zip error occurs.
     * @throws IOException if an {@code IOException} occurs.
     */
    public TinkerZipFile(File file) throws ZipException, IOException {
        this(file, OPEN_READ);
    }
    /**
     * Constructs a new {@code ZipFile} allowing read access to the contents of the given file.
     *
     * <p>UTF-8 is used to decode all comments and entry names in the file.
     *
     * @throws IOException if an IOException occurs.
     */
    public TinkerZipFile(String name) throws IOException {
        this(new File(name), OPEN_READ);
    }
    /**
     * Constructs a new {@code ZipFile} allowing access to the given file.
     *
     * <p>UTF-8 is used to decode all comments and entry names in the file.
     *
     * <p>The {@code mode} must be either {@code OPEN_READ} or {@code OPEN_READ|OPEN_DELETE}.
     * If the {@code OPEN_DELETE} flag is supplied, the file will be deleted at or before the
     * time that the {@code ZipFile} is closed (the contents will remain accessible until
     * this {@code ZipFile} is closed); it also calls {@code File.deleteOnExit}.
     *
     * @throws IOException if an {@code IOException} occurs.
     */
    public TinkerZipFile(File file, int mode) throws IOException {
        filename = file.getPath();
        if (mode != OPEN_READ && mode != (OPEN_READ | OPEN_DELETE)) {
            throw new IllegalArgumentException("Bad mode: " + mode);
        }
        if ((mode & OPEN_DELETE) != 0) {
            fileToDeleteOnClose = file;
            fileToDeleteOnClose.deleteOnExit();
        } else {
            fileToDeleteOnClose = null;
        }
        raf = new RandomAccessFile(filename, "r");

        readCentralDir();
        // guard.open("close");
    }

    /**
     * Returns true if the string is null or 0-length.
     * @param str the string to be examined
     * @return true if str is null or zero length
     */
    public static boolean isEmpty(CharSequence str) {
        if (str == null || str.length() == 0) {
            return true;
        }
        return false;
    }
    /*@Override protected void finalize() throws IOException {
        try {
            if (guard != null) {
                guard.warnIfOpen();
            }
        } finally {
            try {
                super.finalize();
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }
    }*/

    /**
     * Returns true if a and b are equal, including if they are both null.
     * <p><i>Note: In platform versions 1.1 and earlier, this method only worked well if
     * both the arguments were instances of String.</i></p>
     * @param a first CharSequence to check
     * @param b second CharSequence to check
     * @return true if a and b are equal
     */
    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        int length;
        if (a != null && b != null && (length = a.length()) == b.length()) {
            if (a instanceof String && b instanceof String) {
                return a.equals(b);
            } else {
                for (int i = 0; i < length; i++) {
                    if (a.charAt(i) != b.charAt(i)) return false;
                }
                return true;
            }
        }
        return false;
    }

    private static EocdRecord parseEocdRecord(RandomAccessFile raf, long offset, boolean isZip64) throws IOException {
        raf.seek(offset);
        // Read the End Of Central Directory. ENDHDR includes the signature bytes,
        // which we've already read.
        byte[] eocd = new byte[ENDHDR - 4];
        raf.readFully(eocd);
        BufferIterator it = HeapBufferIterator.iterator(eocd, 0, eocd.length, ByteOrder.LITTLE_ENDIAN);
        final long numEntries;
        final long centralDirOffset;
        if (isZip64) {
            numEntries = -1;
            centralDirOffset = -1;
            // If we have a zip64 end of central directory record, we skip through the regular
            // end of central directory record and use the information from the zip64 eocd record.
            // We're still forced to read the comment length (below) since it isn't present in the
            // zip64 eocd record.
            it.skip(16);
        } else {
            // If we don't have a zip64 eocd record, we read values from the "regular"
            // eocd record.
            int diskNumber = it.readShort() & 0xffff;
            int diskWithCentralDir = it.readShort() & 0xffff;
            numEntries = it.readShort() & 0xffff;
            int totalNumEntries = it.readShort() & 0xffff;
            it.skip(4); // Ignore centralDirSize.
            centralDirOffset = ((long) it.readInt()) & 0xffffffffL;
            if (numEntries != totalNumEntries || diskNumber != 0 || diskWithCentralDir != 0) {
                throw new ZipException("Spanned archives not supported");
            }
        }
        final int commentLength = it.readShort() & 0xffff;
        return new EocdRecord(numEntries, centralDirOffset, commentLength);
    }

    static void throwZipException(String filename, long fileSize, String entryName, long localHeaderRelOffset, String msg, int magic) throws ZipException {
        final String hexString = Integer.toHexString(magic);
        throw new ZipException("file name:" + filename
                               + ", file size" + fileSize
                               + ", entry name:" + entryName
                               + ", entry localHeaderRelOffset:" + localHeaderRelOffset
                               + ", "
                               + msg + " signature not found; was " + hexString);
    }

    /**
     * Closes this zip file. This method is idempotent. This method may cause I/O if the
     * zip file needs to be deleted.
     *
     * @throws IOException
     *             if an IOException occurs.
     */
    public void close() throws IOException {
        // guard.close();
        RandomAccessFile localRaf = raf;
        if (localRaf != null) { // Only close initialized instances
            synchronized (localRaf) {
                raf = null;
                localRaf.close();
            }
            if (fileToDeleteOnClose != null) {
                fileToDeleteOnClose.delete();
                fileToDeleteOnClose = null;
            }
        }
    }

    private void checkNotClosed() {
        if (raf == null) {
            throw new IllegalStateException("Zip file closed");
        }
    }

    /**
     * Returns an enumeration of the entries. The entries are listed in the
     * order in which they appear in the zip file.
     *
     * <p>If you only need to iterate over the entries in a zip file, and don't
     * need random-access entry lookup by name, you should probably use {@link ZipInputStream}
     * instead, to avoid paying to construct the in-memory index.
     *
     * @throws IllegalStateException if this zip file has been closed.
     */
    public Enumeration<? extends TinkerZipEntry> entries() {
        checkNotClosed();
        final Iterator<TinkerZipEntry> iterator = entries.values().iterator();
        return new Enumeration<TinkerZipEntry>() {
            public boolean hasMoreElements() {
                checkNotClosed();
                return iterator.hasNext();
            }
            public TinkerZipEntry nextElement() {
                checkNotClosed();
                return iterator.next();
            }
        };
    }

    /**
     * Returns this file's comment, or null if it doesn't have one.
     * See {@link ZipOutputStream#setComment}.
     *
     * @throws IllegalStateException if this zip file has been closed.
     * @since 1.7
     */
    public String getComment() {
        checkNotClosed();
        return comment;
    }

    /**
     * Returns the zip entry with the given name, or null if there is no such entry.
     *
     * @throws IllegalStateException if this zip file has been closed.
     */
    public TinkerZipEntry getEntry(String entryName) {
        checkNotClosed();
        if (entryName == null) {
            throw new NullPointerException("entryName == null");
        }
        TinkerZipEntry ze = entries.get(entryName);
        if (ze == null) {
            ze = entries.get(entryName + "/");
        }
        return ze;
    }

    /**
     * Returns an input stream on the data of the specified {@code ZipEntry}.
     *
     * @param entry
     *            the ZipEntry.
     * @return an input stream of the data contained in the {@code ZipEntry}.
     * @throws IOException
     *             if an {@code IOException} occurs.
     * @throws IllegalStateException if this zip file has been closed.
     */
    public InputStream getInputStream(TinkerZipEntry entry) throws IOException {
        // Make sure this ZipEntry is in this Zip file.  We run it through the name lookup.
        entry = getEntry(entry.getName());
        if (entry == null) {
            return null;
        }
        // Create an InputStream at the right part of the file.
        RandomAccessFile localRaf = raf;
        synchronized (localRaf) {
            // We don't know the entry data's start position. All we have is the
            // position of the entry's local header.
            // http://www.pkware.com/documents/casestudies/APPNOTE.TXT
            RAFStream rafStream = new RAFStream(localRaf, entry.localHeaderRelOffset);
            DataInputStream is = new DataInputStream(rafStream);
            final int localMagic = Integer.reverseBytes(is.readInt());
            if (localMagic != LOCSIG) {
                throwZipException(filename, localRaf.length(), entry.getName(), entry.localHeaderRelOffset, "Local File Header", localMagic);
            }
            is.skipBytes(2);
            // At position 6 we find the General Purpose Bit Flag.
            int gpbf = Short.reverseBytes(is.readShort()) & 0xffff;
            if ((gpbf & TinkerZipFile.GPBF_UNSUPPORTED_MASK) != 0) {
                throw new ZipException("Invalid General Purpose Bit Flag: " + gpbf);
            }
            // Offset 26 has the file name length, and offset 28 has the extra field length.
            // These lengths can differ from the ones in the central header.
            is.skipBytes(18);
            int fileNameLength = Short.reverseBytes(is.readShort()) & 0xffff;
            int extraFieldLength = Short.reverseBytes(is.readShort()) & 0xffff;
            is.close();
            // Skip the variable-size file name and extra field data.
            rafStream.skip(fileNameLength + extraFieldLength);
            /*if (entry.compressionMethod == ZipEntry.STORED) {
                rafStream.endOffset = rafStream.offset + entry.size;
                return rafStream;
            } else {
                rafStream.endOffset = rafStream.offset + entry.compressedSize;
                int bufSize = Math.max(1024, (int) Math.min(entry.getSize(), 65535L));
                return new ZipInflaterInputStream(rafStream, new Inflater(true), bufSize, entry);
            }*/
            if (entry.compressionMethod == TinkerZipEntry.STORED) {
                rafStream.endOffset = rafStream.offset + entry.size;
            } else {
                rafStream.endOffset = rafStream.offset + entry.compressedSize;
            }
            return rafStream;
        }
    }

    /**
     * Gets the file name of this {@code ZipFile}.
     *
     * @return the file name of this {@code ZipFile}.
     */
    public String getName() {
        return filename;
    }

    /**
     * Returns the number of {@code ZipEntries} in this {@code ZipFile}.
     *
     * @return the number of entries in this file.
     * @throws IllegalStateException if this zip file has been closed.
     */
    public int size() {
        checkNotClosed();
        return entries.size();
    }

    /**
     * Find the central directory and read the contents.
     *
     * <p>The central directory can be followed by a variable-length comment
     * field, so we have to scan through it backwards.  The comment is at
     * most 64K, plus we have 18 bytes for the end-of-central-dir stuff
     * itself, plus apparently sometimes people throw random junk on the end
     * just for the fun of it.
     *
     * <p>This is all a little wobbly.  If the wrong value ends up in the EOCD
     * area, we're hosed. This appears to be the way that everybody handles
     * it though, so we're in good company if this fails.
     */
    private void readCentralDir() throws IOException {
        // Scan back, looking for the End Of Central Directory field. If the zip file doesn't
        // have an overall comment (unrelated to any per-entry comments), we'll hit the EOCD
        // on the first try.
        // No need to synchronize raf here -- we only do this when we first open the zip file.
        long scanOffset = raf.length() - ENDHDR;
        if (scanOffset < 0) {
            throw new ZipException("File too short to be a zip file: " + raf.length());
        }

        raf.seek(0);
        final int headerMagic = Integer.reverseBytes(raf.readInt());
        if (headerMagic != LOCSIG) {
            throw new ZipException("Not a zip archive");
        }

        long stopOffset = scanOffset - 65536;
        if (stopOffset < 0) {
            stopOffset = 0;
        }

        while (true) {
            raf.seek(scanOffset);
            if (Integer.reverseBytes(raf.readInt()) == ENDSIG) {
                break;
            }

            scanOffset--;
            if (scanOffset < stopOffset) {
                throw new ZipException("End Of Central Directory signature not found");
            }
        }

        // Read the End Of Central Directory. ENDHDR includes the signature bytes,
        // which we've already read.
        byte[] eocd = new byte[ENDHDR - 4];
        raf.readFully(eocd);

        // Pull out the information we need.
        BufferIterator it = HeapBufferIterator.iterator(eocd, 0, eocd.length, ByteOrder.LITTLE_ENDIAN);
        int diskNumber = it.readShort() & 0xffff;
        int diskWithCentralDir = it.readShort() & 0xffff;
        int numEntries = it.readShort() & 0xffff;
        int totalNumEntries = it.readShort() & 0xffff;
        it.skip(4); // Ignore centralDirSize.
        long centralDirOffset = ((long) it.readInt()) & 0xffffffffL;
        int commentLength = it.readShort() & 0xffff;

        if (numEntries != totalNumEntries || diskNumber != 0 || diskWithCentralDir != 0) {
            throw new ZipException("Spanned archives not supported");
        }

        if (commentLength > 0) {
            byte[] commentBytes = new byte[commentLength];
            raf.readFully(commentBytes);
            comment = new String(commentBytes, 0, commentBytes.length, StandardCharsets.UTF_8);
        }

        // Seek to the first CDE and read all entries.
        // We have to do this now (from the constructor) rather than lazily because the
        // public API doesn't allow us to throw IOException except from the constructor
        // or from getInputStream.
        RAFStream rafStream = new RAFStream(raf, centralDirOffset);
        BufferedInputStream bufferedStream = new BufferedInputStream(rafStream, 4096);
        byte[] hdrBuf = new byte[CENHDR]; // Reuse the same buffer for each entry.
        for (int i = 0; i < numEntries; ++i) {
            TinkerZipEntry newEntry = new TinkerZipEntry(hdrBuf, bufferedStream, StandardCharsets.UTF_8,
                (false) /* isZip64 */);
            if (newEntry.localHeaderRelOffset >= centralDirOffset) {
                throw new ZipException("Local file header offset is after central directory");
            }
            String entryName = newEntry.getName();
            if (entries.put(entryName, newEntry) != null) {
                throw new ZipException("Duplicate entry name: " + entryName);
            }
        }

    }

    // private final CloseGuard guard = CloseGuard.get();
    static class EocdRecord {
        final long numEntries;
        final long centralDirOffset;
        final int commentLength;
        EocdRecord(long numEntries, long centralDirOffset, int commentLength) {
            this.numEntries = numEntries;
            this.centralDirOffset = centralDirOffset;
            this.commentLength = commentLength;
        }
    }

    /**
     * Wrap a stream around a RandomAccessFile.  The RandomAccessFile is shared
     * among all streams returned by getInputStream(), so we have to synchronize
     * access to it.  (We can optimize this by adding buffering here to reduce
     * collisions.)
     *
     * <p>We could support mark/reset, but we don't currently need them.
     *
     * @hide
     */
    public static class RAFStream extends InputStream {
        private final RandomAccessFile sharedRaf;
        private long endOffset;
        private long offset;
        public RAFStream(RandomAccessFile raf, long initialOffset, long endOffset) {
            sharedRaf = raf;
            offset = initialOffset;
            this.endOffset = endOffset;
        }
        public RAFStream(RandomAccessFile raf, long initialOffset) throws IOException {
            this(raf, initialOffset, raf.length());
        }
        @Override public int available() throws IOException {
            return (offset < endOffset ? 1 : 0);
        }
        @Override public int read() throws IOException {
            return Streams.readSingleByte(this);
        }
        @Override public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            synchronized (sharedRaf) {
                final long length = endOffset - offset;
                if (byteCount > length) {
                    byteCount = (int) length;
                }
                sharedRaf.seek(offset);
                int count = sharedRaf.read(buffer, byteOffset, byteCount);
                if (count > 0) {
                    offset += count;
                    return count;
                } else {
                    return -1;
                }
            }
        }
        @Override public long skip(long byteCount) throws IOException {
            if (byteCount > endOffset - offset) {
                byteCount = endOffset - offset;
            }
            offset += byteCount;
            return byteCount;
        }
        /*public int fill(Inflater inflater, int nativeEndBufSize) throws IOException {
            synchronized (sharedRaf) {
                int len = Math.min((int) (endOffset - offset), nativeEndBufSize);
                int cnt = inflater.setFileInput(sharedRaf.getFD(), offset, nativeEndBufSize);
                // setFileInput read from the file, so we need to get the OS and RAFStream back
                // in sync...
                skip(cnt);
                return len;
            }
        }*/
    }
    /** @hide */
    /*public static class ZipInflaterInputStream extends InflaterInputStream {
        private final ZipEntry entry;
        private long bytesRead = 0;
        public ZipInflaterInputStream(InputStream is, Inflater inf, int bsize, ZipEntry entry) {
            super(is, inf, bsize);
            this.entry = entry;
        }
        @Override public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            final int i;
            try {
                i = super.read(buffer, byteOffset, byteCount);
            } catch (IOException e) {
                throw new IOException("Error reading data for " + entry.getName() + " near offset "
                        + bytesRead, e);
            }
            if (i == -1) {
                if (entry.size != bytesRead) {
                    throw new IOException("Size mismatch on inflated file: " + bytesRead + " vs "
                            + entry.size);
                }
            } else {
                bytesRead += i;
            }
            return i;
        }
        @Override public int available() throws IOException {
            if (closed) {
                // Our superclass will throw an exception, but there's a jtreg test that
                // explicitly checks that the InputStream returned from ZipFile.getInputStream
                // returns 0 even when closed.
                return 0;
            }
            return super.available() == 0 ? 0 : (int) (entry.getSize() - bytesRead);
        }
    }*/
}
