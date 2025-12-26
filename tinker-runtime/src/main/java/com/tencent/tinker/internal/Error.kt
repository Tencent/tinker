package com.tencent.tinker.internal

internal class TinkerError(
    val type: Type,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class TypeGroup {
        UNEXPECTED,
        LOAD,
        LOAD_CODE,
        LOAD_CODE_OLD,
        LOAD_CODE_NOUGAT,
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
