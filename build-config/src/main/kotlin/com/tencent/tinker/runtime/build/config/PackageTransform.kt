package com.tencent.tinker.runtime.build.config

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.tencent.tinker.build.config.tinkerBuildConfig
import com.tencent.tinker.capitalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Attribute
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.RecordComponentVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.TypePath
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/*
 * Build logic for transforming any classes used by Tinker runtime, but class package is not based on
 * `com.tencent.tinker.internal`, for example, Kotlin standard library classes, to sub-package of
 * `com.tencent.tinker.internal`.
 *
 * Classes being loaded in Tinker runtime loading procedure are called "loader classes". Because they are loaded by
 * original class loader, not patch class loader created by Tinker runtime, they are unable to be patched. Which causes:
 *
 * - If application uses classes what Tinker runtime are using, like Kotlin standard library classes, behavior of code
 *   using these classes may be unexpected for application designers.
 * - To avoid issues above but without transforming, Tinker runtime have to implement all code without any 3rd-party
 *   libraries or language features, which is not practical.
 *
 * In order to make a third-party library "transform-able", `transformImplementation()` should be used instead of
 * `implementation()` in dependencies blocks.
 */

/**
 * Add dependency like `implementation()`, but transform class packages and include them into target artifact, like a
 * Fat-AAR.
 */
@Suppress("unused")
fun Project.transformImplementation(dependency: Any) {
    dependencies.add("transformImplementation", dependency)
}

private const val PACKAGE_TRANSFORM_FLAVOR_DIMENSION = "tinkerType"

@Suppress("UnstableApiUsage")
internal fun Project.applyPackageTransform() {
    val transformImplementationConfiguration = configurations.dependencyScope("transformImplementation")
    val androidComponents = extensions.getByType(LibraryAndroidComponentsExtension::class.java)
    tinkerBuildConfig {
        publishVariant("productionRelease")
    }
    androidComponents.finalizeDsl { android ->
        android.apply {
            flavorDimensions.add(PACKAGE_TRANSFORM_FLAVOR_DIMENSION)
            productFlavors {
                create("production") { flavor ->
                    flavor.apply {
                        dimension = PACKAGE_TRANSFORM_FLAVOR_DIMENSION
                        isDefault = true
                    }
                }
                create("independent") { flavor ->
                    flavor.dimension = PACKAGE_TRANSFORM_FLAVOR_DIMENSION
                }
            }
        }
    }
    androidComponents.onVariants { variant ->
        val production =
            variant.productFlavors.any { it.first == PACKAGE_TRANSFORM_FLAVOR_DIMENSION && it.second == "production" }
        if (!production) {
            configurations.named("${variant.name}Implementation").configure {
                it.extendsFrom(transformImplementationConfiguration.get())
            }
            return@onVariants
        }
        val dependencies = configurations.resolvable("${variant.name}TransformClasspath") {
            it.extendsFrom(transformImplementationConfiguration.get())
        }
        configurations.named("${variant.name}CompileClasspath").configure {
            it.extendsFrom(transformImplementationConfiguration.get())
        }
        val packageTransformTask =
            project.tasks.register(
                "transform${variant.name.capitalized}Package",
                PackageTransformTask::class.java,
            ) {
                it.inputDependenciesJars.setFrom(dependencies)
            }
        variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
            .use(packageTransformTask)
            .toTransform(
                ScopedArtifact.CLASSES,
                PackageTransformTask::inputJars,
                PackageTransformTask::inputDirectories,
                PackageTransformTask::outputJar,
            )
    }
}

private fun String.nameTransform(targets: Set<String>): String {
    if (startsWith("[")) {
        return descriptorTransform(targets)
    }
    if (this !in targets) {
        return this
    }
    return "com/tencent/tinker/internal/$this"
}

private val tokenizeDelimiters = setOf(';', '<')

private val String.tokenized: List<Pair<String, Boolean>>
    get() {
        val result = mutableListOf<Pair<String, Boolean>>()
        var classStart = -1
        for (index in indices) {
            if (classStart != -1) {
                if (this[index] !in tokenizeDelimiters) {
                    continue
                }
                result.add(substring(classStart, index) to true)
                result.add(substring(index, index + 1) to false)
                classStart = -1
                continue
            }
            if (this[index] == 'L') {
                result.add("L" to false)
                classStart = index + 1
                continue
            }
            result.add(substring(index, index + 1) to false)
        }
        if (classStart != -1) {
            throw IllegalArgumentException("Invalid descriptor \"${this}\".")
        }
        return result
    }

private fun String.descriptorTransform(targets: Set<String>): String =
    tokenized.joinToString("") {
        if (it.second) {
            it.first.nameTransform(targets)
        } else {
            it.first
        }
    }

private fun String.reflectionTransform(targets: Set<String>): String =
    if (replace(".", "/") in targets) {
        "com.tencent.tinker.internal.${this}"
    } else {
        this
    }

private fun Any.constantTransform(targets: Set<String>): Any =
    when (this) {
        is Type -> transform(targets)
        is Handle -> transform(targets)
        is ConstantDynamic -> transform(targets)
        is String -> reflectionTransform(targets)
        else -> this
    }

private fun Handle.transform(targets: Set<String>): Handle =
    Handle(
        tag,
        owner.nameTransform(targets),
        name,
        desc.descriptorTransform(targets),
        isInterface,
    )

private fun ConstantDynamic.transform(targets: Set<String>): ConstantDynamic =
    ConstantDynamic(
        name,
        descriptor.descriptorTransform(targets),
        bootstrapMethod.transform(targets),
        (0 until bootstrapMethodArgumentCount).map { index ->
            getBootstrapMethodArgument(index).constantTransform(targets)
        },
    )

private fun Type.transform(targets: Set<String>): Type =
    Type.getType(descriptor.descriptorTransform(targets))

private fun String.pathTransform(targets: Set<String>): String =
    removeSuffix(".class").nameTransform(targets).let { "${it}.class" }

abstract class PackageTransformTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectories: ListProperty<Directory>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val inputDependenciesJars: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    class ClassCollectVisitor(
        private val collector: MutableSet<String>,
        visitor: ClassVisitor? = null
    ) : ClassVisitor(Opcodes.ASM9, visitor) {

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String>?
        ) {
            collector.add(name)
            super.visit(version, access, name, signature, superName, interfaces)
        }
    }

    private fun InputStream.collect(collector: MutableSet<String>) {
        ClassCollectVisitor(collector).let {
            ClassReader(this).accept(it, 0)
        }
    }

    class ClassTransformVisitor(
        val targets: Set<String>,
        visitor: ClassVisitor? = null
    ) : ClassVisitor(Opcodes.ASM9, visitor) {

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String>?
        ) {
            super.visit(
                version,
                access,
                name.nameTransform(targets),
                signature?.descriptorTransform(targets),
                superName?.nameTransform(targets),
                interfaces?.map { it.nameTransform(targets) }?.toTypedArray(),
            )
        }

        override fun visitPermittedSubclass(
            permittedSubclass: String,
        ) {
            super.visitPermittedSubclass(
                permittedSubclass.nameTransform(targets),
            )
        }

        override fun visitRecordComponent(
            name: String,
            descriptor: String,
            signature: String?,
        ): RecordComponentVisitor? {
            return super.visitRecordComponent(
                name.nameTransform(targets),
                descriptor.descriptorTransform(targets),
                signature?.descriptorTransform(targets),
            )
        }

        override fun visitNestHost(
            nestHost: String,
        ) {
            super.visitNestHost(
                nestHost.nameTransform(targets),
            )
        }

        override fun visitNestMember(
            nestMember: String,
        ) {
            super.visitNestMember(
                nestMember.nameTransform(targets),
            )
        }

        override fun visitInnerClass(
            name: String,
            outerName: String?,
            innerName: String?,
            access: Int,
        ) {
            super.visitInnerClass(
                name.nameTransform(targets),
                outerName?.nameTransform(targets),
                innerName,
                access,
            )
        }

        override fun visitOuterClass(
            owner: String,
            name: String?,
            descriptor: String?,
        ) {
            super.visitOuterClass(
                owner.nameTransform(targets),
                name,
                descriptor?.descriptorTransform(targets),
            )
        }

        override fun visitAnnotation(
            descriptor: String,
            visible: Boolean,
        ): AnnotationVisitor =
            AnnotationTransformer(
                targets,
                super.visitAnnotation(
                    descriptor.descriptorTransform(targets),
                    visible,
                ),
            )

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: TypePath?,
            descriptor: String?,
            visible: Boolean,
        ): AnnotationVisitor =
            AnnotationTransformer(
                targets,
                super.visitTypeAnnotation(typeRef, typePath, descriptor, visible)
            )

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor =
            FieldTransformer(
                targets,
                super.visitField(
                    access,
                    name,
                    descriptor.descriptorTransform(targets),
                    signature?.descriptorTransform(targets),
                    value,
                )
            )

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<String>?
        ): MethodVisitor =
            MethodTransformer(
                targets,
                super.visitMethod(
                    access,
                    name,
                    descriptor.descriptorTransform(targets),
                    signature?.descriptorTransform(targets),
                    exceptions?.map { it.nameTransform(targets) }?.toTypedArray(),
                )
            )

        private class FieldTransformer(
            private val targets: Set<String>,
            visitor: FieldVisitor? = null
        ) : FieldVisitor(Opcodes.ASM9, visitor) {

            override fun visitAnnotation(
                descriptor: String,
                visible: Boolean
            ): AnnotationVisitor =
                AnnotationTransformer(
                    targets,
                    super.visitAnnotation(
                        descriptor.descriptorTransform(targets),
                        visible,
                    )
                )

            override fun visitTypeAnnotation(
                typeRef: Int,
                typePath: TypePath?,
                descriptor: String,
                visible: Boolean,
            ): AnnotationVisitor =
                AnnotationTransformer(
                    targets,
                    super.visitTypeAnnotation(
                        typeRef,
                        typePath,
                        descriptor.descriptorTransform(targets),
                        visible,
                    )
                )

            override fun visitAttribute(attribute: Attribute?) {
                super.visitAttribute(attribute)
            }
        }

        private class MethodTransformer(
            private val targets: Set<String>,
            visitor: MethodVisitor? = null
        ) : MethodVisitor(Opcodes.ASM9, visitor) {

            override fun visitAnnotationDefault(): AnnotationVisitor =
                AnnotationTransformer(
                    targets,
                    super.visitAnnotationDefault(),
                )

            override fun visitAnnotation(
                descriptor: String,
                visible: Boolean,
            ): AnnotationVisitor =
                AnnotationTransformer(
                    targets,
                    super.visitAnnotation(
                        descriptor.descriptorTransform(targets),
                        visible,
                    ),
                )

            override fun visitTypeAnnotation(
                typeRef: Int,
                typePath: TypePath?,
                descriptor: String,
                visible: Boolean
            ): AnnotationVisitor =
                AnnotationTransformer(
                    targets,
                    super.visitTypeAnnotation(
                        typeRef,
                        typePath,
                        descriptor.descriptorTransform(targets),
                        visible,
                    ),
                )

            override fun visitParameterAnnotation(
                parameter: Int,
                descriptor: String,
                visible: Boolean
            ): AnnotationVisitor =
                AnnotationTransformer(
                    targets,
                    super.visitParameterAnnotation(
                        parameter,
                        descriptor.descriptorTransform(targets),
                        visible,
                    ),
                )

            override fun visitMultiANewArrayInsn(
                descriptor: String,
                numDimensions: Int,
            ) {
                super.visitMultiANewArrayInsn(
                    descriptor.descriptorTransform(targets),
                    numDimensions,
                )
            }

            override fun visitLdcInsn(value: Any) {
                super.visitLdcInsn(
                    value.constantTransform(targets)
                )
            }

            override fun visitInsnAnnotation(
                typeRef: Int,
                typePath: TypePath?,
                descriptor: String,
                visible: Boolean
            ): AnnotationVisitor =
                AnnotationTransformer(
                    targets,
                    super.visitInsnAnnotation(
                        typeRef,
                        typePath,
                        descriptor.descriptorTransform(targets),
                        visible,
                    ),
                )

            override fun visitTryCatchAnnotation(
                typeRef: Int,
                typePath: TypePath?,
                descriptor: String,
                visible: Boolean
            ): AnnotationVisitor =
                AnnotationTransformer(
                    targets,
                    super.visitTryCatchAnnotation(
                        typeRef,
                        typePath,
                        descriptor.descriptorTransform(targets),
                        visible,
                    ),
                )

            override fun visitLocalVariableAnnotation(
                typeRef: Int,
                typePath: TypePath?,
                start: Array<Label>,
                end: Array<Label>,
                index: IntArray,
                descriptor: String,
                visible: Boolean
            ): AnnotationVisitor =
                AnnotationTransformer(
                    targets,
                    super.visitLocalVariableAnnotation(
                        typeRef,
                        typePath,
                        start,
                        end,
                        index,
                        descriptor.descriptorTransform(targets),
                        visible,
                    ),
                )

            override fun visitFieldInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
            ) {
                super.visitFieldInsn(
                    opcode,
                    owner.nameTransform(targets),
                    name,
                    descriptor.descriptorTransform(targets),
                )
            }

            override fun visitMethodInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
                isInterface: Boolean
            ) {
                super.visitMethodInsn(
                    opcode,
                    owner.nameTransform(targets),
                    name,
                    descriptor.descriptorTransform(targets),
                    isInterface,
                )
            }

            override fun visitInvokeDynamicInsn(
                name: String,
                descriptor: String,
                bootstrapMethodHandle: Handle,
                vararg bootstrapMethodArguments: Any?
            ) {
                super.visitInvokeDynamicInsn(
                    name,
                    descriptor.descriptorTransform(targets),
                    bootstrapMethodHandle.transform(targets),
                    *bootstrapMethodArguments
                        .map {
                            when (it) {
                                is Type -> it.transform(targets)
                                is Handle -> it.transform(targets)
                                else -> it
                            }
                        }
                        .toTypedArray(),
                )
            }

            override fun visitLocalVariable(
                name: String,
                descriptor: String,
                signature: String?,
                start: Label,
                end: Label,
                index: Int
            ) {
                super.visitLocalVariable(
                    name,
                    descriptor.descriptorTransform(targets),
                    signature?.descriptorTransform(targets),
                    start,
                    end,
                    index,
                )
            }

            override fun visitTryCatchBlock(
                start: Label,
                end: Label,
                handler: Label,
                type: String?,
            ) {
                super.visitTryCatchBlock(
                    start,
                    end,
                    handler,
                    type?.nameTransform(targets),
                )
            }

            override fun visitTypeInsn(
                opcode: Int,
                type: String,
            ) {
                super.visitTypeInsn(
                    opcode,
                    type.nameTransform(targets),
                )
            }
        }

        private class AnnotationTransformer(
            private val targets: Set<String>,
            visitor: AnnotationVisitor? = null
        ) : AnnotationVisitor(Opcodes.ASM9, visitor) {

            override fun visitEnum(
                name: String?,
                descriptor: String,
                value: String,
            ) {
                super.visitEnum(
                    name,
                    descriptor.descriptorTransform(targets),
                    value,
                )
            }

            override fun visitAnnotation(
                name: String?,
                descriptor: String,
            ): AnnotationVisitor =
                AnnotationTransformer(
                    targets,
                    super.visitAnnotation(
                        name,
                        descriptor.descriptorTransform(targets),
                    )
                )
        }
    }

    private fun InputStream.transform(
        targets: Set<String>,
        outputStream: OutputStream,
    ) {
        val writer = ClassWriter(0)
        ClassTransformVisitor(targets, writer).let {
            ClassReader(this).accept(it, 0)
        }
        writer.toByteArray().let(outputStream::write)
    }

    private val File.isClassFileExisting: Boolean
        get() = isFile && extension == "class"

    private val File.isJarFileExisting: Boolean
        get() = isFile && extension == "jar"

    private val JarEntry.isTransformable: Boolean
        get() = name.endsWith(".class")
                && !name.endsWith("module-info.class")
                && !name.startsWith("META-INF")

    @TaskAction
    fun exec() {
        val collector = Collections.synchronizedSet(mutableSetOf<String>())
        val collectedFiles = inputDirectories.get()
            .asSequence()
            .map { it.asFile }
            .flatMap { dir ->
                dir.walk()
                    .filter { it.isClassFileExisting }
                    .map { it.toRelativeString(dir).replace(File.separator, "/") to it }
            }
            .toList()
        val collectedEntries = inputJars.get()
            .asSequence()
            .map { it.asFile }
            .filter { it.isJarFileExisting }
            .flatMap { file ->
                val jar = JarFile(file)
                jar.entries()
                    .iterator()
                    .asSequence()
                    .filter { it.isTransformable }
                    .map { jar to it }
            }
            .toList()
        val collectedDependenciesEntries = inputDependenciesJars
            .asSequence()
            .flatMap { file ->
                if (!file.isJarFileExisting) {
                    throw GradleException("Dependency ${file.absolutePath} is not a jar file.")
                }
                val jar = JarFile(file)
                jar.entries()
                    .iterator()
                    .asSequence()
                    .filter { it.isTransformable }
                    .map { jar to it }
            }
            .toList()
        runBlocking {
            collectedFiles.forEach { (_, file) ->
                launch(Dispatchers.IO) {
                    file.inputStream().use {
                        it.collect(collector)
                    }
                }
            }
            collectedEntries.forEach { (jar, entry) ->
                launch(Dispatchers.IO) {
                    jar.getInputStream(entry).use {
                        it.collect(collector)
                    }
                }
            }
            collectedDependenciesEntries.forEach { (jar, entry) ->
                launch(Dispatchers.IO) {
                    jar.getInputStream(entry).use {
                        it.collect(collector)
                    }
                }
            }
        }
        // Workaround, transform reference "kotlin.reflect.jvm.internal.ReflectionFactoryImpl" even though it is not in
        // our classes.
        collector.add("kotlin/reflect/jvm/internal/ReflectionFactoryImpl")
        val targets = collector
            .filter {
                if (it.startsWith("com/tencent/tinker/internal/")) {
                    return@filter false
                }
                if (it == "com/tencent/tinker/Tinker") {
                    return@filter false
                }
                if (it.startsWith("com/tencent/tinker/Tinker$")) {
                    return@filter false
                }
                return@filter true
            }
            .toSet()
        outputJar.get().asFile.outputStream().let(::JarOutputStream).use { output ->
            val wrote = mutableSetOf<String>()
            collectedFiles.forEach { (path, file) ->
                val transformed = path.pathTransform(targets)
                if (transformed in wrote) {
                    logger.warn("Found duplicate class file \"${transformed}\".")
                    return@forEach
                }
                file.inputStream().use { input ->
                    output.putNextEntry(JarEntry(transformed))
                    input.transform(targets, output)
                    output.closeEntry()
                }
                wrote.add(path)
            }
            collectedEntries.forEach { (jar, entry) ->
                val transformed = entry.name.pathTransform(targets)
                if (transformed in wrote) {
                    logger.warn("Found duplicate class file \"${transformed}\".")
                    return@forEach
                }
                jar.getInputStream(entry).use { input ->
                    output.putNextEntry(JarEntry(transformed))
                    input.transform(targets, output)
                    output.closeEntry()
                }
                wrote.add(entry.name)
            }
            collectedDependenciesEntries.forEach { (jar, entry) ->
                val transformed = entry.name.pathTransform(targets)
                if (transformed in wrote) {
                    logger.warn("Found duplicate class file \"${transformed}\".")
                    return@forEach
                }
                jar.getInputStream(entry).use { input ->
                    output.putNextEntry(JarEntry(transformed))
                    input.transform(targets, output)
                    output.closeEntry()
                }
                wrote.add(entry.name)
            }
        }
    }
}