package com.tencent.tinker.internal.util

import com.tencent.tinker.internal.TinkerError
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.enums.enumEntries

internal val TinkerError.Type.errorCode: Int
    get() = (group.ordinal shl 8) or typeCode

@OptIn(ExperimentalStdlibApi::class, ExperimentalContracts::class)
internal inline fun <reified T> expected(
    action: String,
    scope: () -> Unit
) where T : Enum<T>, T : TinkerError.Type {
    contract {
        callsInPlace(scope, InvocationKind.EXACTLY_ONCE)
    }
    try {
        scope()
    } catch (throwable: Throwable) {
        if (throwable is TinkerError) {
            throw throwable
        }
        throw TinkerError(
            enumEntries<T>()[0],
            "Cannot $action because of unexpected error.",
            throwable
        )
    }
}