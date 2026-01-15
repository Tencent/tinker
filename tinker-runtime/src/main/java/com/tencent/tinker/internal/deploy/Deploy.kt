package com.tencent.tinker.internal.deploy

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.annotation.DeployProcessOnly
import com.tencent.tinker.internal.deploy.legacy.LegacyDeployer
import com.tencent.tinker.internal.module.oat.OatManager
import com.tencent.tinker.internal.module.patch.RawPatchManager
import com.tencent.tinker.internal.module.validate.Validator
import com.tencent.tinker.internal.module.validate.ValidatorImpl
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.withTemporaryDirectory
import java.io.File

/**
 * Patch deployer used to convert diff package to loadable patch files and store them persistently.
 */
internal abstract class Deployer {

    /**
     * Converts [diffPackage] to loadable patch files and store them into [deployedDirectory].
     */
    abstract fun deploy(
        context: Context,
        diffPackage: File,
        deployedDirectory: File,
    )
}

/**
 * Creates patch with provided [version] and [diffPackage].
 */
@DeployProcessOnly
private fun deployPatch(
    context: Context,
    version: String,
    diffPackage: File,
    deployer: Deployer,
    validator: Validator = ValidatorImpl,
    rawPatchManager: RawPatchManager = RawPatchManager.with(context),
    oatManager: OatManager = OatManager.with(context),
) {
    withTemporaryDirectory { temporaryDirectory ->
        deployer.deploy(
            context = context,
            diffPackage = diffPackage,
            deployedDirectory = temporaryDirectory,
        )
        validator.createValidationFingerprint(temporaryDirectory)
        val rawPatch = rawPatchManager.create(version, temporaryDirectory)
        oatManager.generateIfNeeded(rawPatch.directory)
    }
}

private val File.isZipFile: Boolean
    get() {
        val magic = inputStream().use { stream ->
            ByteArray(4).also(stream::read)
        }
        return magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
    }

@DeployProcessOnly
private fun deployPatch(
    context: Context,
    intent: Intent,
) {
    val version = intent.getStringExtra(DEPLOY_IPC_KEY_VERSION)
        ?: throw Tinker.Error(
            Tinker.Error.Deploy.MISSING_VERSION,
            "Version is missing while deploying patch."
        )
    val diffPackage = intent.getStringExtra(DEPLOY_IPC_KEY_DIFF_PACKAGE)
        ?.let(::File)
        ?: throw Tinker.Error(
            Tinker.Error.Deploy.MISSING_DIFF_PACKAGE,
            "Diff package is missing while deploying patch."
        )
    if (!diffPackage.isFile) {
        throw Tinker.Error(
            Tinker.Error.Deploy.INVALID_DIFF_PACKAGE,
            "Diff package \"${diffPackage.path}\" is not an existing file."
        )
    }
    val deployer = when {
        diffPackage.isZipFile -> LegacyDeployer
        else -> throw Tinker.Error(
            Tinker.Error.Deploy.INVALID_DIFF_PACKAGE,
            "Format of diff package \"${diffPackage.path}\" is unsupported."
        )
    }
    deployPatch(
        context = context,
        version = version,
        diffPackage = diffPackage,
        deployer = deployer,
    )
}

private const val DEPLOY_IPC_KEY_VERSION = "v"
private const val DEPLOY_IPC_KEY_DIFF_PACKAGE = "d"

@DeployProcessOnly
class TinkerDeployService : Service() {

    private fun runTask(intent: Intent) {
        val error = try {
            expected<Tinker.Error.Deploy>("deploy patch") {
                deployPatch(this, intent)
            }
            null
        } catch (error: Tinker.Error) {
            error
        }
        application
            .let { it as? Tinker.App }
            ?.deployCallback
            ?.onTaskComplete(error)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        try {
            runTask(intent)
        } finally {
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/**
 * Deploy a patch with provided [version] and [diffPackage] by remote service.
 */
internal fun Context.deployPatchByRemote(
    version: String,
    diffPackage: File,
) {
    Intent(this, TinkerDeployService::class.java)
        .apply {
            putExtra(DEPLOY_IPC_KEY_VERSION, version)
            putExtra(DEPLOY_IPC_KEY_DIFF_PACKAGE, diffPackage.absolutePath)
        }
        .let(::startService)
}