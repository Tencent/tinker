package com.tencent.tinker.internal.deploy.legacy

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.Tinker
import com.tencent.tinker.bsdiff.BSPatch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.deploy.Deployer
import com.tencent.tinker.internal.deploy.legacy.dex.dexDeployToApk
import com.tencent.tinker.internal.deploy.legacy.library.libraryDeploy
import com.tencent.tinker.internal.deploy.legacy.resource.resourceDeploy
import com.tencent.tinker.internal.patchDexApkFile
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.patchResourceApkFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

private enum class ErrorType : TinkerError.Type {
    UNEXPECTED,
    MISSING_METADATA,
    MISSING_CUSTOM_MERGER;

    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.DEPLOY_LEGACY

    override val typeCode: Int
        get() = ordinal
}

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
            globalCustomLegacyMerger ?: throw TinkerError(
                ErrorType.MISSING_CUSTOM_MERGER,
                "Custom merger is required but nothing is provided."
            )
        } else {
            BSDiffMerger
        }
    }
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

    private fun deploy(
        baseApkFile: File,
        diffPackageFile: File,
        deployedDirectory: File,
    ) {
        val baseApk = ZipFile(baseApkFile)
        val diffPackage = ZipFile(diffPackageFile)
        val packageMetadata = diffPackage.getEntry("assets/package_meta.txt")
            ?.let(diffPackage::getInputStream)
            ?.use { it.readBytes() }
            ?.parsePackageMetadata
            ?: throw TinkerError(
                ErrorType.MISSING_METADATA,
                "Cannot find package metadata in patch ${diffPackage.name}."
            )
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