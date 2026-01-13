package com.tencent.tinker.internal.deploy.legacy.library

import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.deploy.legacy.PackageMetadata
import com.tencent.tinker.internal.util.HashOutputStream
import com.tencent.tinker.internal.util.asMd5Hash
import com.tencent.tinker.internal.util.asMd5String
import com.tencent.tinker.internal.util.async
import com.tencent.tinker.internal.util.copyAndGenerateHash
import com.tencent.tinker.internal.util.crc32
import com.tencent.tinker.internal.util.ensureParentIsExistingDirectory
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.hashOf
import java.io.File
import java.util.zip.ZipFile

private enum class ErrorType : TinkerError.Type {
    UNEXPECTED,
    INVALID_METADATA,
    MISSING_BASE_ENTRY,
    INVALID_BASE_ENTRY,
    MISSING_DIFF_ENTRY,
    INVALID_DIFF_ENTRY,
    INVALID_DEPLOY_RESULT;

    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.DEPLOY_LEGACY_LIBRARY

    override val typeCode: Int
        get() = ordinal
}

private class LibraryMetadata(
    /**
     * Name of library file.
     */
    val name: String,

    /**
     * ABI of library file.
     */
    val abi: String,

    /**
     * MD5 hash of patched file.
     */
    val patchedHash: ByteArray,

    /**
     * CRC32 checksum of base file.
     */
    val baseCrc32: Long,

    /**
     * MD5 hash of diff file.
     */
    val diffHash: ByteArray,
) {
    val entryName by lazy {
        "lib/${abi}/${name}"
    }
}

private val ByteArray.parsedLibraryMetadataList: List<LibraryMetadata>
    get() = toString(Charsets.UTF_8)
        .split("\n")
        .filter { it.isNotBlank() }
        .mapIndexed { lineNumberMinusOne, line ->
            val parts = line.split(",")
            if (parts.size < 5) {
                throw TinkerError(
                    ErrorType.INVALID_METADATA,
                    "Line ${lineNumberMinusOne + 1} only has ${parts.size} parts.",
                )
            }
            val name = parts[0].trim()
            val abi = parts[1].trim()
                .split("/")
                .let { pathParts ->
                    if (pathParts.size != 2) {
                        throw TinkerError(
                            ErrorType.INVALID_METADATA,
                            "Path of line ${lineNumberMinusOne + 1} is has ${pathParts.size} parts.",
                        )
                    }
                    if (pathParts[0] != "lib") {
                        throw TinkerError(
                            ErrorType.INVALID_METADATA,
                            "Path of line ${lineNumberMinusOne + 1} is not starts with \"lib/\"",
                        )
                    }
                    pathParts[1]
                }
            val patchedHash = try {
                parts[2].trim().asMd5Hash
            } catch (throwable: Throwable) {
                throw TinkerError(
                    ErrorType.INVALID_METADATA,
                    "Patched hash of line ${lineNumberMinusOne + 1} is invalid.",
                    throwable,
                )
            }
            val baseCrc32 = try {
                parts[3].trim().toLong()
            } catch (throwable: Throwable) {
                throw TinkerError(
                    ErrorType.INVALID_METADATA,
                    "Base file CRC32 of line ${lineNumberMinusOne + 1} is invalid.",
                    throwable,
                )
            }
            val diffHash = try {
                parts[4].trim().asMd5Hash
            } catch (throwable: Throwable) {
                throw TinkerError(
                    ErrorType.INVALID_METADATA,
                    "Diff hash of line ${lineNumberMinusOne + 1} is invalid.",
                    throwable,
                )
            }
            return@mapIndexed LibraryMetadata(
                name = name,
                abi = abi,
                patchedHash = patchedHash,
                baseCrc32 = baseCrc32,
                diffHash = diffHash,
            )
        }

/**
 * Creates newly added library file from diff package. The content of newly added library file is same as diff file.
 *
 * Returns the MD5 hash of created library file.
 */
private fun File.createLibraryAsNewlyAdded(
    metadata: LibraryMetadata,
    diffPackage: ZipFile,
): ByteArray {
    val entry = diffPackage.getEntry(metadata.entryName)
        ?: throw TinkerError(
            ErrorType.MISSING_DIFF_ENTRY,
            "Cannot find entry \"${metadata.entryName}\" in diff package \"${diffPackage.name}\"."
        )
    return diffPackage.getInputStream(entry)
        .use { stream ->
            resolve(metadata.abi).resolve(metadata.name)
                .ensureParentIsExistingDirectory()
                .outputStream()
                .buffered()
                .use(stream::copyAndGenerateHash)
        }
}

/**
 * Creates library file by merging base library file and diff file.
 *
 * Returns the MD5 hash of created library file.
 */
private fun File.createLibraryByMerging(
    packageMetadata: PackageMetadata,
    metadata: LibraryMetadata,
    baseApk: ZipFile,
    diffPackage: ZipFile,
): ByteArray {
    val baseEntry = baseApk.getEntry(metadata.entryName)
        ?: throw TinkerError(
            ErrorType.MISSING_BASE_ENTRY,
            "Cannot find entry \"${metadata.entryName}\" in base apk file \"${baseApk.name}\"."
        )
    if (baseEntry.crc != metadata.baseCrc32) {
        throw TinkerError(
            ErrorType.INVALID_BASE_ENTRY,
            "CRC32 checksum \"${baseEntry.crc}\" of entry \"${metadata.entryName}\" in base apk file "
                    + "\"${baseApk.name}\" is not match \"${metadata.baseCrc32}\" in metadata.",
        )
    }
    val diffEntry = diffPackage.getEntry(metadata.entryName)
        ?: throw TinkerError(
            ErrorType.MISSING_DIFF_ENTRY,
            "Cannot find entry \"${metadata.entryName}\" in diff package \"${diffPackage.name}\"."
        )
    val diffEntryHash = diffPackage.hashOf(diffEntry)
    if (!diffEntryHash.contentEquals(metadata.diffHash)) {
        throw TinkerError(
            ErrorType.INVALID_DIFF_ENTRY,
            "Hash \"${diffEntryHash.asMd5String}\" of entry \"${diffEntry.name}\" in diff package "
                    + "\"${diffPackage.name}\" is not match \"${metadata.diffHash.asMd5String}\" in metadata.",
        )
    }
    return resolve(metadata.abi).resolve(metadata.name)
        .ensureParentIsExistingDirectory()
        .outputStream()
        .buffered()
        .use { output ->
            HashOutputStream(output)
                .also { wrapped ->
                    baseApk.getInputStream(baseEntry).use { baseInput ->
                        diffPackage.getInputStream(diffEntry).use { diffInput ->
                            packageMetadata.merger.merge(baseInput, diffInput, wrapped)
                        }
                    }
                }
                .digest
        }
}

private fun libraryDeployInternal(
    packageMetadata: PackageMetadata,
    baseApk: ZipFile,
    diffPackage: ZipFile,
    directory: File
) {
    val metadataList = diffPackage.getEntry("assets/so_meta.txt")
        ?.let(diffPackage::getInputStream)
        ?.use {
            it.readBytes()
        }
        ?.parsedLibraryMetadataList
        ?: emptyList()
    async {
        metadataList.forEach { metadata ->
            launch {
                val hash = if (metadata.baseCrc32 == 0L) {
                    // No base library file, the patched library file is newly added.
                    directory.createLibraryAsNewlyAdded(
                        metadata = metadata,
                        diffPackage = diffPackage,
                    )
                } else {
                    directory.createLibraryByMerging(
                        packageMetadata = packageMetadata,
                        metadata = metadata,
                        baseApk = baseApk,
                        diffPackage = diffPackage,
                    )
                }
                if (!hash.contentEquals(metadata.patchedHash)) {
                    throw TinkerError(
                        ErrorType.INVALID_DEPLOY_RESULT,
                        "Hash \"${hash.asMd5String}\" of patch result \"${metadata.name}(${metadata.abi})\" "
                                + "\"${diffPackage.name}\" is not match \"${metadata.patchedHash.asMd5String}\" in metadata.",
                    )
                }
            }
        }
        baseApk.entries()
            .asSequence()
            .filter { !it.isDirectory && it.name.startsWith("assets/tinker/lib/") }
            .map { entry ->
                val parts = entry.name.split("/").drop(3)
                val abi = parts[0]
                val name = parts[1]
                Triple(abi, name, entry)
            }
            .forEach { (abi, name, entry) ->
                launch {
                    val output = directory.resolve(abi).resolve(name)
                    baseApk.getInputStream(entry)
                        .use { stream ->
                            output.ensureParentIsExistingDirectory()
                                .outputStream()
                                .buffered()
                                .use(stream::copyTo)
                        }
                    if (output.crc32 != entry.crc) {
                        throw Tinker.Error(
                            Tinker.Error.Deploy.Legacy.Library.INVALID_DEPLOY_RESULT,
                            "CRC32 checksum \"${output.crc32}\" of test library file as patch result "
                                    + "is not match \"${entry.crc}\" in base apk file \"${baseApk.name}\".",
                        )
                    }
                }
            }
    }
}

internal fun libraryDeploy(
    packageMetadata: PackageMetadata,
    baseApk: ZipFile,
    diffPackage: ZipFile,
    directory: File
) {
    expected<ErrorType>("deploy libraries") {
        libraryDeployInternal(
            packageMetadata,
            baseApk,
            diffPackage,
            directory,
        )
    }
}