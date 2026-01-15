package com.tencent.tinker.internal.util

import com.tencent.tinker.Tinker
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.enums.enumEntries

/**
 * Runs code in [scope]. If any throwable is thrown, the function rethrows it as original if it is a [Tinker.Error], or
 * wraps it as a [Tinker.Error] with first entry of [T] as error type, and [cleaner] will be called.
 */
@OptIn(ExperimentalStdlibApi::class, ExperimentalContracts::class)
internal inline fun <reified T, R> expected(
    action: String,
    noinline cleaner: (() -> Unit)? = null,
    scope: () -> R,
): R where T : Enum<T>, T : Tinker.Error.Type {
    contract {
        callsInPlace(scope, InvocationKind.EXACTLY_ONCE)
    }
    try {
        return scope()
    } catch (throwable: Throwable) {
        cleaner?.invoke()
        if (throwable is Tinker.Error) {
            throw throwable
        }
        throw Tinker.Error(
            enumEntries<T>()[0],
            "Cannot $action because of unexpected error.",
            throwable
        )
    }
}

/**
 * Runs code in [scope]. If any throwable is thrown, the function rethrows it as original if it is a [Tinker.Error], or
 * wraps it as a [Tinker.Error] with first entry of [T] as error type, and [cleaner] will be called.
 */
@OptIn(ExperimentalStdlibApi::class, ExperimentalContracts::class)
internal inline fun <reified T> expected(
    action: String,
    noinline cleaner: (() -> Unit)? = null,
    scope: () -> Unit,
) where T : Enum<T>, T : Tinker.Error.Type {
    contract {
        callsInPlace(scope, InvocationKind.EXACTLY_ONCE)
    }
    expected<T, Unit>(action, cleaner, scope)
}

/**
 * Tries each strategy and returns the first successful result. If all strategies fail, the function throws the first
 * error.
 */
internal fun <T> tryEach(
    strategies: Iterable<() -> T>,
): T {
    val throwableList =
        strategies.mapNotNull { strategy ->
            try {
                return strategy() ?: return@mapNotNull null
            } catch (throwable: Throwable) {
                return@mapNotNull throwable
            }
        }
    throw throwableList.first()
}