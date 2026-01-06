package com.tencent.tinker.internal.util

import com.tencent.tinker.internal.TEST_DEX_FILE_NAME
import java.io.File

/**
 * Pattern used to match dex files with name "classesN.dex", where "N" is a number or empty.
 */
internal val classesDexWithIndexPattern = "classes(\\d*)\\.dex".toRegex()

/**
 * Searches and sorts dex files in the given directory. Returns sorted dex files, or null if given directory is not an
 * existing directory or no dex files found.
 */
internal fun File.searchAndSortDexFiles(): List<File>? =
    takeIf { it.isDirectory }
        ?.listFiles()
        ?.filter {
            it.isFile && it.extension == "dex"
        }
        ?.takeIf { it.isNotEmpty() }
        ?.map { dex ->
            if (dex.name == TEST_DEX_FILE_NAME) {
                return@map dex to (2 to 0)
            }
            val match = classesDexWithIndexPattern
                .matchEntire(dex.name)
                ?: return@map dex to (1 to dex.name)
            val index = match.groupValues[1]
                .takeIf { it.isNotEmpty() }
                ?.toInt()
                ?: 0
            return@map dex to (0 to index)
        }
        ?.sortedWith(
            compareBy(
                { it.second.first },
                { it.second.second }
            )
        )
        ?.map { it.first }
