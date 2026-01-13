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
        LOAD_RESOURCE,
        DEPLOY_LEGACY,
        DEPLOY_LEGACY_DEX,
        DEPLOY_LEGACY_LIBRARY,
        DEPLOY_LEGACY_RESOURCE,
        MODULE_PATCH,
        MODULE_OAT,
        MODULE_LAYOUT,
        MODULE_VALIDATE,
        HIDDEN,
    }

    interface Type {
        val group: TypeGroup
        val typeCode: Int
    }
}
