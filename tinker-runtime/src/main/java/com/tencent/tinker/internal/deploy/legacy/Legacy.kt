package com.tencent.tinker.internal.deploy.legacy

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.bsdiff.BSPatch
import com.tencent.tinker.internal.deploy.Deployer
import com.tencent.tinker.internal.deploy.legacy.dex.dexDeployToApk
import com.tencent.tinker.internal.deploy.legacy.library.libraryDeploy
import com.tencent.tinker.internal.deploy.legacy.resource.resourceDeploy
import com.tencent.tinker.internal.patchDexApkFile
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.patchResourceApkFile
import com.tencent.tinker.internal.util.async
import com.tencent.tinker.internal.util.crc32
import com.tencent.tinker.internal.util.debugLog
import com.tencent.tinker.internal.util.traceE
import com.tencent.tinker.internal.util.traceS
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.jar.JarFile
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
     * Whether to use custom merger.
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

private val Context.apkSignature: Signature?
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
            ?.signingCertificateHistory
            ?.firstOrNull()
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            .signatures
            ?.firstOrNull()
    }

internal object LegacyDeployer : Deployer() {

    private const val TAG = "Tinker.Deploy.Legacy"

    private fun checkSignature(
        baseApkSignature: Signature,
        diffPackageFile: File
    ) {
        val expected = baseApkSignature.toByteArray().crc32
        val diffPackage = JarFile(diffPackageFile)
        diffPackage.entries()
            .asSequence()
            .filter {
                !it.name.startsWith("META-INF/")
            }
            .forEach { entry ->
                diffPackage.getInputStream(entry)
                    .use {
                        // Read entry content and just drop it for loading certificates.
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = it.read(buffer)
                            if (read < 0) {
                                break
                            }
                        }
                    }
                val pass = entry.certificates
                    ?.takeIf { it.isNotEmpty() }
                    ?.any { it.encoded.crc32 == expected }
                    ?: false
                if (!pass) {
                    throw Tinker.Error(
                        Tinker.Error.Deploy.Legacy.CHECK_SIGNATURE_FAILED,
                        "Entry \"${entry.name}\" has different signature with base APK."
                    )
                }
            }
    }

    private fun deploy(
        baseApkFile: File,
        baseApkSignature: Signature?,
        diffPackageFile: File,
        deployedDirectory: File,
    ) {
        debugLog(TAG) {
            "Start deploying \"${diffPackageFile.absolutePath}\"" +
                    " with \"${baseApkFile.absolutePath}\"" +
                    " to \"${deployedDirectory.absolutePath}\"" +
                    " via legacy way."
        }
        if (baseApkSignature != null) {
            checkSignature(baseApkSignature, diffPackageFile)
        }
        val baseApk = ZipFile(baseApkFile)
        val diffPackage = ZipFile(diffPackageFile)
        val packageMetadata = traceE("deploy.legacy.read_metadata") {
            diffPackage.getEntry("assets/package_meta.txt")
                ?.let(diffPackage::getInputStream)
                ?.use { it.readBytes() }
                ?.parsePackageMetadata
                ?: throw Tinker.Error(
                    Tinker.Error.Deploy.Legacy.MISSING_METADATA,
                    "Cannot find package metadata in patch ${diffPackage.name}."
                )
        }
        debugLog(TAG) {
            buildList {
                add("Read package metadata from \"${diffPackage.name}\":")
                add(packageMetadata)
            }.joinToString("\n")
        }
        async {
            launch {
                traceS("deploy.legacy.dex") {
                    dexDeployToApk(
                        baseApk = baseApk,
                        diffPackage = diffPackage,
                        apk = deployedDirectory.patchDexApkFile,
                    )
                }
            }
            launch {
                traceS("deploy.legacy.library") {
                    libraryDeploy(
                        packageMetadata = packageMetadata,
                        baseApk = baseApk,
                        diffPackage = diffPackage,
                        directory = deployedDirectory.patchLibraryDirectory,
                    )
                }
            }
            launch {
                traceS("deploy.legacy.resource") {
                    resourceDeploy(
                        packageMetadata = packageMetadata,
                        baseApk = baseApk,
                        diffPackage = diffPackage,
                        apk = deployedDirectory.patchResourceApkFile,
                    )
                }
            }
        }
    }

    override fun deploy(
        applicationContext: Context,
        diffPackage: File,
        skipCheckingSignature: Boolean,
        deployedDirectory: File,
    ) {
        deploy(
            baseApkFile = applicationContext.applicationInfo.sourceDir.let(::File),
            diffPackageFile = diffPackage,
            baseApkSignature = if (skipCheckingSignature) {
                null
            } else {
                applicationContext.apkSignature
                    ?: throw Tinker.Error(
                        Tinker.Error.Deploy.Legacy.READ_BASE_APK_SIGNATURE_FAILED,
                        "Cannot read base APK signature.",
                    )
            },
            deployedDirectory = deployedDirectory
        )
    }
}