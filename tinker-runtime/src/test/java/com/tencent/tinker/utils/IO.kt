package com.tencent.tinker.utils

import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class IOTest {

    /**
     * Tests if the file can be deleted while its exclusive guarded content is held.
     */
    @Test
    fun deleteFileWhileEscapedGuardedContentIsHeld() {
        val file = Files.createTempFile("test-", ".txt").toFile()
        val guardedContent = file.escapedGuardedContentExclusive("Hello world!".toByteArray())
        file.delete()
        assertFalse(file.exists())
        guardedContent.close()
    }
}