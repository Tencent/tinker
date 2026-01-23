package com.tencent.tinker.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.collections.asSequence
import kotlin.collections.forEach

abstract class PackageTransformTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectories: ListProperty<Directory>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJars: ListProperty<RegularFile>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    class DependenciesCollector(
        private val graph: MutableMap<String, Set<String>>,
        visitor: ClassVisitor? = null
    ) : ClassVisitor(Opcodes.ASM9, visitor) {

        private var clazz: String? = null

        private val dependencies = mutableSetOf<String>()

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String?>?
        ) {
            clazz = name
            superName?.let(dependencies::add)
            interfaces?.forEach { i -> i?.let(dependencies::add) }
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            dependencies.add(descriptor)  // TODO: Split classes from descriptor.
            return super.visitField(access, name, descriptor, signature, value)
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String?>?
        ): MethodVisitor {
            dependencies.add(descriptor)  // TODO: Split classes from descriptor.
            return MethodCollector(dependencies)
        }

        class MethodCollector(
            private val dependencies: MutableSet<String>,
        ) : MethodVisitor(Opcodes.ASM9) {
            override fun visitMethodInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
                isInterface: Boolean
            ) {
                dependencies.add(owner)
                dependencies.add(descriptor) // TODO: Split classes from descriptor.
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }

        override fun visitEnd() {
            clazz?.let { graph[it] = dependencies }
                ?: throw GradleException("Missing class name.")
            super.visitEnd()
        }
    }

    private fun InputStream.collect(graph: MutableMap<String, Set<String>>) {
        DependenciesCollector(graph).let {
            ClassReader(this).accept(it, 0)
        }
    }

    class Transformer(
        val graph: Map<String, Set<String>>,
        visitor: ClassVisitor? = null
    ) : ClassVisitor(Opcodes.ASM9, visitor) {

        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String?>?
        ) {

        }
    }

    private fun InputStream.transform(
        graph: Map<String, Set<String>>,
        outputStream: OutputStream,
    ) {
        val writer = ClassWriter(0)
//        Transformer(graph, writer).let {
//            ClassReader(this).accept(it, 0)
//        }
        ClassReader(this).accept(writer, 0)
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
        val graph = ConcurrentHashMap<String, Set<String>>()
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
        runBlocking {
            collectedFiles.forEach { (_, file) ->
                launch(Dispatchers.IO) {
                    file.inputStream().use {
                        it.collect(graph)
                    }
                }
            }
            collectedEntries.forEach { (jar, entry) ->
                launch(Dispatchers.IO) {
                    jar.getInputStream(entry).use {
                        it.collect(graph)
                    }
                }
            }
        }
        outputJar.get().asFile.outputStream().let(::JarOutputStream).use { output ->
            val wrote = mutableSetOf<String>()
            collectedFiles.forEach { (path, file) ->
                if (path in wrote) {
                    logger.warn("Found duplicate class file \"${path}\".")
                    return@forEach
                }
                file.inputStream().use { input ->
                    output.putNextEntry(JarEntry(path))
                    input.transform(graph, output)
                    output.closeEntry()
                }
                wrote.add(path)
            }
            collectedEntries.forEach { (jar, entry) ->
                if (entry.name in wrote) {
                    logger.warn("Found duplicate class file \"${entry.name}\".")
                    return@forEach
                }
                jar.getInputStream(entry).use { input ->
                    output.putNextEntry(JarEntry(entry.name))
                    input.transform(graph, output)
                    output.closeEntry()
                }
            }
        }
        File("/Users/aoramd/Downloads/output.txt").bufferedWriter().use { writer ->
            graph.forEach { (key, value) ->
                writer.appendLine(key)
                value.forEach {
                    writer.appendLine("  $it")
                }
            }
        }
    }
}