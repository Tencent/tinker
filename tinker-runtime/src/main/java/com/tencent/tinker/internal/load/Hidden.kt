package com.tencent.tinker.internal.load

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.tryEach
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.reflect.KProperty

// The module is designed to:
//
// - Allow other modules to access hidden members obtained by reflection in a manner similar to accessing normal
//   members.
// - Provide access functions with initialized reflected hidden fields/methods through partial application, moving the
//   step of reflecting hidden fields/methods to the time the access function is created, instead of when the access
//   function is called.
//
// Here is an explanation of access functions. Access functions are used to access properties or virtual properties
// (properties without a real existing field, only have getters and/or setters, similar to properties without backing
// fields in Kotlin) of instances. These functions are constructed through partial application of reflection access.
// Reflection access consists of three parts:
//
// - The instance.
// - The reflected member, usually a field or getter/setter method.
// - The value.
//
// To avoid confusion, some members that provide access functions follow these naming rules:
//
// - Whether it includes "self". "self" means the provided access function is used to access the instance which provides
//   this function, not be used for other instances. Access function with "self" includes "instance" part of reflection
//   access.
// - Whether it includes "lazy". "lazy" generally only named for setter. Lazy access function is used to pre-set a
//   value, but the setting only occurs when the access function is invoked. Access function with "self" includes
//   "value" part of reflection access. A lazy getter is meaningless, because all getters involved in this module are
//   considered to have no side effects.
//
// If the instance generic type is T and the value generic type is V, access function types can be organized into the
// following list:
//
// - getter / non-lazy / non-self : T.() -> V
// - getter / non-lazy / self     : () -> V
// - getter / lazy                : meaningless
// - setter / non-lazy / non-self : T.(V) -> Unit
// - setter / non-lazy / self     : (V) -> Unit
// - setter / lazy     / non-self : T.() -> Unit
// - setter / lazy     / self     : () -> Unit

private val Type.name: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        typeName
    } else {
        toString()
    }

private val Field.descriptor: String
    get() = buildString {
        declaringClass.name.let(::append)
        append(".")
        name.let(::append)
        append(" -> ")
        genericType.name.let(::append)
    }

private val Method.descriptor: String
    get() = buildString {
        declaringClass.name.let(::append)
        append(".")
        name.let(::append)
        append("(")
        genericParameterTypes
            .joinToString(", ") { it.name }
            .let(::append)
        append(") -> ")
        genericReturnType.name.let(::append)
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
        ?: throw Tinker.Error(
            Tinker.Error.Load.NO_SUCH_ELEMENT,
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
            throw Tinker.Error(Tinker.Error.Load.NO_SUCH_ELEMENT, it)
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
            throw Tinker.Error(Tinker.Error.Load.NO_SUCH_ELEMENT, it)
        }

/**
 * Base class of delegate classes for hidden framework classes.
 */
internal abstract class HiddenClass {
    abstract val original: Any
}

/**
 * Delegate for accessing hidden fields.
 */
private class HiddenField(
    /**
     * Use lazy to avoid unnecessary reflection.
     */
    private val lazyField: () -> Field
) {
    private val field by lazy {
        lazyField()
    }

    inline operator fun <reified T> getValue(
        instance: HiddenClass,
        property: KProperty<*>
    ): T {
        expected<Tinker.Error.Load>("get field value") {
            try {
                return instance.original.let(field::get) as T
            } catch (exception: ClassCastException) {
                throw Tinker.Error(
                    Tinker.Error.Load.CAST_FAILED,
                    "Type of field \"${field.descriptor}\" is not \"${T::class.java.name}\".",
                    exception,
                )
            }
        }
    }

    inline operator fun <reified T> setValue(
        instance: HiddenClass,
        property: KProperty<*>,
        value: T
    ) {
        expected<Tinker.Error.Load>("set field value") {
            try {
                field.set(instance.original, value)
            } catch (exception: ClassCastException) {
                throw Tinker.Error(
                    Tinker.Error.Load.CAST_FAILED,
                    "Type of field \"${field.descriptor}\" is not \"${T::class.java.name}\".",
                    exception,
                )
            }
        }
    }
}

private class GetterWithInstanceDelegate<T : HiddenClass, V>(
    private val field: Field,
) : (T) -> V {
    override fun invoke(instance: T): V {
        try {
            @Suppress("UNCHECKED_CAST")
            return field.get(instance.original) as V
        } catch (exception: ClassCastException) {
            throw Tinker.Error(
                Tinker.Error.Load.CAST_FAILED,
                "Type of field \"${field.descriptor}\" is not unexpected.",
                exception,
            )
        }
    }
}

private class SetterWithInstanceDelegate<T : HiddenClass, V>(
    private val field: Field,
) : (T, V) -> Unit {
    override fun invoke(instance: T, value: V) {
        field.set(instance.original, value)
    }
}

private class DelegateSetterWithInstanceDelegate<T : HiddenClass, V: HiddenClass>(
    private val field: Field,
) : (T, V) -> Unit {
    override fun invoke(instance: T, value: V) {
        field.set(instance.original, value.original)
    }
}

private class SelfGetter<V>(
    private val field: Field,
    private val instance: Any,
) : () -> V {
    override fun invoke(): V {
        try {
            @Suppress("UNCHECKED_CAST")
            return field.get(instance) as V
        } catch (exception: ClassCastException) {
            throw Tinker.Error(
                Tinker.Error.Load.CAST_FAILED,
                "Type of field \"${field.descriptor}\" is not unexpected.",
                exception,
            )
        }
    }
}

private class SelfSetter<V>(
    private val field: Field,
    private val instance: Any,
) : (V) -> Unit {
    override fun invoke(value: V) {
        field.set(instance, value)
    }
}

private class LazySelfSetter(
    private val instance: Any,
    private val field: Field,
    private val value: Any,
) : () -> Unit {
    override fun invoke() {
        field.set(instance, value)
    }
}

/**
 * Delegate of [android.app.Application] for accessing hidden members.
 */
internal class ApplicationDelegate(override val original: Application) : HiddenClass() {

    companion object {

        /**
         * Converts [Application] to delegate.
         */
        val Application.delegated: ApplicationDelegate
            get() = ApplicationDelegate(this)

        private val baseField by lazy {
            ContextWrapper::class.java.field("mBase")
        }
    }

    val base: ContextDelegate
        get() {
            val original = try {
                baseField.get(original)!! as Context
            } catch (exception: ClassCastException) {
                throw Tinker.Error(
                    Tinker.Error.Load.CAST_FAILED,
                    "Type of field \"${baseField.descriptor}\" is not \"${Context::class.java.name}\".",
                    exception,
                )
            }
            return ContextDelegate(original)
        }
}

/**
 * Delegate of [android.content.Context] for accessing hidden members.
 */
internal class ContextDelegate(override val original: Context) : HiddenClass() {

    private val classLoaderField by lazy {
        original.javaClass.field("mClassLoader")
    }

    val classLoaderSelfSetter: (ClassLoader) -> Unit
        get() = SelfSetter(
            field = classLoaderField,
            instance = original,
        )

    private val packageInfoField by lazy {
        original.javaClass.field("mPackageInfo")
    }

    val packageInfo: LoadedApkDelegate?
        get() = packageInfoField.get(original)
            ?.let(::LoadedApkDelegate)
}

/**
 * Delegate of hidden class `android.app.LoadedApk` for accessing hidden members.
 */
internal class LoadedApkDelegate(override val original: Any) : HiddenClass() {

    companion object {
        private val originalClass by lazy @SuppressLint("PrivateApi") {
            Class.forName("android.app.LoadedApk")
        }

        private val resDirField by lazy {
            originalClass.field("mResDir")
        }

        val resDirGetter: LoadedApkDelegate.() -> String
            get() = GetterWithInstanceDelegate(
                field = resDirField,
            )

        val resDirSetter: LoadedApkDelegate.(String) -> Unit
            get() = SetterWithInstanceDelegate(
                field = resDirField,
            )
    }

    private val classLoaderField by lazy {
        originalClass.field("mClassLoader")
    }

    val classLoaderSelfSetter: (ClassLoader) -> Unit
        get() = SelfSetter(
            field = classLoaderField,
            instance = original,
        )
}

/**
 * Delegate of hidden class `android.graphics.drawable.DrawableInflater` for accessing hidden
 * members.
 */
internal class DrawableInflaterDelegate(override val original: Any) : HiddenClass() {

    private val classLoaderField by lazy {
        original.javaClass.field("mClassLoader")
    }

    val classLoaderSelfSetter: (ClassLoader) -> Unit
        get() = SelfSetter(
            field = classLoaderField,
            instance = original,
        )
}

/**
 * Delegate of [java.lang.ClassLoader] or custom implementation from manufacturers for accessing
 * hidden members.
 */
internal class ClassLoaderDelegate(override val original: ClassLoader) : HiddenClass() {

    companion object {
        val ClassLoader.delegated: ClassLoaderDelegate
            get() = ClassLoaderDelegate(this)

        internal fun lazySetParent(value: ClassLoader): ClassLoader.() -> Unit =
            ParentLazySetter(value)
    }

    private class ParentLazySetter(
        private val value: ClassLoader
    ) : (ClassLoader) -> Unit {
        companion object {
            private val field =
                ClassLoader::class.java.field("parent")
        }

        override fun invoke(original: ClassLoader) {
            field.set(original, value)
        }
    }

    private val pathListField by lazy {
        original.javaClass.field("pathList")
    }

    val pathList: DexPathListDelegate
        get() = pathListField.get(original)!!.let(::DexPathListDelegate)
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal typealias JavaMutableList<T> = java.util.List<T>

/**
 * Delegate of hidden class `dalvik.system.DexPathList` for accessing hidden members.
 */
internal class DexPathListDelegate(override val original: Any) : HiddenClass() {

    private val dexElementsField by lazy {
        original.javaClass.field("dexElements")
    }

    @get:RequiresApi(Build.VERSION_CODES.M)
    private val nativeLibraryPathElementsField by lazy {
        original.javaClass.field("nativeLibraryPathElements")
    }

    internal var dexElements: Array<Any> by HiddenField {
        dexElementsField
    }

    fun lazySelfSetDexElements(value: Array<Any>): () -> Unit =
        LazySelfSetter(
            instance = original,
            field = dexElementsField,
            value = value
        )

    @RequiresApi(Build.VERSION_CODES.M)
    fun lazySelfSetNativeLibraryPathElements(value: Array<Any>): () -> Unit =
        LazySelfSetter(
            instance = original,
            field = nativeLibraryPathElementsField,
            value = value
        )

    private val nativeLibraryDirectoriesField by lazy {
        original.javaClass.field("nativeLibraryDirectories")
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @get:RequiresApi(Build.VERSION_CODES.M)
    val nativeLibraryDirectoriesV23: JavaMutableList<File>? by HiddenField {
        nativeLibraryDirectoriesField
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun lazySelfSetNativeLibraryDirectoriesV23(value: ArrayList<File>): () -> Unit =
        LazySelfSetter(
            instance = original,
            field = nativeLibraryDirectoriesField,
            value = value
        )

    val nativeLibraryDirectoriesOld: Array<File> by HiddenField {
        nativeLibraryDirectoriesField
    }

    fun lazySelfSetNativeLibraryDirectoriesOld(value: Array<File>): () -> Unit =
        LazySelfSetter(
            instance = original,
            field = nativeLibraryDirectoriesField,
            value = value
        )

    @get:RequiresApi(Build.VERSION_CODES.M)
    private val systemNativeLibraryDirectoriesField by lazy {
        original.javaClass.field("systemNativeLibraryDirectories")
    }

    @get:RequiresApi(Build.VERSION_CODES.M)
    @set:RequiresApi(Build.VERSION_CODES.M)
    var systemNativeLibraryDirectories: List<File> by HiddenField @RequiresApi(Build.VERSION_CODES.N) {
        systemNativeLibraryDirectoriesField
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val definingContextField by lazy {
        original.javaClass.field("definingContext")
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val definingContext: Any by HiddenField @RequiresApi(Build.VERSION_CODES.N) {
        definingContextField
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
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
            throw Tinker.Error(
                Tinker.Error.Load.CAST_FAILED,
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
            throw Tinker.Error(
                Tinker.Error.Load.CAST_FAILED,
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
            throw Tinker.Error(
                Tinker.Error.Load.CAST_FAILED,
                "Return type of method \"${makeDexElementsOld.descriptor}\" is not \"Array<Any>\".",
                exception,
            )
        }
    }

    fun makeDexElements(
        files: ArrayList<File>,
        optimizedDirectory: File?,
        suppressedExceptions: ArrayList<IOException>,
    ): Array<Any> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            {
                makeDexElementsV24(files, optimizedDirectory, suppressedExceptions)
            }.let(::add)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            {
                makeDexElementsV23(files, optimizedDirectory, suppressedExceptions)
            }.let(::add)
        }
        {
            makeDexElementsOld(files, optimizedDirectory, suppressedExceptions)
        }.let(::add)
    }.let(::tryEach)

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
            throw Tinker.Error(
                Tinker.Error.Load.CAST_FAILED,
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
            throw Tinker.Error(
                Tinker.Error.Load.CAST_FAILED,
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
            throw Tinker.Error(
                Tinker.Error.Load.CAST_FAILED,
                "Return type of method \"${makeLibraryElementsMethodV23.descriptor}\" is not \"Array<Any>\".",
                exception,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun makeLibraryElements(
        files: ArrayList<File>,
        suppressedExceptions: ArrayList<IOException>,
    ): Array<Any> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            {
                makeLibraryElementsV26(files)
            }.let(::add)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            {
                makeLibraryElementsV24(files, suppressedExceptions)
            }.let(::add)
        }
        {
            makeLibraryElementsV23(files, suppressedExceptions)
        }.let(::add)
    }.let(::tryEach)
}

/**
 * Delegate of hidden class `android.app.ActivityThread` for accessing hidden members.
 */
internal class ActivityThreadDelegate(override val original: Any) : HiddenClass() {

    companion object {
        private val originalClass by lazy @SuppressLint("PrivateApi") {
            Class.forName("android.app.ActivityThread")
        }

        private val currentActivityThreadMethod by lazy {
            originalClass.method("currentActivityThread")
        }

        private val handlerField by lazy {
            originalClass.field("mH")
        }

        val currentActivityThread: ActivityThreadDelegate
            get() = currentActivityThreadMethod.invoke(null)!!.let(::ActivityThreadDelegate)
    }

    private class ReferencedPackagesSelfGetter(
        private val instance: ActivityThreadDelegate
    ) : () -> List<LoadedApkDelegate> {

        private val fields =
            buildList {
                originalClass.field("mPackages").let(::add)
                originalClass.fieldOrNull("mResourcePackages")?.let(::add)
            }

        override fun invoke(): List<LoadedApkDelegate> = buildList {
            fields.forEach { field ->
                field.get(instance.original)
                    .let {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            it as Map<Any, WeakReference<Any?>>
                        } catch (exception: ClassCastException) {
                            throw Tinker.Error(
                                Tinker.Error.Load.CAST_FAILED,
                                "Return type of field \"${field.descriptor}\" is not \"Map<Any, WeakReference<Any?>>\".",
                                exception,
                            )
                        }
                    }
                    .values
                    .mapNotNull { it.get() }
                    .map(::LoadedApkDelegate)
                    .let(::addAll)
            }
        }
    }

    internal val referencedPackagesSelfGetter: () -> List<LoadedApkDelegate>
        get() = ReferencedPackagesSelfGetter(this)

    val handler by lazy {
        handlerField.get(original)!!.let(::HandlerDelegate)
    }
}

/**
 * Delegate of [android.content.res.AssetManager] or custom implementation from manufacturers for
 * accessing hidden members.
 */
internal class AssetManagerDelegate(override val original: Any) : HiddenClass() {

    companion object {
        fun createInstanceLike(like: AssetManager): AssetManagerDelegate =
            like.javaClass.constructor().newInstance().let(::AssetManagerDelegate)
    }

    private val addAssetPathMethod by lazy {
        original.javaClass.method(
            "addAssetPath",
            String::class.java
        )
    }

    fun addAssetPath(path: String) {
        addAssetPathMethod.invoke(original, path)
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val addAssetPathAsSharedLibraryMethod by lazy {
        javaClass.method(
            "addAssetPathAsSharedLibrary",
            String::class.java
        )
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun addAssetPathAsSharedLibrary(path: String) {
        addAssetPathAsSharedLibraryMethod.invoke(this, path)
    }

    /**
     * Field was removed in Android P because replacing implementation to AssetManager 2 via commit
     * `b20a0ce59f59cb5ec857748e056cc341dbd13b92`.
     */
    private val stringBlocksField by lazy {
        original.javaClass.fieldOrNull("mStringBlocks")
    }

    /**
     * Field was removed in Android P because replacing implementation to AssetManager 2 via commit
     * `b20a0ce59f59cb5ec857748e056cc341dbd13b92`.
     */
    private val ensureStringBlocksMethod by lazy {
        original.javaClass.methodOrNull("ensureStringBlocks")
    }

    fun initializeStringBlocksIfNeeded() {
        val stringBlocksField = stringBlocksField ?: return
        val ensureStringBlocksMethod = ensureStringBlocksMethod ?: return
        stringBlocksField.set(original, null)
        ensureStringBlocksMethod.invoke(original)
    }
}

/**
 * Delegate of hidden class `android.app.ResourcesManager` for accessing hidden members.
 */
internal class ResourceManagerDelegate(override val original: Any) : HiddenClass() {

    companion object {
        private val originalClass by lazy @SuppressLint("PrivateApi") {
            Class.forName("android.app.ResourcesManager")
        }

        private val getInstanceMethod by lazy {
            originalClass.method("getInstance")
        }

        val instance by lazy {
            getInstanceMethod.invoke(null)!!.let(::ResourceManagerDelegate)
        }
    }

    private sealed class ReferencedResourcesSelfGetter : () -> List<ResourcesDelegate> {

        abstract val resources: List<Resources>

        override fun invoke(): List<ResourcesDelegate> =
            resources.map(::ResourcesDelegate)

        @RequiresApi(Build.VERSION_CODES.N)
        class V24(
            private val instance: ResourceManagerDelegate
        ) : ReferencedResourcesSelfGetter() {

            companion object {
                private val resourceReferencesField =
                    originalClass.field("mResourceReferences")
            }

            override val resources: List<Resources>
                get() = resourceReferencesField
                    .let {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            it.get(instance.original) as List<WeakReference<Resources>>
                        } catch (exception: ClassCastException) {
                            throw Tinker.Error(
                                Tinker.Error.Load.CAST_FAILED,
                                "Type of field \"${it.descriptor}\" is not \"List<WeakReference<Resources>>\".",
                                exception,
                            )
                        }
                    }
                    .mapNotNull { it.get() }
        }

        class Old(
            private val instance: ResourceManagerDelegate
        ) : ReferencedResourcesSelfGetter() {

            companion object {
                private val activeResourcesField =
                    originalClass.field("mActiveResources")
            }

            override val resources: List<Resources>
                get() = activeResourcesField
                    .let {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            it.get(instance.original) as Map<Any, WeakReference<Resources>>
                        } catch (exception: ClassCastException) {
                            throw Tinker.Error(
                                Tinker.Error.Load.CAST_FAILED,
                                "Type of field \"${it.descriptor}\" is not \"Map<Any, WeakReference<Resources>>\".",
                                exception,
                            )
                        }
                    }
                    .values
                    .mapNotNull { it.get() }
        }
    }

    internal val referencedResourcesSelfGetter: () -> List<ResourcesDelegate>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                {
                    ReferencedResourcesSelfGetter.V24(this@ResourceManagerDelegate)
                }.let(::add)
            }
            {
                ReferencedResourcesSelfGetter.Old(this@ResourceManagerDelegate)
            }.let(::add)
        }.let(::tryEach)

    @RequiresApi(Build.VERSION_CODES.N)
    private class ResourceImplementationSelfGetter(
        private val instance: ResourceManagerDelegate
    ) : () -> List<Pair<ResourceKeyDelegate, ResourceImplementationDelegate?>> {

        companion object {
            private val field =
                originalClass.field("mResourceImpls")
        }

        override fun invoke(): List<Pair<ResourceKeyDelegate, ResourceImplementationDelegate?>> =
            field
                .let { field ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        field.get(instance.original) as Map<Any, WeakReference<Any>>
                    } catch (exception: ClassCastException) {
                        throw Tinker.Error(
                            Tinker.Error.Load.CAST_FAILED,
                            "Type of field \"${field.descriptor}\" is not \"Map<Any, WeakReference<Any>>\".",
                            exception,
                        )
                    }
                }
                .mapNotNull {
                    Pair(
                        it.key.let(::ResourceKeyDelegate),
                        it.value.get()?.let(::ResourceImplementationDelegate),
                    )
                }
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    internal val resourceImplementationsSelfGetter: () -> List<Pair<ResourceKeyDelegate, ResourceImplementationDelegate?>>
        get() = ResourceImplementationSelfGetter(this)
}

/**
 * Delegate of [android.content.res.Resources] for accessing hidden members.
 */
internal class ResourcesDelegate(override val original: Resources) : HiddenClass() {

    companion object {
        val Resources.delegated: ResourcesDelegate
            get() = ResourcesDelegate(this)

        private val drawableInflaterField by lazy {
            Resources::class.java.field("mDrawableInflater")
        }

        private val typedArrayPoolField by lazy {
            Resources::class.java.field("mTypedArrayPool")
        }

        @SuppressLint("PrivateApi")
        private val synchronizedPoolAcquireMethod =
            Class.forName("android.util.Pools\$SynchronizedPool").method("acquire")

        internal val assetsSetter: ResourcesDelegate.(AssetManagerDelegate) -> Unit
            get() = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    {
                        AssetsSetter.V24
                    }.let(::add)
                }
                {
                    AssetsSetter.Old
                }.let(::add)
            }.let(::tryEach)
    }

    private sealed class AssetsSetter : (ResourcesDelegate, AssetManagerDelegate) -> Unit {

        abstract fun update(instance: Resources, value: Any)

        override fun invoke(instance: ResourcesDelegate, value: AssetManagerDelegate) {
            update(instance.original, value.original)
            instance.refresh()
        }

        @RequiresApi(Build.VERSION_CODES.N)
        object V24 : AssetsSetter() {

            private val implementationField =
                Resources::class.java
                    .field("mResourcesImpl")

            @SuppressLint("PrivateApi")
            private val assetsField =
                Class.forName("android.content.res.ResourcesImpl").field("mAssets")

            override fun update(instance: Resources, value: Any) {
                instance
                    .let(implementationField::get)!!
                    .let {
                        assetsField.set(it, value)
                    }
            }
        }

        object Old : AssetsSetter() {

            private val assetsField =
                Resources::class.java.field("mAssets")

            override fun update(instance: Resources, value: Any) {
                assetsField.set(instance, value)
            }
        }
    }

    private val classLoaderField by lazy {
        Resources::class.java.field("mClassLoader")
    }

    val classLoaderSelfSetter: (ClassLoader) -> Unit
        get() = SelfSetter(
            field = classLoaderField,
            instance = original,
        )

    val drawableInflater: DrawableInflaterDelegate?
        get() = drawableInflaterField.get(original)
            ?.let(::DrawableInflaterDelegate)

    private val typedArrayPool =
        typedArrayPoolField.get(original)!!

    private fun acquireTypedArrayPool(): Any? =
        typedArrayPool.let(synchronizedPoolAcquireMethod::invoke)

    private fun refresh() {
        // Resources has `mTypedArrayPool` field, which just like message poll to reduce GC.
        //
        // `MiuiResource` change `TypedArray` to `MiuiTypedArray`, but it gets string block from
        // offset instead of `AssetManager`.
        while (true) {
            acquireTypedArrayPool() ?: break
        }
        // Updates configuration.
        @Suppress("DEPRECATION")
        original.updateConfiguration(original.configuration, original.displayMetrics)
    }
}

/**
 * Delegate of hidden class `android.content.res.ResourcesKey` for accessing hidden members.
 */
internal class ResourceKeyDelegate(override val original: Any) : HiddenClass() {

    companion object {
        private val originalClass by lazy @SuppressLint("PrivateApi") {
            Class.forName("android.content.res.ResourcesKey")
        }

        private val resDirField by lazy {
            originalClass.field("mResDir")
        }

        val resourceDirectoryGetter: ResourceKeyDelegate.() -> String
            get() = GetterWithInstanceDelegate(
                field = resDirField,
            )

        val resourceDirectorySetter: ResourceKeyDelegate.(String) -> Unit
            get() = SetterWithInstanceDelegate(
                field = resDirField,
            )
    }
}

/**
 * Delegate of hidden class `android.content.res.ResourcesImpl` for accessing hidden members.
 */
internal class ResourceImplementationDelegate(override val original: Any) : HiddenClass() {

    companion object {

        private val originalClass by lazy @SuppressLint("PrivateApi") {
            Class.forName("android.content.res.ResourcesImpl")
        }

        private val assetsField by lazy {
            originalClass.field("mAssets")
        }

        internal val assetsSetter: ResourceImplementationDelegate.(AssetManagerDelegate) -> Unit
            get() = DelegateSetterWithInstanceDelegate(assetsField)
    }
}

/**
 * Delegate of [android.os.Handler] or custom implementation from manufacturers for accessing hidden
 * members.
 */
internal class HandlerDelegate(override val original: Any) : HiddenClass() {

    private val callbackField by lazy {
        original.javaClass.field("mCallback")
    }

    val callbackSelfGetter: () -> Handler.Callback
        get() = SelfGetter(
            field = callbackField,
            instance = original,
        )

    val callbackSelfSetter: (Handler.Callback) -> Unit
        get() = SelfSetter(
            field = callbackField,
            instance = original,
        )

    val launchActivityMessageId by lazy {
        original.javaClass.fieldOrNull("LAUNCH_ACTIVITY")?.getInt(null)
    }

    val relaunchActivityMessageId by lazy {
        original.javaClass.fieldOrNull("RELAUNCH_ACTIVITY")?.getInt(null)
    }

    val transactionMessageId by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            original.javaClass.fieldOrNull("EXECUTE_TRANSACTION")?.getInt(null)
        } else {
            null
        }
    }
}

/**
 * Delegate of hidden class `android.app.severtransaction.ClientTransaction` or custom
 * implementation from manufacturers for accessing hidden members.
 */
internal class ClientTransactionDelegate(override val original: Any) : HiddenClass() {

    companion object {
        val Any.delegatedAsClientTransaction: ClientTransactionDelegate
            get() = ClientTransactionDelegate(this)
    }

    private class CallbacksGetter(
        builder: ClientTransactionDelegate,
    ) : (ClientTransactionDelegate) -> List<Any>? {

        private val method = builder.original.javaClass.method("getCallbacks")

        override fun invoke(instance: ClientTransactionDelegate): List<Any>? {
            try {
                @Suppress("UNCHECKED_CAST")
                return method.invoke(instance.original)?.let { it as List<Any> }
            } catch (exception: ClassCastException) {
                throw Tinker.Error(
                    Tinker.Error.Load.CAST_FAILED,
                    "Return type of method \"${method.descriptor}\" is unexpected.",
                    exception,
                )
            }
        }
    }

    val callbacksGetter: (ClientTransactionDelegate) -> List<Any>?
        get() = CallbacksGetter(this)
}