package com.tencent.tinker.internal.deploy.legacy

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.Tinker
import com.tencent.tinker.bsdiff.BSPatch
import com.tencent.tinker.internal.deploy.Deployer
import com.tencent.tinker.internal.deploy.legacy.dex.dexDeployToApk
import com.tencent.tinker.internal.deploy.legacy.library.libraryDeploy
import com.tencent.tinker.internal.deploy.legacy.resource.resourceDeploy
import com.tencent.tinker.internal.patchDexApkFile
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.patchResourceApkFile
import com.tencent.tinker.internal.util.debugLog
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

@Volatile
internal var globalCustomLegacyMerger = null as Tinker.LegacyMerger?

private object BSDiffMerger : Tinker.LegacyMerger {
    override fun merge(
        baseInput: InputStream,
        diffInput: InputStream,
        patchedOutput: OutputStream
    ) {
        patchedOutput.write(
            BSPatch.patchFast(
                baseInput,
                diffInput,
            ),
        )
    }
}

internal class PackageMetadata(
    /**
     * Whether use custom merger.
     */
    private val useCustomMerger: Boolean,
) {
    val merger by lazy {
        if (useCustomMerger) {
            globalCustomLegacyMerger ?: throw Tinker.Error(
                Tinker.Error.Deploy.Legacy.MISSING_CUSTOM_MERGER,
                "Custom merger is required but nothing is provided."
            )
        } else {
            BSDiffMerger
        }
    }

    override fun toString(): String = buildList {
        add("package_metadata {")
        add("  use_custom_merger: $useCustomMerger")
        add("}")
    }.joinToString("\n")
}

private val ByteArray.parsePackageMetadata: PackageMetadata
    get() = toString(Charsets.UTF_8)
        .split("\n")
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { it.substringBefore("=") to it.substringAfter("=") }
        .let {
            PackageMetadata(
                useCustomMerger = it["use_custom_file_patch"] == "1",
            )
        }

internal object LegacyDeployer : Deployer() {

    private const val TAG = "Tinker.Deploy.Legacy"

    private fun deploy(
        baseApkFile: File,
        diffPackageFile: File,
        deployedDirectory: File,
    ) {
        debugLog(TAG) {
            "Start deploying \"${diffPackageFile.absolutePath}\"" +
                    " with \"${baseApkFile.absolutePath}\"" +
                    " to \"${deployedDirectory.absolutePath}\"" +
                    " via legacy way."
        }
        val baseApk = ZipFile(baseApkFile)
        val diffPackage = ZipFile(diffPackageFile)
        val packageMetadata = diffPackage.getEntry("assets/package_meta.txt")
            ?.let(diffPackage::getInputStream)
            ?.use { it.readBytes() }
            ?.parsePackageMetadata
            ?: throw Tinker.Error(
                Tinker.Error.Deploy.Legacy.MISSING_METADATA,
                "Cannot find package metadata in patch ${diffPackage.name}."
            )
        debugLog(TAG) {
            buildList {
                add("Read package metadata from \"${diffPackage.name}\":")
                add(packageMetadata)
            }.joinToString("\n")
        }
        dexDeployToApk(
            baseApk = baseApk,
            diffPackage = diffPackage,
            apk = deployedDirectory.patchDexApkFile,
        )
        libraryDeploy(
            packageMetadata = packageMetadata,
            baseApk = baseApk,
            diffPackage = diffPackage,
            directory = deployedDirectory.patchLibraryDirectory,
        )
        resourceDeploy(
            packageMetadata = packageMetadata,
            baseApk = baseApk,
            diffPackage = diffPackage,
            apk = deployedDirectory.patchResourceApkFile,
        )
    }

    @VisibleForTesting
    fun deployForTesting(
        baseApkFile: File,
        diffPackageFile: File,
        deployedDirectory: File,
    ) {
        deploy(
            baseApkFile = baseApkFile,
            diffPackageFile = diffPackageFile,
            deployedDirectory = deployedDirectory
        )
    }

    override fun deploy(context: Context, diffPackage: File, deployedDirectory: File) {
        deploy(
            baseApkFile = context.applicationInfo.sourceDir.let(::File),
            diffPackageFile = diffPackage,
            deployedDirectory = deployedDirectory
        )
    }
}