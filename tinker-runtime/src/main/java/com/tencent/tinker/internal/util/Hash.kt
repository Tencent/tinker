package com.tencent.tinker.internal.util

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Converts a MD5 hash string to byte array format.
 */
internal val String.asMd5Hash: ByteArray
    get() {
        require(length == 32) {
            "Length of string should be 32"
        }
        return chunked(2)
            .map { it.toUByte(16).toByte() }
            .toByteArray()
    }

/**
 * Converts a MD5 hash byte array to string format.
 */
@OptIn(ExperimentalStdlibApi::class)
internal val ByteArray.asMd5String: String
    get() = toHexString()

/**
 * Converts a MD5 hash string to ByteArray format.
 */
internal val String.asMd5HashNullable: ByteArray?
    get() {
        if (this == "0") {
            return null
        }
        return asMd5Hash
    }


/**
 * Gets the CRC32 checksum of this byte array.
 */
internal val ByteArray.crc32: Long
    get() = CRC32()
        .apply {
            update(this@crc32)
        }
        .value

/**
 * Gets the CRC32 checksum of this file.
 */
internal val File.crc32: Long
    get() {
        val calculator = CRC32()
        inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) {
                    break
                }
                calculator.update(buffer, 0, read)
            }
        }
        return calculator.value
    }

/**
 * Gets the MD5 hash of this entry in the given zip file.
 */
internal fun ZipFile.hashOf(entry: ZipEntry): ByteArray =
    getInputStream(entry).use { stream ->
        val calculator = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytes = stream.read(buffer)
        while (bytes >= 0) {
            calculator.update(buffer, 0, bytes)
            bytes = stream.read(buffer)
        }
        return@use calculator.digest()
    }

/**
 * Like [copyTo], but also generates the MD5 hash of the content.
 */
internal fun InputStream.copyAndGenerateHash(out: OutputStream): ByteArray {
    val calculator = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        calculator.update(buffer, 0, bytes)
        bytes = read(buffer)
    }
    return calculator.digest()
}

/**
 * An [OutputStream] wrapper to calculate the CRC32 checksum of written bytes.
 */
internal class Crc32OutputStream(
    private val output: OutputStream,
) : OutputStream() {

    private val calculator = CRC32()

    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    private var cursor = 0

    override fun write(byte: Int) {
        output.write(byte)
        if (cursor >= buffer.size) {
            calculator.update(buffer)
            cursor = 0
        }
        buffer[cursor] = byte.toByte()
        cursor++
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        output.write(bytes, offset, length)
        if (cursor > 0) {
            calculator.update(buffer, 0, cursor)
            cursor = 0
        }
        calculator.update(bytes, offset, length)
    }

    val checksum: Long
        get() {
            if (cursor > 0) {
                calculator.update(buffer, 0, cursor)
                cursor = 0
            }
            return calculator.value
        }
}

/**
 * An [OutputStream] wrapper to calculate the MD5 hash of written bytes.
 */
internal class HashOutputStream(
    private val output: OutputStream,
) : OutputStream() {

    private val calculator = MessageDigest.getInstance("MD5")

    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    private var cursor = 0

    override fun write(byte: Int) {
        output.write(byte)
        if (cursor >= buffer.size) {
            calculator.update(buffer)
            cursor = 0
        }
        buffer[cursor] = byte.toByte()
        cursor++
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        output.write(bytes, offset, length)
        if (cursor > 0) {
            calculator.update(buffer, 0, cursor)
            cursor = 0
        }
        calculator.update(bytes, offset, length)
    }

    val digest: ByteArray
        get() {
            if (cursor > 0) {
                calculator.update(buffer, 0, cursor)
                cursor = 0
            }
            return calculator.digest()
        }
}