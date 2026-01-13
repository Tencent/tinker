package com.tencent.tinker

import java.io.InputStream
import java.io.OutputStream

class Tinker {

    /**
     * Logger used to print log messages.
     */
    abstract class Logger {

        /**
         * Log a [message] with [tag] and [priority].
         */
        abstract fun log(
            priority: Int,
            tag: String,
            message: String,
        )
    }

    /**
     * Merger used to generate patched data from base data and diff data.
     */
    abstract class LegacyMerger {

        /**
         * Merge base data from [baseInput] and diff data from [diffInput] to patched data, and write to
         * [patchedOutput].
         */
        abstract fun merge(
            baseInput: InputStream,
            diffInput: InputStream,
            patchedOutput: OutputStream,
        )
    }
}