package com.tencent.tinker.test.internal.util

import com.tencent.tinker.internal.util.HashOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.outputStream

class HashTest {

    /**
     * Tests calculating the hash from an output stream.
     */
    @Test
    fun calculateHashFromOutputStream() {
        val data = "Hello world!".toByteArray()
        val file = Files.createTempFile("tinker-test-", ".txt")
        val actualHash = file.outputStream().use { stream ->
            HashOutputStream(stream)
                .apply {
                    write(data)
                }
                .digest
        }
        val expectedCalculator = MessageDigest.getInstance("MD5")
        expectedCalculator.update(data)
        assertArrayEquals(expectedCalculator.digest(), actualHash)
    }

    /**
     * Tests calculating the hash with a huge size of data.
     */
    @Test
    fun calculateWithHugeData() {
        val data = ByteArray(DEFAULT_BUFFER_SIZE * 2) {
            (it % 256).toByte()
        }
        val file = Files.createTempFile("tinker-test-", ".txt")
        val actualHash = file.outputStream().use { stream ->
            HashOutputStream(stream)
                .apply {
                    write(data)
                }
                .digest
        }
        val expectedCalculator = MessageDigest.getInstance("MD5")
        expectedCalculator.update(data)
        assertArrayEquals(expectedCalculator.digest(), actualHash)
    }
}