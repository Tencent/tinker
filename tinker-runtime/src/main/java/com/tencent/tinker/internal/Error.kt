package com.tencent.tinker.internal

internal class TinkerError(
    val type: Type,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class TypeGroup {
        UNEXPECTED,
        LOAD,
        LOAD_CODE,
        LOAD_CODE_INJECT_PATH,
        LOAD_CODE_NEW_CLASS_LOADER,
        LOAD_RESOURCE,
        DEPLOY,
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