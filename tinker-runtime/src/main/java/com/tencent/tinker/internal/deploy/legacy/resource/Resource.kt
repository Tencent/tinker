package com.tencent.tinker.internal.deploy.legacy.resource

import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.deploy.legacy.PackageMetadata
import com.tencent.tinker.internal.util.HashOutputStream
import com.tencent.tinker.internal.util.asMd5Hash
import com.tencent.tinker.internal.util.asMd5String
import com.tencent.tinker.internal.util.copyAndGenerateHash
import com.tencent.tinker.internal.util.crc32
import com.tencent.tinker.internal.util.ensureParentIsExistingDirectory
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.forked
import com.tencent.tinker.internal.util.forkedStored
import com.tencent.tinker.internal.util.withTemporaryFile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private enum class ErrorType : TinkerError.Type {
    UNEXPECTED,
    INVALID_METADATA,
    MISSING_MANIFEST,
    MISSING_BASE_ENTRY,
    INVALID_BASE_ENTRY,
    MISSING_DIFF_ENTRY,
    INVALID_DEPLOY_RESULT;

    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.DEPLOY_LEGACY_RESOURCE

    override val typeCode: Int
        get() = ordinal
}

private class ResourceMetadata(

    val baseArscCrc32: Long,


    val patterns: List<Regex>,
    val added: Map<String, Boolean>,
    val removed: Set<String>,
    val modified: Map<String, ByteArray>,
)

private val ByteArray.parsedResourceMetadata: ResourceMetadata
    get() = toString(Charsets.UTF_8)
        .split("\n")
        .filter { it.isNotBlank() }
        .let { inputs ->
            var baseArscCrc32 = null as Long?
            val patterns = mutableListOf<Regex>()
            val modified = mutableMapOf<String, ByteArray>()
            val added = mutableSetOf<String>()
            val removed = mutableSetOf<String>()
            val stored = mutableSetOf<String>()
            val lines = inputs.toMutableList()
            val pop: MutableList<String>.() -> String = {
                if (isEmpty()) {
                    throw TinkerError(
                        ErrorType.INVALID_METADATA,
                        "Missing line in resource metadata."
                    )
                }
                removeAt(0)
            }
            while (lines.isNotEmpty()) {
                val line = lines.pop()
                if (line.startsWith("resources_out.zip")) {
                    val parts = line.split(",")
                    try {
                        baseArscCrc32 = parts[1].trim().toLong()
                    } catch (throwable: Throwable) {
                        throw TinkerError(
                            ErrorType.INVALID_METADATA,
                            "Hash of resource arsc file in base apk file is invalid.",
                            throwable,
                        )
                    }
                } else if (line.startsWith("pattern:")) {
                    val count = try {
                        line.removePrefix("pattern:").toInt()
                    } catch (throwable: Throwable) {
                        throw TinkerError(
                            ErrorType.INVALID_METADATA,
                            "Pattern count is invalid.",
                            throwable,
                        )
                    }
                    repeat(count) {
                        lines.pop()
                            .replace("\\.", "\\\\.")
                            .replace("\\?", "\\\\?")
                            .replace("*", ".*")
                            .toRegex()
                            .let(patterns::add)
                    }
                } else if (line.startsWith("add:")) {
                    val count = try {
                        line.removePrefix("add:").toInt()
                    } catch (throwable: Throwable) {
                        throw TinkerError(
                            ErrorType.INVALID_METADATA,
                            "Added count is invalid.",
                            throwable,
                        )
                    }
                    repeat(count) {
                        lines.pop().let(added::add)
                    }
                } else if (line.startsWith("modify:")) {
                    val count = try {
                        line.removePrefix("modify:").toInt()
                    } catch (throwable: Throwable) {
                        throw TinkerError(
                            ErrorType.INVALID_METADATA,
                            "Modified count is invalid.",
                            throwable,
                        )
                    }
                    repeat(count) {
                        lines.pop().let(added::add)
                    }
                } else if (line.startsWith("delete:")) {
                    val count = try {
                        line.removePrefix("delete:").toInt()
                    } catch (throwable: Throwable) {
                        throw TinkerError(
                            ErrorType.INVALID_METADATA,
                            "Removed count is invalid.",
                            throwable,
                        )
                    }
                    repeat(count) {
                        lines.pop().let(removed::add)
                    }
                } else if (line.startsWith("store:")) {
                    val count = try {
                        line.removePrefix("store:").toInt()
                    } catch (throwable: Throwable) {
                        throw TinkerError(
                            ErrorType.INVALID_METADATA,
                            "Stored count is invalid.",
                            throwable,
                        )
                    }
                    repeat(count) {
                        lines.pop().let(stored::add)
                    }
                } else if (line.startsWith("large modify:")) {
                    val count = try {
                        line.removePrefix("large modify:").toInt()
                    } catch (throwable: Throwable) {
                        throw TinkerError(
                            ErrorType.INVALID_METADATA,
                            "Large-modified count is invalid.",
                            throwable,
                        )
                    }
                    repeat(count) { index ->
                        val parts = lines.pop().split(",")
                        if (parts.size < 3) {
                            throw TinkerError(
                                ErrorType.INVALID_METADATA,
                                "Subline ${index + 1} of large-modified only has ${parts.size} parts.",
                            )
                        }
                        val path = parts[0].trim()
                        val hash = try {
                            parts[1].trim().asMd5Hash
                        } catch (throwable: Throwable) {
                            throw TinkerError(
                                ErrorType.INVALID_METADATA,
                                "Hash of subline ${index + 1} of large-modified is invalid.",
                                throwable,
                            )
                        }
                        modified[path] = hash
                    }
                }
            }
            return@let ResourceMetadata(
                baseArscCrc32 = baseArscCrc32
                    ?: throw TinkerError(
                        ErrorType.INVALID_METADATA,
                        "CRC32 checksum of resource arsc file in base apk file is not found.",
                    ),
                patterns = patterns,
                added = added.associateWith { it in stored },
                removed = removed,
                modified = modified,
            )
        }

private const val RESOURCE_ARSC_ENTRY_NAME = "resources.arsc"
private const val MANIFEST_ENTRY_NAME = "AndroidManifest.xml"

private sealed class DeployStrategy {

    class NewlyAdded(val stored: Boolean) : DeployStrategy()

    object NotModified : DeployStrategy()

    class Merging(val patchedHash: ByteArray) : DeployStrategy()
}

private fun buildEntryStrategies(
    metadata: ResourceMetadata,
    baseApk: ZipFile,
    diffPackage: ZipFile
): List<Pair<String, DeployStrategy>> {
    val allEntryNames = buildSet {
        baseApk.entries()
            .asSequence()
            .map { it.name }
            .filter { name -> metadata.patterns.any { it.matches(name) } }
            .toList()
            .let(::addAll)
        diffPackage.entries()
            .asSequence()
            .map { it.name }
            .filter { name -> metadata.patterns.any { it.matches(name) } }
            .toList()
            .let(::addAll)
        remove(MANIFEST_ENTRY_NAME)
    }
    return allEntryNames.mapNotNull { entryName ->
        metadata.added[entryName]?.let { stored ->
            return@mapNotNull entryName to DeployStrategy.NewlyAdded(stored)
        }
        if (entryName in metadata.removed) {
            return@mapNotNull null
        }
        metadata.modified[entryName]?.let { patchedHash ->
            return@mapNotNull entryName to DeployStrategy.Merging(patchedHash)
        }
        if (baseApk.getEntry(entryName) == null) {
            // Entry only exists in diff package, and is not used as patched resource. For example, metadata files.
            return@mapNotNull null
        }
        return@mapNotNull entryName to DeployStrategy.NotModified
    }
}

/**
 * Creates newly added resource file from diff package. The content of newly added resource file is same as diff file.
 */
private fun ZipOutputStream.createResourceAsNewlyAdded(
    diffPackage: ZipFile,
    entryName: String,
    stored: Boolean
) {
    val diffEntry = diffPackage.getEntry(entryName) ?: throw TinkerError(
        ErrorType.MISSING_DIFF_ENTRY,
        "Cannot find entry \"${entryName}\" in diff package \"${diffPackage.name}\".",
    )
    if (stored) {
        putNextEntry(diffEntry.forkedStored)
    } else {
        putNextEntry(diffEntry.forked)
    }
    diffPackage.getInputStream(diffEntry).use { input ->
        input.copyTo(this)
    }
    closeEntry()
}

/**
 * Copies not-modified resource file from base apk file. The content of created resource file is same as original
 * resource file in base apk file.
 */
private fun ZipOutputStream.createResourceAsNotModified(
    baseApk: ZipFile,
    entryName: String,
) {
    val baseEntry = baseApk.getEntry(entryName) ?: throw TinkerError(
        ErrorType.MISSING_BASE_ENTRY,
        "Cannot find entry \"${entryName}\" in base apk file \"${baseApk.name}\".",
    )
    putNextEntry(baseEntry.forked)
    baseApk.getInputStream(baseEntry).use { input ->
        input.copyTo(this)
    }
    closeEntry()
}

/**
 * Creates resource file by merging base resource file and diff file.
 */
private fun ZipOutputStream.createResourceByMerging(
    packageMetadata: PackageMetadata,
    baseApk: ZipFile,
    diffPackage: ZipFile,
    entryName: String,
    expectedPatchedHash: ByteArray,
) {
    val baseEntry = baseApk.getEntry(entryName) ?: throw TinkerError(
        ErrorType.MISSING_BASE_ENTRY,
        "Cannot find entry \"${entryName}\" in base apk file \"${baseApk.name}\".",
    )
    val diffEntry = diffPackage.getEntry(entryName) ?: throw TinkerError(
        ErrorType.MISSING_DIFF_ENTRY,
        "Cannot find entry \"${entryName}\" in diff package \"${diffPackage.name}\".",
    )
    withTemporaryFile { file ->
        // Writes merged content into temporary file, for calculating size and checksum.
        file.outputStream().buffered().use { temporaryOutput ->
            baseApk.getInputStream(baseEntry).use { baseInput ->
                diffPackage.getInputStream(diffEntry).use { diffInput ->
                    packageMetadata.merger.merge(baseInput, diffInput, temporaryOutput)
                }
            }
        }
        // Copies merged content into output.
        ZipEntry(entryName)
            .apply {
                this.method = ZipEntry.STORED
                this.size = file.length()
                this.crc = file.crc32
            }
            .let(this::putNextEntry)
        val hash = file.inputStream().buffered().use { temporaryInput ->
            HashOutputStream(this)
                .also { wrapped ->
                    temporaryInput.copyAndGenerateHash(wrapped)
                }
                .digest
        }
        closeEntry()
        if (!hash.contentEquals(expectedPatchedHash)) {
            throw TinkerError(
                ErrorType.INVALID_DEPLOY_RESULT,
                "Hash \"${hash.asMd5String}\" of patch result \"${entryName}\" "
                        + "\"${diffPackage.name}\" is not match \"${expectedPatchedHash.asMd5String}\" in metadata.",
            )
        }
    }
}

private fun resourceDeployInternal(
    packageMetadata: PackageMetadata,
    baseApk: ZipFile,
    diffPackage: ZipFile,
    apk: File
) {
    val metadata = diffPackage.getEntry("assets/res_meta.txt")
        ?.let(diffPackage::getInputStream)
        ?.use {
            it.readBytes()
        }
        ?.parsedResourceMetadata
        ?: return
    val baseArscCrc32 = baseApk.getEntry(RESOURCE_ARSC_ENTRY_NAME)?.crc
    if (baseArscCrc32 != metadata.baseArscCrc32) {
        throw TinkerError(
            ErrorType.INVALID_BASE_ENTRY,
            "CRC32 checksum \"${baseArscCrc32}\" of entry \"${RESOURCE_ARSC_ENTRY_NAME}\" in base apk file "
                    + "\"${baseApk.name}\" is not match \"${metadata.baseArscCrc32}\" in metadata.",
        )
    }
    val entryStrategies = buildEntryStrategies(metadata, baseApk, diffPackage)
    apk.ensureParentIsExistingDirectory()
        .outputStream()
        .let(::ZipOutputStream)
        .use { output ->
            // Copies manifest to output apk file.
            val baseManifestEntry = baseApk.getEntry(MANIFEST_ENTRY_NAME)
                ?: throw TinkerError(
                    ErrorType.MISSING_MANIFEST,
                    "Cannot find entry manifest in base apk file \"${baseApk.name}\".",
                )
            output.putNextEntry(baseManifestEntry.forked)
            baseApk.getInputStream(baseManifestEntry).use { input ->
                input.copyTo(output)
            }
            output.closeEntry()
            // Creates entries by their strategies.
            entryStrategies.forEach { (entryName, strategy) ->
                when (strategy) {

                    is DeployStrategy.NewlyAdded -> output.createResourceAsNewlyAdded(
                        diffPackage = diffPackage,
                        entryName = entryName,
                        stored = strategy.stored
                    )

                    is DeployStrategy.NotModified -> output.createResourceAsNotModified(
                        baseApk = baseApk,
                        entryName = entryName,
                    )

                    is DeployStrategy.Merging -> output.createResourceByMerging(
                        packageMetadata = packageMetadata,
                        baseApk = baseApk,
                        diffPackage = diffPackage,
                        entryName = entryName,
                        expectedPatchedHash = strategy.patchedHash,
                    )
                }
            }
        }
}

internal fun resourceDeploy(
    packageMetadata: PackageMetadata,
    baseApk: ZipFile,
    diffPackage: ZipFile,
    apk: File
) {
    expected<ErrorType>("deploy resources") {
        resourceDeployInternal(
            packageMetadata,
            baseApk,
            diffPackage,
            apk
        )
    }
}