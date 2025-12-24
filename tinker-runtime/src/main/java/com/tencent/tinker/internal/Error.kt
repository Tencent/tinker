package com.tencent.tinker.internal

internal class TinkerError(
    val type: Type,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class TypeGroup {
        UNEXPECTED,
        LOAD,
        LOAD_DEX,
        LOAD_DEX_OLD,
        LOAD_DEX_NOUGAT,
        MODULE_PATCH,
        MODULE_OAT,
        MODULE_LAYOUT,
        HIDDEN,
    }

    interface Type {
        val group: TypeGroup
        val typeCode: Int
    }
}
