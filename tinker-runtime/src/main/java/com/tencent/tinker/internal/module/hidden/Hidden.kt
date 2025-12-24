package com.tencent.tinker.internal.module.hidden

import android.app.Application
import android.content.res.Resources
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.util.expected
import java.io.File
import java.io.IOException
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KProperty

private enum class ErrorType : TinkerError.Type {
    UNEXPECTED,
    NO_SUCH_ELEMENT,
    CAST_FAILED;

    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.HIDDEN

    override val typeCode: Int
        get() = ordinal
}

@VisibleForTesting
internal fun hiddenErrorTypeOfForTesting(key: String): TinkerError.Type =
    ErrorType.valueOf(key)

internal class ReflectLazySetter(
    private val field: Field,
    private val instance: Any,
    private val value: Any,
) : Runnable {
    override fun run() {
        field.set(instance, value)
    }
}

internal class ReflectSetter(
    private val field: Field,
    private val instance: Any,
) {
    fun set(value: Any) {
        field.set(instance, value)
    }
}

internal class ReflectInjector(
    private val field: Field,
    private val value: Any,
) {
    fun inject(instance: Any) {
        field.set(instance, value)
    }
}

private val Method.descriptor: String
    get() = buildString {
        declaringClass.name.let(::append)
        append(".")
        name.let(::append)
        append("(")
        genericParameterTypes
            .joinToString(", ") { it.typeName }
            .let(::append)
        append(") -> ")
        genericReturnType.typeName.let(::append)
    }

private fun Class<*>.fieldOrNull(name: String): Field? {
    var current = this
    while (true) {
        current.declaredFields
            .firstOrNull { it.name == name }
            ?.apply {
                isAccessible = true
            }
            ?.let { return it }
        current = current.superclass ?: return null
    }
}

private fun Class<*>.field(field: String): Field =
    fieldOrNull(field)
        ?: throw TinkerError(
            ErrorType.NO_SUCH_ELEMENT,
            "Cannot find field \"${name}.${field}\"."
        )

private fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? {
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

private fun Class<*>.method(method: String, vararg parameterTypes: Class<*>): Method =
    methodOrNull(method, *parameterTypes)
        ?: buildString {
            append("Cannot find method \"")
            append(name)
            append(".")
            append(method)
            append("(")
            append(parameterTypes.joinToString(",") { it.name })
            append(")\".")
        }.let {
            throw TinkerError(ErrorType.NO_SUCH_ELEMENT, it)
        }

private fun Class<*>.constructorOrNull(vararg parameterTypes: Class<*>): Constructor<*>? =
    constructors
        .firstOrNull { it.parameterTypes.contentEquals(parameterTypes) }

private fun Class<*>.constructor(vararg parameterTypes: Class<*>): Constructor<*> =
    constructorOrNull(*parameterTypes)
        ?: buildString {
            append("Cannot find constructor \"")
            append(name)
            append(".<init>(")
            append(parameterTypes.joinToString(",") { it.name })
            append(")\".")
        }.let {
            throw TinkerError(ErrorType.NO_SUCH_ELEMENT, it)
        }

private class FieldDelegate<T>(
    val name: String,
) {
    inline operator fun <reified T> getValue(instance: Any, property: KProperty<*>): T {
        expected<ErrorType>("get field value") {
            try {
                return instance.javaClass.field(name).get(instance) as T
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Type of field \"${instance.javaClass.name}.${name}\" is not \"${T::class.java.name}\".",
                    exception,
                )
            }
        }
    }

    inline operator fun <reified T> setValue(instance: Any, property: KProperty<*>, value: T) {
        expected<ErrorType>("set field value") {
            try {
                instance.javaClass.field(name).set(instance, value)
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Type of field \"${instance.javaClass.name}.${name}\" is not \"${T::class.java.name}\".",
                    exception,
                )
            }
        }
    }
}

private const val CLASS_LOADER_PARENT_NAME = "parent"

internal fun Class<ClassLoader>.parentLazyInjector(value: ClassLoader): ReflectInjector =
    ReflectInjector(
        field = field(CLASS_LOADER_PARENT_NAME),
        value = value,
    )


private const val M_BASE_NAME = "mBase"

internal val Application.base: Any
        by FieldDelegate<Any>(M_BASE_NAME)

private const val M_CLASS_LOADER_NAME = "mClassLoader"

internal val Any.classLoaderSetter: ReflectSetter
    get() = ReflectSetter(
        field = javaClass.field(M_CLASS_LOADER_NAME),
        instance = this,
    )

private const val M_PACKAGE_INFO_NAME = "mPackageInfo"

internal val Any.packageInfo: Any?
        by FieldDelegate<Any?>(M_PACKAGE_INFO_NAME)

private const val M_DRAWABLE_INFLATER = "mDrawableInflater"

internal val Resources.drawableInflater: Any?
        by FieldDelegate<Any?>(M_DRAWABLE_INFLATER)


internal val ClassLoader.pathList: Any
        by FieldDelegate<Any>("pathList")

private const val DEX_ELEMENTS_NAME = "dexElements"

internal var Any.dexElements: Array<Any>
        by FieldDelegate<Array<Any>>(DEX_ELEMENTS_NAME)

internal fun Any.lazySetDexElements(value: Array<Any>): ReflectLazySetter =
    ReflectLazySetter(
        field = javaClass.field(DEX_ELEMENTS_NAME),
        instance = this,
        value = value
    )

private const val NATIVE_LIBRARY_PATH_ELEMENTS_NAME = "nativeLibraryPathElements"

@RequiresApi(Build.VERSION_CODES.M)
internal fun Any.lazySetNativeLibraryPathElements(value: Array<Any>): ReflectLazySetter =
    ReflectLazySetter(
        field = javaClass.field(NATIVE_LIBRARY_PATH_ELEMENTS_NAME),
        instance = this,
        value = value
    )

private const val NATIVE_LIBRARY_DIRECTORIES_NAME = "nativeLibraryDirectories"

internal typealias JavaMutableList<T> = java.util.List<T>

@get:RequiresApi(Build.VERSION_CODES.M)
internal val Any.nativeLibraryDirectoriesV23: JavaMutableList<File>?
        by FieldDelegate<JavaMutableList<File>?>(NATIVE_LIBRARY_DIRECTORIES_NAME)

@RequiresApi(Build.VERSION_CODES.M)
internal fun Any.lazySetNativeLibraryDirectoriesV23(value: ArrayList<File>) =
    ReflectLazySetter(
        field = javaClass.field(NATIVE_LIBRARY_DIRECTORIES_NAME),
        instance = this,
        value = value
    )

internal var Any.nativeLibraryDirectoriesOld: Array<File>
        by FieldDelegate<Array<File>>(NATIVE_LIBRARY_DIRECTORIES_NAME)

private const val DEX_PATH_LIST_SYSTEM_NATIVE_LIBRARY_DIRECTORIES_NAME =
    "systemNativeLibraryDirectories"

@get:RequiresApi(Build.VERSION_CODES.M)
@set:RequiresApi(Build.VERSION_CODES.M)
internal var Any.systemNativeLibraryDirectories: List<File>
        by FieldDelegate<List<File>>(DEX_PATH_LIST_SYSTEM_NATIVE_LIBRARY_DIRECTORIES_NAME)

private const val MAKE_PATH_ELEMENTS_NAME = "makePathElements"

@get:RequiresApi(Build.VERSION_CODES.M)
private val Any.makePathElementsMethod: Method?
    get() {
        try {
            return javaClass.method(
                MAKE_PATH_ELEMENTS_NAME,
                List::class.java,
                File::class.java,
                List::class.java,
            )
        } catch (_: TinkerError) {
            // Ignored.
        }
        try {
            return javaClass.method(
                MAKE_PATH_ELEMENTS_NAME,
                ArrayList::class.java,
                File::class.java,
                ArrayList::class.java,
            )
        } catch (_: TinkerError) {
            // Ignored.
        }
        return null
    }

private const val DEX_PATH_LIST_MAKE_DEX_ELEMENTS_NAME = "makeDexElements"

private val Any.makeDexElementsMethod: Method?
    get() {
        try {
            return javaClass.method(
                DEX_PATH_LIST_MAKE_DEX_ELEMENTS_NAME,
                List::class.java,
                File::class.java,
                List::class.java,
            )
        } catch (_: TinkerError) {
            // Ignored.
        }
        try {
            return javaClass.method(
                DEX_PATH_LIST_MAKE_DEX_ELEMENTS_NAME,
                ArrayList::class.java,
                File::class.java,
                ArrayList::class.java,
            )
        } catch (_: TinkerError) {
            // Ignored.
        }
        return null
    }

internal fun Any.makeElements(
    files: ArrayList<File>,
    optimizedDirectory: File?,
    suppressedExceptions: ArrayList<IOException>,
): Array<Any> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        makePathElementsMethod?.let { method ->
            try {
                @Suppress("UNCHECKED_CAST")
                return method.invoke(
                    this,
                    files,
                    optimizedDirectory,
                    suppressedExceptions
                ) as Array<Any>
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Return type of method \"${method.descriptor}\" is not \"Array<Any>\".",
                    exception,
                )
            }
        }

    }
    makeDexElementsMethod?.let { method ->
        try {
            @Suppress("UNCHECKED_CAST")
            return method.invoke(
                this,
                files,
                optimizedDirectory,
                suppressedExceptions
            ) as Array<Any>
        } catch (exception: ClassCastException) {
            throw TinkerError(
                ErrorType.CAST_FAILED,
                "Return type of method \"${method.descriptor}\" is not \"Array<Any>\".",
                exception,
            )
        }
    }
    buildString {
        append("Cannot find method \"")
        append(javaClass.name)
        append(".")
        append(MAKE_PATH_ELEMENTS_NAME)
        append("/")
        append(DEX_PATH_LIST_MAKE_DEX_ELEMENTS_NAME)
        append("(")
        append("java.util.List/java.util.ArrayList")
        append(", ")
        append("java.io.File")
        append(", ")
        append("java.util.List/java.util.ArrayList")
        append(").")
    }.let {
        throw TinkerError(ErrorType.NO_SUCH_ELEMENT, it)
    }
}

@get:RequiresApi(Build.VERSION_CODES.O)
private val Any.makeNativeLibraryPathElements: Method
    get() = javaClass.method(
        MAKE_PATH_ELEMENTS_NAME,
        List::class.java,
    )

internal fun Any.makeNativeLibraryElements(files: List<File>): Array<Any> =
    makeNativeLibraryPathElements.let { method ->
        try {
            @Suppress("UNCHECKED_CAST")
            method.invoke(this, files) as Array<Any>
        } catch (exception: ClassCastException) {
            throw TinkerError(
                ErrorType.CAST_FAILED,
                "Return type of method \"${method.descriptor}\" is not \"Array<Any>\".",
                exception,
            )
        }
    }
