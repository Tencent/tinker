package com.tencent.tinker.internal.util

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

private const val TAG = "Tinker.Util.Concurrent"

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

internal abstract class AsyncScope<T> {
    abstract fun launch(action: () -> T)
}

private class AsyncScopeImpl<T>(
    private val executor: ExecutorService,
    private val futures: MutableList<Future<T>>
): AsyncScope<T>() {

    private class Task<T>(private val original: () -> T) : Callable<T> {
        override fun call(): T {
            debugLog(TAG) {
                "Start asynchronized task ${hashCode().toString(16)}."
            }
            try {
                return original()
            } finally {
                debugLog(TAG) {
                    "Complete asynchronized task ${hashCode().toString(16)}."
                }
            }
        }
    }

    override fun launch(action: () -> T) {
        action.let(::Task)
            .let(executor::submit)
            .also(futures::add)
    }
}

internal fun <T> async(
    name: String? = null,
    scope: AsyncScope<T>.() -> Unit,
): List<T> {
    val executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    ) {
        Thread(it, name ?: "tinker-async")
    }
    val futures = mutableListOf<Future<T>>()
    try {
        AsyncScopeImpl(executor, futures).scope()
        return futures.map { it.get() }
    } finally {
        executor.shutdown()
    }
}