package com.tencent.tinker.internal.util

import java.util.zip.ZipEntry

/**
 * Returns a new [ZipEntry] with the same name as this entry.
 */
internal val ZipEntry.forked: ZipEntry
    get() = ZipEntry(name).apply {
        method = this@forked.method
        size = this@forked.size
        crc = this@forked.crc
    }

/**
 * Returns a new [ZipEntry] with the same name as this entry, but with the stored method.
 */
internal val ZipEntry.forkedStored: ZipEntry
    get() = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = this@forkedStored.size
        crc = this@forkedStored.crc
    }