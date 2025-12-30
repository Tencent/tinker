package com.tencent.tinker.internal.module.hidden

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.util.expected
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
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

private sealed class ReflectGetDelegate<T> : (Any) -> T {

    class InstanceMethod<T>(
        private val method: Method,
    ) : ReflectGetDelegate<T>() {
        override fun invoke(instance: Any): T {
            try {
                @Suppress("UNCHECKED_CAST")
                return method.invoke(instance) as T
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Return type of method \"${method.descriptor}\" is unexpected.",
                    exception,
                )
            }
        }
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

/**
 * Base class of delegate classes for hidden framework classes.
 */
internal abstract class HiddenClass {
    abstract val original: Any
}

/**
 * Delegate for accessing hidden fields.
 */
private sealed class HiddenField<T> {

    /**
     * Use lazy to avoid unnecessary reflection.
     */
    abstract val lazyField: () -> Field

    private val field by lazy {
        lazyField()
    }

    abstract fun actualOf(self: T): Any

    inline operator fun <reified R> getValue(
        instance: T,
        property: KProperty<*>
    ): R {
        expected<ErrorType>("get field value") {
            try {
                return instance.let(::actualOf)
                    .let(field::get) as R
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Type of field \"${field.descriptor}\" is not \"${R::class.java.name}\".",
                    exception,
                )
            }
        }
    }

    inline operator fun <reified R> setValue(
        instance: T,
        property: KProperty<*>,
        value: R
    ) {
        expected<ErrorType>("set field value") {
            try {
                field.set(instance.let(::actualOf), value)
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Type of field \"${field.descriptor}\" is not \"${R::class.java.name}\".",
                    exception,
                )
            }
        }
    }

    /**
     * Use if `this` instance is the actual instance.
     */
    class Self(
        override val lazyField: () -> Field,
    ) : HiddenField<Any>() {
        override fun actualOf(self: Any): Any = self
    }

    /**
     * Use if `this` instance is a delegate instance based on [HiddenClass].
     */
    class Delegate(
        override val lazyField: () -> Field,
    ) : HiddenField<HiddenClass>() {
        override fun actualOf(self: HiddenClass): Any = self.original
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

private val contextWrapperBaseField by lazy {
    ContextWrapper::class.java.field("mBase")
}

internal val Application.base: Context by HiddenField.Self {
    contextWrapperBaseField
}

internal val Context.classLoaderSetter: ReflectSetter
    get() = ReflectSetter(
        field = javaClass.field("mClassLoader"),
        instance = this,
    )

/**
 * Delegate of hidden class `android.app.LoadedApk`.
 */
internal class LoadedApkDelegate(override val original: Any) : HiddenClass() {

    companion object {
        private val originalClass by lazy {
            Class.forName("android.app.LoadedApk")
        }

        private val resDirField by lazy {
            originalClass.field("mResDir")
        }
    }

    val classLoaderSetter: ReflectSetter
        get() = ReflectSetter(
            field = javaClass.field("mClassLoader"),
            instance = original,
        )

    var resDir: String? by HiddenField.Delegate {
        resDirField
    }
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
internal class DexPathListDelegate(override val original: Any) : HiddenClass() {

    private val dexElementsField by lazy {
        original.javaClass.field("dexElements")
    }

    @get:RequiresApi(Build.VERSION_CODES.M)
    private val nativeLibraryPathElementsField by lazy {
        original.javaClass.field("nativeLibraryPathElements")
    }

    internal var dexElements: Array<Any> by HiddenField.Delegate {
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
    val nativeLibraryDirectoriesV23: JavaMutableList<File>? by HiddenField.Delegate {
        nativeLibraryDirectoriesField
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun lazySetNativeLibraryDirectoriesV23(value: ArrayList<File>) =
        ReflectLazySetter(
            field = nativeLibraryDirectoriesField,
            instance = original,
            value = value
        )

    val nativeLibraryDirectoriesOld: Array<File> by HiddenField.Delegate {
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
    var systemNativeLibraryDirectories: List<File> by HiddenField.Delegate {
        systemNativeLibraryDirectoriesField
    }


    @get:RequiresApi(Build.VERSION_CODES.N)
    private val definingContextField by lazy {
        original.javaClass.field("definingContext")
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val definingContext: Any by HiddenField.Delegate {
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
                firstThrowable = firstThrowable ?: throwable
            }
        }
        try {
            return makeDexElementsOld(
                files,
                optimizedDirectory,
                suppressedExceptions,
            )
        } catch (throwable: Throwable) {
            firstThrowable = firstThrowable ?: throwable
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
                firstThrowable = firstThrowable ?: throwable
            }
        }
        try {
            return makeLibraryElementsV23(files, suppressedExceptions)
        } catch (throwable: Throwable) {
            firstThrowable = firstThrowable ?: throwable
        }
        // FIXME: Remove unnecessary suppression until Kotlin version is upgraded.
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        throw firstThrowable!!
    }
}

internal val ClassLoader.pathList: DexPathListDelegate
    get() = javaClass.field("pathList").get(this)!!.let(::DexPathListDelegate)

/**
 * Delegate of hidden class `android.app.ActivityThread`.
 */
internal class ActivityThreadDelegate(override val original: Any) : HiddenClass() {

    companion object {
        private val originalClass by lazy {
            Class.forName("android.app.ActivityThread")
        }

        private val currentActivityThreadMethod by lazy {
            originalClass.method("currentActivityThread")
        }

        val currentActivityThread: ActivityThreadDelegate
            get() = currentActivityThreadMethod.invoke(null)!!.let(::ActivityThreadDelegate)

        private val packagesField by lazy {
            originalClass.field("mPackages")
        }

        private val resourcePackagesField by lazy {
            originalClass.fieldOrNull("mResourcePackages")
        }

        private val handlerField by lazy {
            originalClass.field("mH")
        }
    }

    val referencedPackages: List<LoadedApkDelegate>
        get() = buildList {
            packagesField.get(original)
                .let {
                    @Suppress("UNCHECKED_CAST")
                    it as Map<String, WeakReference<Any?>>
                }
                .values
                .mapNotNull { it.get() }
                .map(::LoadedApkDelegate)
                .let(::addAll)
            resourcePackagesField?.get(original)
                ?.let {
                    @Suppress("UNCHECKED_CAST")
                    it as Map<String, WeakReference<Any?>>
                }
                ?.values
                ?.mapNotNull { it.get() }
                ?.map(::LoadedApkDelegate)
                ?.let(::addAll)
        }

    val handler: HandlerDelegate
        get() = handlerField.get(original)!!.let(::HandlerDelegate)
}

/**
 * Delegate of [android.content.res.AssetManager] or custom asset manager from manufacturers for
 * accessing hidden members
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
        stringBlocksField.set(this, null)
        ensureStringBlocksMethod.invoke(this)
    }
}

/**
 * Delegate of hidden class `android.app.ResourcesManager`.
 */
internal object ResourceManagerDelegate : HiddenClass() {

    private val originalClass by lazy {
        Class.forName("android.app.ResourcesManager")
    }

    private val getInstanceMethod by lazy {
        originalClass.method("getInstance")
    }

    override val original: Any by lazy {
        getInstanceMethod.invoke(null)!!
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val resourceReferencesField by lazy {
        originalClass.field("mResourceReferences")
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val referencedResourcesV24: List<ResourcesDelegate>
        get() = resourceReferencesField
            .let { field ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    field.get(original) as List<WeakReference<Resources>>
                } catch (exception: ClassCastException) {
                    throw TinkerError(
                        ErrorType.CAST_FAILED,
                        "Type of field \"${field.descriptor}\" is not \"List<WeakReference<Resources>>\".",
                        exception,
                    )
                }
            }
            .mapNotNull { it.get() }
            .map(::ResourcesDelegate)

    private val activeResourcesField by lazy {
        originalClass.field("mActiveResources")
    }

    private val referencedResourcesOld: List<ResourcesDelegate>
        get() = activeResourcesField
            .let { field ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    field.get(original) as Map<Any, WeakReference<Resources>>
                } catch (exception: ClassCastException) {
                    throw TinkerError(
                        ErrorType.CAST_FAILED,
                        "Type of field \"${field.descriptor}\" is not \"Map<Any, WeakReference<Resources>>\".",
                        exception,
                    )
                }
            }
            .values
            .mapNotNull { it.get() }
            .map(::ResourcesDelegate)

    val referencedResources: List<ResourcesDelegate>
        get() {
            var firstThrowable = null as Throwable?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    return referencedResourcesV24
                } catch (throwable: Throwable) {
                    firstThrowable = throwable
                }
            }
            try {
                return referencedResourcesOld
            } catch (throwable: Throwable) {
                firstThrowable = firstThrowable ?: throwable
            }
            // FIXME: Remove unnecessary suppression until Kotlin version is upgraded.
            @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
            throw firstThrowable!!
        }

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val resourceImplementationsField by lazy {
        originalClass.field("mResourceImpls")
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    val resourceImplementations: List<Pair<ResourcesKeyDelegate, ResourcesImplDelegate?>>
        get() = resourceImplementationsField
            .let { field ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    field.get(original) as Map<Any, WeakReference<Any>>
                } catch (exception: ClassCastException) {
                    throw TinkerError(
                        ErrorType.CAST_FAILED,
                        "Type of field \"${field.descriptor}\" is not \"Map<Any, WeakReference<Any>>\".",
                        exception,
                    )
                }
            }
            .mapNotNull {
                Pair(
                    it.key.let(::ResourcesKeyDelegate),
                    it.value.get()?.let(::ResourcesImplDelegate),
                )
            }

}

/**
 * Delegate of [android.content.res.Resources] for accessing hidden members.
 */
internal class ResourcesDelegate(override val original: Resources) : HiddenClass() {

    companion object {
        private val assetsField by lazy {
            Resources::class.java.field("mAssets")
        }

        @get:RequiresApi(Build.VERSION_CODES.N)
        private val implementationField by lazy {
            Resources::class.java.field("mResourcesImpl")
        }

        private val typedArrayPoolField by lazy {
            Resources::class.java.field("mTypedArrayPool")
        }
    }

    @get:RequiresApi(Build.VERSION_CODES.N)
    private val implementation by lazy {
        implementationField.get(original)!!.let(::ResourcesImplDelegate)
    }

    var assets: AssetManagerDelegate
        get() = original.assets.let(::AssetManagerDelegate)
        set(value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                implementation.assets = value
            } else {
                assetsField.set(original, value)
            }
        }

    private val typedArrayPool: SynchronizedPoolDelegate<Any> by lazy {
        typedArrayPoolField.get(original)!!.let(::SynchronizedPoolDelegate)
    }

    /**
     * Resources has `mTypedArrayPool` field, which just like message poll to reduce GC.
     *
     * `MiuiResource` change `TypedArray` to `MiuiTypedArray`, but it gets string block from offset
     * instead of `AssetManager`.
     */
    fun clearPreloadTypedArrayIssue() {
        while (true) {
            typedArrayPool.acquire() ?: break
        }
    }

    fun refreshConfiguration() {
        original.updateConfiguration(original.configuration, original.displayMetrics)
    }
}

/**
 * Delegate of hidden class `android.content.res.ResourcesKey`.
 */
internal class ResourcesKeyDelegate(override val original: Any) : HiddenClass() {
    companion object {
        private val originalClass by lazy {
            Class.forName("android.content.res.ResourcesKey")
        }

        private val resDirField by lazy {
            originalClass.field("mResDir")
        }
    }

    var resDir: String by HiddenField.Delegate {
        resDirField
    }
}

/**
 * Delegate of hidden class `android.content.res.ResourcesImpl`.
 */
@RequiresApi(Build.VERSION_CODES.N)
internal class ResourcesImplDelegate(override val original: Any) : HiddenClass() {

    companion object {
        private val originalClass by lazy {
            Class.forName("android.content.res.ResourcesImpl")
        }

        private val assetsField by lazy {
            originalClass.field("mAssets")
        }
    }

    var assets: Any by HiddenField.Delegate {
        assetsField
    }
}

/**
 * Delegate of hidden class `android.util.Pools$SynchronizedPool`.
 */
private class SynchronizedPoolDelegate<T>(override val original: Any) : HiddenClass() {

    companion object {
        private val originalClass by lazy {
            Class.forName("android.util.Pools\$SynchronizedPool")
        }

        private val acquireMethod by lazy {
            originalClass.method("acquire")
        }
    }

    fun acquire(): T? =
        acquireMethod.let { method ->
            try {
                @Suppress("UNCHECKED_CAST")
                return method.invoke(original)?.let { it as T }
            } catch (exception: ClassCastException) {
                throw TinkerError(
                    ErrorType.CAST_FAILED,
                    "Return type of \"${method.descriptor}\" is unexpected.",
                    exception,
                )
            }
        }
}

/**
 * Delegate of [android.os.Handler] or custom handler from manufacturers for accessing hidden
 * members.
 */
internal class HandlerDelegate(override val original: Any) : HiddenClass() {

    private val originalClass by lazy {
        original.javaClass
    }

    private val callbackField by lazy {
        originalClass.field("mCallback")
    }

    var callback: Handler.Callback by HiddenField.Delegate {
        callbackField
    }

    val launchActivityMessageId by lazy {
        originalClass.fieldOrNull("LAUNCH_ACTIVITY")?.getInt(null)
    }

    val relaunchActivityMessageId by lazy {
        originalClass.fieldOrNull("RELAUNCH_ACTIVITY")?.getInt(null)
    }

    val transactionMessageId by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            originalClass.fieldOrNull("EXECUTE_TRANSACTION")?.getInt(null)
        } else {
            null
        }
    }
}

internal val Any.transactionGetCallbacks: Any.() -> List<Any>?
    get() = ReflectGetDelegate.InstanceMethod(
        method = javaClass.method("getCallbacks")
    )