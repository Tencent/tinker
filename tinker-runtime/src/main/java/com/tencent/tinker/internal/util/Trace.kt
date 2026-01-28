package com.tencent.tinker.internal.util

import android.os.Process
import android.os.SystemClock
import android.os.Trace
import com.tencent.tinker.Tinker
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class TraceContext {
    private val container = ConcurrentLinkedQueue<Tinker.TraceEvent>()

    fun collect(event: Tinker.TraceEvent) {
        container.add(event)
    }

    val events: List<Tinker.TraceEvent>
        get() = container.toList()
}

private val currentTraceContext = ThreadLocal<TraceContext>()

internal val currentTraceContextForInherit: TraceContext?
    get() = currentTraceContext.get()

@OptIn(ExperimentalContracts::class)
internal inline fun inheritTraceContext(
    traceContext: TraceContext?,
    action: () -> Unit,
) {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    currentTraceContext.set(traceContext)
    try {
        action()
    } finally {
        currentTraceContext.set(null)
    }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> traceTask(name: String, action: () -> T): Pair<T, List<Tinker.TraceEvent>> {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    val collector = TraceContext()
    currentTraceContext
        .also {
            it.get()?.let {
                throw Tinker.Error(
                    Tinker.Error.Unexpected.Trace.TRACE_TASK_INSIDE_A_TASK,
                    "Function traceTask() cannot be called inside another traceTask()."
                )
            }
        }
        .set(collector)
    try {
        return traceE(name, action) to collector.events
    } finally {
        currentTraceContext.set(null)
    }
}

/**
 * Trace task procedure as expression.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun <T> traceE(event: String, action: () -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    val timestamp = SystemClock.elapsedRealtimeNanos() / 1000
    val name = "Tinker $event"
    Trace.beginSection(name)
    try {
        return action()
    } finally {
        Trace.endSection()
        val duration = (SystemClock.elapsedRealtimeNanos() / 1000) - timestamp
        currentTraceContext.get()
            ?.collect(
                Tinker.TraceEvent(
                    name,
                    Process.myPid(),
                    Process.myTid(),
                    timestamp,
                    duration,
                )
            )
    }
}

/**
 * Trace task procedure as statement.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun traceS(event: String, action: () -> Unit) {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    traceE(event, action)
}

internal fun Iterable<Tinker.TraceEvent>.dumpToFile(file: File) {
    val events = toList()
    file.bufferedWriter().use { writer ->
        writer.write("{")
        writer.write("\"traceEvents\":[")
        events.forEachIndexed { index, event ->
            if (index == 0) {
                writer.write("{")
            } else {
                writer.write(",{")
            }
            writer.write("\"ph\":\"X\",")
            writer.write("\"name\":\"${event.name}\",")
            writer.write("\"pid\":${event.pid},")
            writer.write("\"tid\":${event.tid},")
            writer.write("\"ts\":${event.timestamp},")
            writer.write("\"dur\":${event.duration}")
            writer.write("}")
        }
        writer.write("]")
        writer.write("}")
    }
}