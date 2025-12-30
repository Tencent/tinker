package com.tencent.tinker.internal.module.hidden

import android.app.Application
import android.content.Context
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

private val Field.descriptor: String
    get() = buildString {
        declaringClass.name.let(::append)
        append(".")
        name.let(::append)
        append(" -> ")
        genericType.typeName.let(::append)
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
) : (Any) -> Unit {

    override fun invoke(value: Any) {
        field.set(instance, value)
    }
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

private fun Class<*>.constructorOrNull(vararg parameterTypes: Class<*>): Constructor<*>? {
    var current = this
    while (true) {
        current.declaredConstructors
            .firstOrNull {
                it.parameterTypes.contentEquals(parameterTypes)
            }
            ?.apply { isAccessible = true }
            ?.let { return it }
        current = current.superclass ?: return null
    }
}

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

internal inline fun <reified T> Class<T>.createWithDefaultConstructor(): T =
    constructor().newInstance() as T

/**
 * Base class of delegate classes for hidden framework classes.
 */
internal abstract class HiddenClassDelegate {
    abstract val original: Any
}

private class HiddenFieldDelegate(
    /**
     * Use lazy to avoid unnecessary reflection.
     */
    private val fieldGetter: () -> Field,
) {
    private val field by lazy {
        fieldGetter.invoke()
    }

    inline operator fun <reified T> getValue(
        delegate: HiddenClassDelegate,
        property: KProperty<*>
    ): T {
        expected<ErrorType>("get field value") {
            try {
                return field.get(delegate.original) as T
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Type of field \"${field.descriptor}\" is not \"${T::class.java.name}\".",
                    exception,
                )
            }
        }
    }

    inline operator fun <reified T> setValue(
        instance: HiddenClassDelegate,
        property: KProperty<*>,
        value: T
    ) {
        expected<ErrorType>("set field value") {
            try {
                field.set(instance.original, value)
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Type of field \"${field.descriptor}\" is not \"${T::class.java.name}\".",
                    exception,
                )
            }
        }
    }
}

private class FixedFieldDelegate(private val field: Field) {
    inline operator fun <reified T> getValue(instance: Any, property: KProperty<*>): T {
        expected<ErrorType>("get field value") {
            try {
                return field.get(instance) as T
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Type of field \"${field.descriptor}\" is not \"${T::class.java.name}\".",
                    exception,
                )
            }
        }
    }

    inline operator fun <reified T> setValue(instance: Any, property: KProperty<*>, value: T) {
        expected<ErrorType>("set field value") {
            try {
                field.set(instance, value)
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Type of field \"${field.descriptor}\" is not \"${T::class.java.name}\".",
                    exception,
                )
            }
        }
    }
}

private class DynamicFieldDelegate(private val name: String) {
    inline operator fun <reified T> getValue(instance: Any, property: KProperty<*>): T {
        expected<ErrorType>("get field value") {
            val field = instance.javaClass.field(name)
            try {
                return field.get(instance) as T
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Type of field \"${field.descriptor}\" is not \"${T::class.java.name}\".",
                    exception,
                )
            }
        }
    }

    inline operator fun <reified T> setValue(instance: Any, property: KProperty<*>, value: T) {
        expected<ErrorType>("set field value") {
            val field = instance.javaClass.field(name)
            try {
                field.set(instance, value)
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Type of field \"${field.descriptor}\" is not \"${T::class.java.name}\".",
                    exception,
                )
            }
        }
    }
}

private class ClassLoaderParentInjector(private val value: ClassLoader) : (ClassLoader) -> Unit {

    companion object {
        private val classLoaderParentField by lazy {
            ClassLoader::class.java.field("parent")
        }
    }

    override fun invoke(original: ClassLoader) {
        classLoaderParentField.set(original, value)
    }
}

/**
 * Creates a lazy-setter to set class loader parent as `value`.
 */
internal fun classLoaderParentInjector(value: ClassLoader): ClassLoader.() -> Unit =
    ClassLoaderParentInjector(value)

internal val Application.base: Context by DynamicFieldDelegate("mBase")

internal val Context.classLoaderSetter: ReflectSetter
    get() = ReflectSetter(
        field = javaClass.field("mClassLoader"),
        instance = this,
    )

/**
 * Delegate of hidden class `android.app.LoadedApk`.
 */
internal class LoadedApkDelegate(override val original: Any) : HiddenClassDelegate() {
    val classLoaderSetter: ReflectSetter
        get() = ReflectSetter(
            field = javaClass.field("mClassLoader"),
            instance = original,
        )
}

internal val Context.packageInfo: LoadedApkDelegate?
    get() = javaClass.field("mPackageInfo").get(this)
        ?.let(::LoadedApkDelegate)

internal val Resources.classLoaderSetter: ReflectSetter
    get() = ReflectSetter(
        field = javaClass.field("mClassLoader"),
        instance = this,
    )

/**
 * Delegate of hidden class `android.graphics.drawable.DrawableInflater`.
 */
@JvmInline
internal value class DrawableInflaterDelegate(private val original: Any) {
    val classLoaderSetter: ReflectSetter
        get() = ReflectSetter(
            field = javaClass.field("mClassLoader"),
            instance = original,
        )
}

internal val Resources.drawableInflater: DrawableInflaterDelegate?
    get() = javaClass.field("mDrawableInflater").get(this)
        ?.let(::DrawableInflaterDelegate)

internal typealias JavaMutableList<T> = java.util.List<T>

/**
 * Delegate of hidden class `dalvik.system.DexPathList`.
 */
internal class DexPathListDelegate(override val original: Any) : HiddenClassDelegate() {

    private val dexElementsField by lazy {
        original.javaClass.field("dexElements")
    }

    @get:RequiresApi(Build.VERSION_CODES.M)
    private val nativeLibraryPathElementsField by lazy {
        original.javaClass.field("nativeLibraryPathElements")
    }

    internal var dexElements: Array<Any> by HiddenFieldDelegate {
        dexElementsField
    }

    fun lazySetDexElements(value: Array<Any>): ReflectLazySetter =
        ReflectLazySetter(
            field = dexElementsField,
            instance = original,
            value = value
        )

    @RequiresApi(Build.VERSION_CODES.M)
    fun lazySetNativeLibraryPathElements(value: Array<Any>): ReflectLazySetter =
        ReflectLazySetter(
            field = nativeLibraryPathElementsField,
            instance = original,
            value = value
        )

    private val nativeLibraryDirectoriesField by lazy {
        original.javaClass.field("nativeLibraryDirectories")
    }

    @get:RequiresApi(Build.VERSION_CODES.M)
    val nativeLibraryDirectoriesV23: JavaMutableList<File>? by HiddenFieldDelegate {
        nativeLibraryDirectoriesField
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun lazySetNativeLibraryDirectoriesV23(value: ArrayList<File>) =
        ReflectLazySetter(
            field = nativeLibraryDirectoriesField,
            instance = original,
            value = value
        )

    val nativeLibraryDirectoriesOld: Array<File> by HiddenFieldDelegate {
        nativeLibraryDirectoriesField
    }

    fun lazySetNativeLibraryDirectoriesOld(value: Array<File>) =
        ReflectLazySetter(
            field = nativeLibraryDirectoriesField,
            instance = original,
            value = value
        )

    private val systemNativeLibraryDirectoriesField by lazy {
        original.javaClass.field("systemNativeLibraryDirectories")
    }

    @get:RequiresApi(Build.VERSION_CODES.M)
    @set:RequiresApi(Build.VERSION_CODES.M)
    var systemNativeLibraryDirectories: List<File> by HiddenFieldDelegate {
        systemNativeLibraryDirectoriesField
    }


    @get:RequiresApi(Build.VERSION_CODES.N)
    private val definingContextField by lazy {
        original.javaClass.field("definingContext")
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val definingContext: Any by HiddenFieldDelegate {
        definingContextField
    }

    private val makeDexElementsMethodV24 by lazy {
        original.javaClass.method(
            "makeDexElements",
            List::class.java,
            File::class.java,
            List::class.java,
            ClassLoader::class.java,
        )
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun makeDexElementsV24(
        files: ArrayList<File>,
        optimizedDirectory: File?,
        suppressedExceptions: ArrayList<IOException>,
    ): Array<Any> {
        try {
            @Suppress("UNCHECKED_CAST")
            return makeDexElementsMethodV24.invoke(
                original,
                files,
                optimizedDirectory,
                suppressedExceptions,
                definingContext,
            ) as Array<Any>
        } catch (exception: ClassCastException) {
            throw TinkerError(
                ErrorType.CAST_FAILED,
                "Return type of method \"${makeDexElementsMethodV24.descriptor}\" is not \"Array<Any>\".",
                exception,
            )
        }
    }

    @get:RequiresApi(Build.VERSION_CODES.M)
    private val makeDexElementsMethodV23 by lazy {
        original.javaClass.method(
            "makePathElements",
            List::class.java,
            File::class.java,
            List::class.java,
        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun makeDexElementsV23(
        files: ArrayList<File>,
        optimizedDirectory: File?,
        suppressedExceptions: ArrayList<IOException>,
    ): Array<Any> {
        try {
            @Suppress("UNCHECKED_CAST")
            return makeDexElementsMethodV23.invoke(
                original,
                files,
                optimizedDirectory,
                suppressedExceptions,
            ) as Array<Any>
        } catch (exception: ClassCastException) {
            throw TinkerError(
                ErrorType.CAST_FAILED,
                "Return type of method \"${makeDexElementsMethodV23.descriptor}\" is not \"Array<Any>\".",
                exception,
            )
        }
    }

    private val makeDexElementsOld by lazy {
        original.javaClass.method(
            "makeDexElements",
            ArrayList::class.java,
            File::class.java,
            ArrayList::class.java,
        )
    }

    private fun makeDexElementsOld(
        files: ArrayList<File>,
        optimizedDirectory: File?,
        suppressedExceptions: ArrayList<IOException>,
    ): Array<Any> {
        try {
            @Suppress("UNCHECKED_CAST")
            return makeDexElementsOld.invoke(
                original,
                files,
                optimizedDirectory,
                suppressedExceptions,
            ) as Array<Any>
        } catch (exception: ClassCastException) {
            throw TinkerError(
                ErrorType.CAST_FAILED,
                "Return type of method \"${makeDexElementsOld.descriptor}\" is not \"Array<Any>\".",
                exception,
            )
        }
    }

    fun makeDexElements(
        files: ArrayList<File>,
        optimizedDirectory: File?,
        suppressedExceptions: ArrayList<IOException>,
    ): Array<Any> {
        var firstThrowable = null as Throwable?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                return makeDexElementsV24(
                    files,
                    optimizedDirectory,
                    suppressedExceptions,
                )
            } catch (throwable: Throwable) {
                firstThrowable = throwable
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return makeDexElementsV23(
                    files,
                    optimizedDirectory,
                    suppressedExceptions,
                )
            } catch (throwable: Throwable) {
                if (firstThrowable == null) {
                    firstThrowable = throwable
                }
            }
        }
        try {
            return makeDexElementsOld(
                files,
                optimizedDirectory,
                suppressedExceptions,
            )
        } catch (throwable: Throwable) {
            if (firstThrowable == null) {
                firstThrowable = throwable
            }
        }
        // FIXME: Remove unnecessary suppression until Kotlin version is upgraded.
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        throw firstThrowable!!
    }

    @get:RequiresApi(Build.VERSION_CODES.O)
    private val makeLibraryElementsMethodV26 by lazy {
        original.javaClass.method(
            "makePathElements",
            List::class.java,
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun makeLibraryElementsV26(
        files: ArrayList<File>,
    ): Array<Any> {
        try {
            @Suppress("UNCHECKED_CAST")
            return makeLibraryElementsMethodV26.invoke(
                original,
                files,
            ) as Array<Any>
        } catch (exception: ClassCastException) {
            throw TinkerError(
                ErrorType.CAST_FAILED,
                "Return type of method \"${makeLibraryElementsMethodV26.descriptor}\" is not \"Array<Any>\".",
                exception,
            )
        }
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val makeLibraryElementsMethodV24 by lazy {
        original.javaClass.method(
            "makePathElements",
            List::class.java,
            List::class.java,
            ClassLoader::class.java,
        )
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun makeLibraryElementsV24(
        files: ArrayList<File>,
        suppressedExceptions: ArrayList<IOException>,
    ): Array<Any> {
        try {
            @Suppress("UNCHECKED_CAST")
            return makeLibraryElementsMethodV24.invoke(
                original,
                files,
                suppressedExceptions,
                definingContext,
            ) as Array<Any>
        } catch (exception: ClassCastException) {
            throw TinkerError(
                ErrorType.CAST_FAILED,
                "Return type of method \"${makeLibraryElementsMethodV24.descriptor}\" is not \"Array<Any>\".",
                exception,
            )
        }
    }

    @get:RequiresApi(Build.VERSION_CODES.M)
    private val makeLibraryElementsMethodV23 by lazy {
        original.javaClass.method(
            "makePathElements",
            List::class.java,
            File::class.java,
            List::class.java,
        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun makeLibraryElementsV23(
        files: ArrayList<File>,
        suppressedExceptions: ArrayList<IOException>,
    ): Array<Any> {
        try {
            @Suppress("UNCHECKED_CAST")
            return makeLibraryElementsMethodV23.invoke(
                original,
                files,
                null,
                suppressedExceptions,
            ) as Array<Any>
        } catch (exception: ClassCastException) {
            throw TinkerError(
                ErrorType.CAST_FAILED,
                "Return type of method \"${makeLibraryElementsMethodV23.descriptor}\" is not \"Array<Any>\".",
                exception,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    internal fun makeLibraryElements(
        files: ArrayList<File>,
        suppressedExceptions: ArrayList<IOException>,
    ): Array<Any> {
        var firstThrowable = null as Throwable?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                return makeLibraryElementsV26(files)
            } catch (throwable: Throwable) {
                firstThrowable = throwable
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                return makeLibraryElementsV24(files, suppressedExceptions)
            } catch (throwable: Throwable) {
                if (firstThrowable == null) {
                    firstThrowable = throwable
                }
            }
        }
        try {
            return makeLibraryElementsV23(files, suppressedExceptions)
        } catch (throwable: Throwable) {
            if (firstThrowable == null) {
                firstThrowable = throwable
            }
        }
        // FIXME: Remove unnecessary suppression until Kotlin version is upgraded.
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        throw firstThrowable!!
    }
}

internal val ClassLoader.pathList: DexPathListDelegate
    get() = javaClass.field("pathList").get(this)!!.let(::DexPathListDelegate)
