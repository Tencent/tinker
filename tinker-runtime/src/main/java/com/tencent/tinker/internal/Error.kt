package com.tencent.tinker.internal

internal class TinkerError(
    val type: Type,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class TypeGroup {
        UNEXPECTED,
        MODULE_PATCH,
        MODULE_OAT,
        MODULE_LAYOUT
    }

    interface Type {
        val group: TypeGroup
        val typeCode: Int
    }
}
