package com.tencent.tinker.internal.deploy

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.annotation.DeployProcessOnly
import com.tencent.tinker.internal.deploy.legacy.LegacyDeployer
import com.tencent.tinker.internal.errorTypeShouldBeThrown
import com.tencent.tinker.internal.module.oat.OatManager
import com.tencent.tinker.internal.module.patch.RawPatchManager
import com.tencent.tinker.internal.module.validate.Validator
import com.tencent.tinker.internal.module.validate.ValidatorImpl
import com.tencent.tinker.internal.util.debugLog
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.infoLog
import com.tencent.tinker.internal.util.traceE
import com.tencent.tinker.internal.util.traceS
import com.tencent.tinker.internal.util.traceTask
import com.tencent.tinker.internal.util.withTemporaryDirectory
import java.io.File
import kotlin.concurrent.thread

private const val TAG = "Tinker.Deploy"

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
        skipCheckingSignature: Boolean,
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
    skipCheckingSignature: Boolean,
    deployer: Deployer,
    validator: Validator = ValidatorImpl,
    rawPatchManager: RawPatchManager = RawPatchManager.with(context),
    oatManager: OatManager = OatManager.with(context),
) {
    withTemporaryDirectory { temporaryDirectory ->
        debugLog(TAG) {
            "Deploying diff package \"${diffPackage.absolutePath}\"" +
                    " with version \"${version}\"" +
                    " via deployer \"${deployer.javaClass.name}\"" +
                    " to \"${temporaryDirectory.absolutePath}\"."
        }
        traceS("deploy.deploy(deployer = ${deployer.javaClass.simpleName}@${deployer.hashCode().toString(16)})") {
            deployer.deploy(
                context = context,
                diffPackage = diffPackage,
                skipCheckingSignature = skipCheckingSignature,
                deployedDirectory = temporaryDirectory,
            )
        }
        debugLog(TAG) {
            "Creating validation fingerprint for \"${temporaryDirectory.absolutePath}\"" +
                    " with validator <${validator.javaClass.name}>."
        }
        traceS("deploy.validate.create_fingerprint") {
            validator.createValidationFingerprint(temporaryDirectory)
        }
        debugLog(TAG) {
            "Creating raw patch \"${version}\" with \"${temporaryDirectory.absolutePath}\"."
        }
        val rawPatch = traceE("deploy.raw_patch.create") {
            rawPatchManager.create(version, temporaryDirectory)
        }
        debugLog(TAG) {
            "Generating OAT for \"${rawPatch.directory.absolutePath}\" if needed" +
                    " with manager <${oatManager.javaClass.name}>."
        }
        traceS("deploy.oat.generate") {
            oatManager.generateIfNeeded(rawPatch.directory)
        }
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
    debugLog(TAG) {
        "Version \"${version}\" from deploy request intent is read."
    }
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
    debugLog(TAG) {
        "Diff package \"${diffPackage.absolutePath}\" from deploy request intent is read."
    }
    val skipCheckingSignature = intent.getBooleanExtra(
        DEPLOY_IPC_KEY_SKIP_CHECKING_SIGNATURE,
        false,
    )
    debugLog(TAG) {
        "Skip checking signature \"${skipCheckingSignature}\" from deploy request intent is read."
    }
    val deployer = when {
        diffPackage.isZipFile -> {
            debugLog(TAG) {
                "Diff package is legacy type. Using legacy deployer."
            }
            LegacyDeployer
        }

        else -> throw Tinker.Error(
            Tinker.Error.Deploy.INVALID_DIFF_PACKAGE,
            "Format of diff package \"${diffPackage.path}\" is unsupported."
        )
    }
    deployPatch(
        context = context,
        version = version,
        diffPackage = diffPackage,
        skipCheckingSignature = skipCheckingSignature,
        deployer = deployer,
    )
}

private const val DEPLOY_IPC_KEY_VERSION = "v"
private const val DEPLOY_IPC_KEY_DIFF_PACKAGE = "d"
private const val DEPLOY_IPC_KEY_SKIP_CHECKING_SIGNATURE = "c"

@DeployProcessOnly
class TinkerDeployService : Service() {

    private fun runTask(intent: Intent) {
        thread(name = "tinker-deploy") {
            infoLog(TAG) {
                "Deploying request received. Start deploying."
            }
            val (error, events) = traceTask("deploy") {
                try {
                    expected<Tinker.Error.Deploy>("deploy patch") {
                        deployPatch(this, intent)
                    }
                    null
                } catch (error: Tinker.Error) {
                    if (error.type in errorTypeShouldBeThrown) {
                        throw error
                    }
                    error
                }
            }
            application
                .let { it as? Tinker.App }
                ?.deployCallback
                ?.apply {
                    onTaskComplete(
                        Tinker.TaskSummary(
                            error = error,
                            events = events,
                        )
                    )
                }
        }
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
    skipCheckingSignature: Boolean,
) {
    infoLog(TAG) {
        "Send deploying request to remote" +
                " with version \"${version}\"" +
                " and diff package \"${diffPackage.absolutePath}\"."
    }
    Intent(this, TinkerDeployService::class.java)
        .apply {
            putExtra(DEPLOY_IPC_KEY_VERSION, version)
            putExtra(DEPLOY_IPC_KEY_DIFF_PACKAGE, diffPackage.absolutePath)
            putExtra(DEPLOY_IPC_KEY_SKIP_CHECKING_SIGNATURE, skipCheckingSignature)
        }
        .let(::startService)
}