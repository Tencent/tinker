package com.tencent.tinker.internal.util

internal class SynchronizedCache<T> {

    private var content = null as T?

    fun getOrPut(builder: () -> T): T {
        content?.let { return it }
        synchronized(this) {
            content?.let { return it }
            return builder().also { content = it }
        }
    }
}