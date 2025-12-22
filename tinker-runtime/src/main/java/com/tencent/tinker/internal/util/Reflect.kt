package com.tencent.tinker.internal.util

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

internal fun Class<*>.fieldOrNull(name: String): Field? {
    var current = this
    while (true) {
        current.declaredFields
            .firstOrNull { it.name == name }
            ?.let { return it }
        current = current.superclass ?: return null
    }
}

internal fun Class<*>.field(name: String): Field =
    fieldOrNull(name)
        ?: throw NoSuchFieldException("Cannot find field \"${name}\"")

internal fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? {
    var current = this
    while (true) {
        current.declaredMethods
            .firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(parameterTypes)
            }
            ?.apply { isAccessible = true }
            ?.let { return it }
        current = current.superclass ?: return null
    }
}

internal fun Class<*>.method(name: String, vararg parameterTypes: Class<*>): Method =
    methodOrNull(name, *parameterTypes)
        ?: throw NoSuchMethodException("Cannot find method \"${name}\"")

internal fun Class<*>.constructorOrNull(vararg parameterTypes: Class<*>): Constructor<*>? =
    constructors
        .firstOrNull { it.parameterTypes.contentEquals(parameterTypes) }

internal fun Class<*>.constructor(vararg parameterTypes: Class<*>): Constructor<*> =
    constructorOrNull(*parameterTypes)
        ?: throw NoSuchMethodException("Cannot find constructor")