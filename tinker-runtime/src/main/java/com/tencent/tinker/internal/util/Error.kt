package com.tencent.tinker.internal.util

import com.tencent.tinker.internal.TinkerError
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.enums.enumEntries

internal val TinkerError.Type.errorCode: Int
    get() = (group.ordinal shl 8) or typeCode

/**
 * Runs code in `scope`. If any throwable is thrown, the function rethrows it as original if it is a
 * `TinkerError`, or wraps it as a `TinkerError` with `unexpected` error type, and `cleaner` will be
 * called.
 */
@OptIn(ExperimentalStdlibApi::class, ExperimentalContracts::class)
internal inline fun <T> expected(
    action: String,
    unexpected: TinkerError.Type,
    noinline cleaner: (() -> Unit)? = null,
    scope: () -> T,
): T {
    contract {
        callsInPlace(scope, InvocationKind.EXACTLY_ONCE)
    }
    try {
        return scope()
    } catch (throwable: Throwable) {
        cleaner?.invoke()
        if (throwable is TinkerError) {
            throw throwable
        }
        throw TinkerError(
            unexpected,
            "Cannot $action because of unexpected error.",
            throwable
        )
    }
}

/**
 * Runs code in `scope`. If any throwable is thrown, the function rethrows it as original if it is a
 * `TinkerError`, or wraps it as a `TinkerError` with first entry of `T` as error type, and
 * `cleaner` will be called.
 */
@OptIn(ExperimentalStdlibApi::class, ExperimentalContracts::class)
internal inline fun <reified T> expected(
    action: String,
    noinline cleaner: (() -> Unit)? = null,
    scope: () -> Unit,
) where T : Enum<T>, T : TinkerError.Type {
    contract {
        callsInPlace(scope, InvocationKind.EXACTLY_ONCE)
    }
    return expected(action, enumEntries<T>()[0], cleaner, scope)
}

/**
 * Tries each strategy and returns the first successful result. If all strategies fail, the function throws the first
 * error.
 */
internal fun <T> tryEach(
    strategies: Iterable<() -> T>,
): T {
    val throwables =
        strategies.mapNotNull { strategy ->
            try {
                return strategy() ?: return@mapNotNull null
            } catch (throwable: Throwable) {
                return@mapNotNull throwable
            }
        }
    throw throwables.first()
}