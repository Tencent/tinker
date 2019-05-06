/*
 * Copyright (C) 2016 THL A29 Limited, a Tencent company.
 * Copyright (c) 2005, Joe Desbonnet, (jdesbonnet@gmail.com)
 * Copyright 2003-2005 Colin Percival
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted providing that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.tencent.tinker.bsdiff;


import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Java Binary Diff utility. Based on bsdiff (v4.2) by Colin Percival (see http://www.daemonology.net/bsdiff/ ) and distributed under BSD license.
 * Running this on large files will probably require an increase of the default maximum heap size (use java -Xmx300m)
 */
public class BSDiff {

    //private static final String VERSION = "jbdiff-0.1.0.1";

    // This is
    private static final byte[] MAGIC_BYTES = new byte[]{0x4D, 0x69, 0x63,
        0x72, 0x6F, 0x4D, 0x73, 0x67};

    private static void split(int[] arrayI, int[] arrayV, int start, int len, int h) {

        int i, j, k, x, tmp, jj, kk;

        if (len < 16) {
            for (k = start; k < start + len; k += j) {
                j = 1;
                x = arrayV[arrayI[k] + h];
                for (i = 1; k + i < start + len; i++) {
                    if (arrayV[arrayI[k + i] + h] < x) {
                        x = arrayV[arrayI[k + i] + h];
                        j = 0;
                    }

                    if (arrayV[arrayI[k + i] + h] == x) {
                        tmp = arrayI[k + j];
                        arrayI[k + j] = arrayI[k + i];
                        arrayI[k + i] = tmp;
                        j++;
                    }

                }

                for (i = 0; i < j; i++) {
                    arrayV[arrayI[k + i]] = k + j - 1;
                }
                if (j == 1) {
                    arrayI[k] = -1;
                }
            }

            return;
        }

        x = arrayV[arrayI[start + len / 2] + h];
        jj = 0;
        kk = 0;
        for (i = start; i < start + len; i++) {
            if (arrayV[arrayI[i] + h] < x) {
                jj++;
            }
            if (arrayV[arrayI[i] + h] == x) {
                kk++;
            }
        }

        jj += start;
        kk += jj;

        i = start;
        j = 0;
        k = 0;
        while (i < jj) {
            if (arrayV[arrayI[i] + h] < x) {
                i++;
            } else if (arrayV[arrayI[i] + h] == x) {
                tmp = arrayI[i];
                arrayI[i] = arrayI[jj + j];
                arrayI[jj + j] = tmp;
                j++;
            } else {
                tmp = arrayI[i];
                arrayI[i] = arrayI[kk + k];
                arrayI[kk + k] = tmp;
                k++;
            }

        }

        while (jj + j < kk) {
            if (arrayV[arrayI[jj + j] + h] == x) {
                j++;
            } else {
                tmp = arrayI[jj + j];
                arrayI[jj + j] = arrayI[kk + k];
                arrayI[kk + k] = tmp;
                k++;
            }

        }

        if (jj > start) {
            split(arrayI, arrayV, start, jj - start, h);
        }

        for (i = 0; i < kk - jj; i++) {
            arrayV[arrayI[jj + i]] = kk - 1;
        }

        if (jj == kk - 1) {
            arrayI[jj] = -1;
        }

        if (start + len > kk) {
            split(arrayI, arrayV, kk, start + len - kk, h);
        }

    }

    /**
     * Fast suffix sporting. Larsson and Sadakane's qsufsort algorithm. See
     * http://www.cs.lth.se/Research/Algorithms/Papers/jesper5.ps
     */
    private static void qsufsort(int[] arrayI, int[] arrayV, byte[] oldBuf, int oldsize) {

        // int oldsize = oldBuf.length;
        int[] buckets = new int[256];

        // No need to do that in Java.
        // for ( int i = 0; i < 256; i++ ) {
        // buckets[i] = 0;
        // }

        for (int i = 0; i < oldsize; i++) {
            buckets[oldBuf[i] & 0xff]++;
        }

        for (int i = 1; i < 256; i++) {
            buckets[i] += buckets[i - 1];
        }

        for (int i = 255; i > 0; i--) {
            buckets[i] = buckets[i - 1];
        }

        buckets[0] = 0;

        for (int i = 0; i < oldsize; i++) {
            arrayI[++buckets[oldBuf[i] & 0xff]] = i;
        }

        arrayI[0] = oldsize;
        for (int i = 0; i < oldsize; i++) {
            arrayV[i] = buckets[oldBuf[i] & 0xff];
        }
        arrayV[oldsize] = 0;

        for (int i = 1; i < 256; i++) {
            if (buckets[i] == buckets[i - 1] + 1) {
                arrayI[buckets[i]] = -1;
            }
        }

        arrayI[0] = -1;

        for (int h = 1; arrayI[0] != -(oldsize + 1); h += h) {
            int len = 0;
            int i;
            for (i = 0; i < oldsize + 1;) {
                if (arrayI[i] < 0) {
                    len -= arrayI[i];
                    i -= arrayI[i];
                } else {
                    // if(len) I[i-len]=-len;
                    if (len != 0) {
                        arrayI[i - len] = -len;
                    }
                    len = arrayV[arrayI[i]] + 1 - i;
                    split(arrayI, arrayV, i, len, h);
                    i += len;
                    len = 0;
                }

            }

            if (len != 0) {
                arrayI[i - len] = -len;
            }
        }

        for (int i = 0; i < oldsize + 1; i++) {
            arrayI[arrayV[i]] = i;
        }
    }


    /**
     * 分别将 oldBufd[start..oldSize] 和 oldBufd[end..oldSize] 与  newBuf[newBufOffset...newSize] 进行匹配，
     * 返回他们中的最长匹配长度，并且将最长匹配的开始位置记录到pos.value中。
     */
    private static int search(int[] arrayI, byte[] oldBuf, int oldSize, byte[] newBuf, int newSize, int newBufOffset, int start, int end, IntByRef pos) {

        if (end - start < 2) {
            int x = matchlen(oldBuf, oldSize, arrayI[start], newBuf, newSize, newBufOffset);
            int y = matchlen(oldBuf, oldSize, arrayI[end], newBuf, newSize, newBufOffset);

            if (x > y) {
                pos.value = arrayI[start];
                return x;
            } else {
                pos.value = arrayI[end];
                return y;
            }
        }

        // binary search
        int x = start + (end - start) / 2;
        if (memcmp(oldBuf, oldSize, arrayI[x], newBuf, newSize, newBufOffset) < 0) {
            return search(arrayI, oldBuf, oldSize, newBuf, newSize, newBufOffset, x, end, pos);  // Calls itself recursively
        } else {
            return search(arrayI, oldBuf, oldSize, newBuf, newSize, newBufOffset, start, x, pos);
        }
    }


    /**
     * Count the number of bytes that match in oldBuf[oldOffset...oldSize] and newBuf[newOffset...newSize]
     */
    private static int matchlen(byte[] oldBuf, int oldSize, int oldOffset, byte[] newBuf, int newSize, int newOffset) {

        int end = Math.min(oldSize - oldOffset, newSize - newOffset);
        for (int i = 0; i < end; i++) {
            if (oldBuf[oldOffset + i] != newBuf[newOffset + i]) {
                return i;
            }
        }
        return end;
    }

    /**
     * Compare two byte array segments to see if they are equal
     *
     * return 1 if s1[s1offset...s1Size] is bigger than s2[s2offset...s2Size] otherwise return -1
     */
    private static int memcmp(byte[] s1, int s1Size, int s1offset, byte[] s2, int s2Size, int s2offset) {

        int n = s1Size - s1offset;

        if (n > (s2Size - s2offset)) {
            n = s2Size - s2offset;
        }

        for (int i = 0; i < n; i++) {

            if (s1[i + s1offset] != s2[i + s2offset]) {
                return s1[i + s1offset] < s2[i + s2offset] ? -1 : 1;
            }
        }
        return 0;
    }


    public static void bsdiff(File oldFile, File newFile, File diffFile) throws IOException {
        InputStream oldInputStream = new BufferedInputStream(new FileInputStream(oldFile));
        InputStream newInputStream = new BufferedInputStream(new FileInputStream(newFile));
        OutputStream diffOutputStream = new FileOutputStream(diffFile);
        try {
            byte[] diffBytes = bsdiff(oldInputStream, (int) oldFile.length(), newInputStream, (int) newFile.length());
            diffOutputStream.write(diffBytes);
        } finally {
            diffOutputStream.close();
        }
    }


    public static byte[] bsdiff(InputStream oldInputStream, int oldsize, InputStream newInputStream, int newsize) throws IOException {

        byte[] oldBuf = new byte[oldsize];

        BSUtil.readFromStream(oldInputStream, oldBuf, 0, oldsize);
        oldInputStream.close();

        byte[] newBuf = new byte[newsize];
        BSUtil.readFromStream(newInputStream, newBuf, 0, newsize);
        newInputStream.close();

        return bsdiff(oldBuf, oldsize, newBuf, newsize);
    }


    public static byte[] bsdiff(byte[] oldBuf, int oldsize, byte[] newBuf, int newsize) throws IOException {

        int[] arrayI = new int[oldsize + 1];
        qsufsort(arrayI, new int[oldsize + 1], oldBuf, oldsize);

        // diff block
        int diffBLockLen = 0;
        byte[] diffBlock = new byte[newsize];

        // extra block
        int extraBlockLen = 0;
        byte[] extraBlock = new byte[newsize];

        /*
         * Diff file is composed as follows:
         *
         * Header (32 bytes) Data (from offset 32 to end of file)
         *
         * Header:
         * Offset 0, length 8 bytes: file magic "MicroMsg"
         * Offset 8, length 8 bytes: length of compressed ctrl block
         * Offset 16, length 8 bytes: length of compressed diff block
         * Offset 24, length 8 bytes: length of new file
         *
         * Data:
         * 32 (length ctrlBlockLen): ctrlBlock (bzip2)
         * 32 + ctrlBlockLen (length diffBlockLen): diffBlock (bzip2)
         * 32 + ctrlBlockLen + diffBlockLen (to end of file): extraBlock (bzip2)
         *
         * ctrlBlock comprises a set of records, each record 12 bytes.
         * A record comprises 3 x 32 bit integers. The ctrlBlock is not compressed.
         */

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream diffOut = new DataOutputStream(byteOut);

        // Write as much of header as we have now. Size of ctrlBlock and diffBlock must be filled in later.
        diffOut.write(MAGIC_BYTES);
        diffOut.writeLong(-1); // place holder for ctrlBlockLen
        diffOut.writeLong(-1); // place holder for diffBlockLen
        diffOut.writeLong(newsize);
        diffOut.flush();

        GZIPOutputStream bzip2Out = new GZIPOutputStream(diffOut);
        DataOutputStream dataOut = new DataOutputStream(bzip2Out);

        int oldscore, scsc;

        int overlap, ss, lens;
        int i;
        int scan = 0;
        int matchLen = 0;
        int lastscan = 0;
        int lastpos = 0;
        int lastoffset = 0;

        IntByRef pos = new IntByRef();
        // int ctrlBlockLen = 0;

        while (scan < newsize) {
            oldscore = 0;

            for (scsc = scan += matchLen; scan < newsize; scan++) {
                //  oldBuf[0...oldsize] newBuf[scan...newSize]. pos.value，scan
                matchLen = search(arrayI, oldBuf, oldsize, newBuf, newsize, scan, 0, oldsize, pos);

                for (; scsc < scan + matchLen; scsc++) {
                    if ((scsc + lastoffset < oldsize) && (oldBuf[scsc + lastoffset] == newBuf[scsc])) {
                        oldscore++;
                    }
                }

                if (((matchLen == oldscore) && (matchLen != 0)) || (matchLen > oldscore + 8)) {
                    break;
                }

                if ((scan + lastoffset < oldsize) && (oldBuf[scan + lastoffset] == newBuf[scan])) {
                    oldscore--;
                }
            }

            if ((matchLen != oldscore) || (scan == newsize)) {

                int equalNum = 0;
                int sf = 0;
                int lenFromOld = 0;
                for (i = 0; (lastscan + i < scan) && (lastpos + i < oldsize);) {
                    if (oldBuf[lastpos + i] == newBuf[lastscan + i]) {
                        equalNum++;
                    }
                    i++;
                    if (equalNum * 2 - i > sf * 2 - lenFromOld) {
                        sf = equalNum;
                        lenFromOld = i;
                    }
                }

                int lenb = 0;
                if (scan < newsize) {
                    equalNum = 0;
                    int sb = 0;
                    for (i = 1; (scan >= lastscan + i) && (pos.value >= i); i++) {
                        if (oldBuf[pos.value - i] == newBuf[scan - i]) {
                            equalNum++;
                        }
                        if (equalNum * 2 - i > sb * 2 - lenb) {
                            sb = equalNum;
                            lenb = i;
                        }
                    }
                }

                if (lastscan + lenFromOld > scan - lenb) {
                    overlap = (lastscan + lenFromOld) - (scan - lenb);
                    equalNum = 0;
                    ss = 0;
                    lens = 0;
                    for (i = 0; i < overlap; i++) {
                        if (newBuf[lastscan + lenFromOld - overlap + i] == oldBuf[lastpos + lenFromOld - overlap + i]) {
                            equalNum++;
                        }
                        if (newBuf[scan - lenb + i] == oldBuf[pos.value - lenb + i]) {
                            equalNum--;
                        }
                        if (equalNum > ss) {
                            ss = equalNum;
                            lens = i + 1;
                        }
                    }

                    lenFromOld += lens - overlap;
                    lenb -= lens;
                }

                // ? byte casting introduced here -- might affect things
                for (i = 0; i < lenFromOld; i++) {
                    diffBlock[diffBLockLen + i] = (byte) (newBuf[lastscan + i] - oldBuf[lastpos + i]);
                }

                for (i = 0; i < (scan - lenb) - (lastscan + lenFromOld); i++) {
                    extraBlock[extraBlockLen + i] = newBuf[lastscan + lenFromOld + i];
                }

                diffBLockLen += lenFromOld;
                extraBlockLen += (scan - lenb) - (lastscan + lenFromOld);

                // Write control block entry (3 x int)
                dataOut.writeInt(lenFromOld);  // oldBuf
                dataOut.writeInt((scan - lenb) - (lastscan + lenFromOld));  // diffBufextraBlock
                dataOut.writeInt((pos.value - lenb) - (lastpos + lenFromOld));  // oldBuf

                lastscan = scan - lenb;
                lastpos = pos.value - lenb;
                lastoffset = pos.value - scan;
            } // end if
        } // end while loop

        dataOut.flush();
        bzip2Out.finish();

        // now compressed ctrlBlockLen
        int ctrlBlockLen = diffOut.size() - BSUtil.HEADER_SIZE;

        // GZIPOutputStream gzOut;

        /*
         * Write diff block
         */
        bzip2Out = new GZIPOutputStream(diffOut);
        bzip2Out.write(diffBlock, 0, diffBLockLen);
        bzip2Out.finish();
        bzip2Out.flush();
        int diffBlockLen = diffOut.size() - ctrlBlockLen - BSUtil.HEADER_SIZE;
        // System.err.println( "Diff: diffBlockLen=" + diffBlockLen );

        /*
         * Write extra block
         */
        bzip2Out = new GZIPOutputStream(diffOut);
        bzip2Out.write(extraBlock, 0, extraBlockLen);
        bzip2Out.finish();
        bzip2Out.flush();

        diffOut.close();

        /*
         * Write missing header info.
         */
        ByteArrayOutputStream byteHeaderOut = new ByteArrayOutputStream(BSUtil.HEADER_SIZE);
        DataOutputStream headerOut = new DataOutputStream(byteHeaderOut);
        headerOut.write(MAGIC_BYTES);
        headerOut.writeLong(ctrlBlockLen); // place holder for ctrlBlockLen
        headerOut.writeLong(diffBlockLen); // place holder for diffBlockLen
        headerOut.writeLong(newsize);
        headerOut.close();

        // Copy header information into the diff
        byte[] diffBytes = byteOut.toByteArray();
        byte[] headerBytes = byteHeaderOut.toByteArray();

        System.arraycopy(headerBytes, 0, diffBytes, 0, headerBytes.length);

        return diffBytes;
    }

    //    /**
    //     * Run JBDiff from the command line. Params: oldfile newfile difffile. diff
    //     * file will be created.
    //     */
    //    public static void main(String[] arg) throws IOException {
    //
    //        if (arg.length != 3) {
    //            System.err.println("usage example: java -Xmx250m JBDiff oldfile newfile patchfile\n");
    //            return;
    //        }
    //        File oldFile = new File(arg[0]);
    //        File newFile = new File(arg[1]);
    //        File diffFile = new File(arg[2]);
    //
    //        bsdiff(oldFile, newFile, diffFile);
    //
    //    }

    private static class IntByRef {
        private int value;
    }
}
