package com.tencent.tinker.internal.deploy

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.Build
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.JobId
import com.tencent.tinker.internal.annotation.DeployProcessOnly
import com.tencent.tinker.internal.deploy.legacy.LegacyDeployer
import com.tencent.tinker.internal.errorTypeShouldBeThrown
import com.tencent.tinker.internal.module.oat.OatManager
import com.tencent.tinker.internal.module.patch.RawPatchManager
import com.tencent.tinker.internal.module.validate.Validator
import com.tencent.tinker.internal.module.validate.ValidatorImpl
import com.tencent.tinker.internal.util.className
import com.tencent.tinker.internal.util.debugLog
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.infoLog
import com.tencent.tinker.internal.util.scheduleJob
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
        applicationContext: Context,
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
    applicationContext: Context,
    version: String,
    diffPackage: File,
    skipCheckingSignature: Boolean,
    deployer: Deployer,
    validator: Validator = ValidatorImpl,
    rawPatchManager: RawPatchManager = RawPatchManager.with(applicationContext),
    oatManager: OatManager = OatManager.with(applicationContext),
) {
    withTemporaryDirectory { temporaryDirectory ->
        debugLog(TAG) {
            "Deploying diff package \"${diffPackage.absolutePath}\"" +
                    " with version \"${version}\"" +
                    " via deployer \"${deployer.javaClass.name}\"" +
                    " to \"${temporaryDirectory.absolutePath}\"."
        }
        traceS("deploy.deploy(deployer = ${deployer.javaClass.className}@${deployer.hashCode().toString(16)})") {
            deployer.deploy(
                applicationContext = applicationContext,
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

private class DeployResult(
    val version: String,
    val sourceDiffPackage: File,
)

@DeployProcessOnly
private fun deployPatch(
    applicationContext: Context,
    params: JobParameters,
): DeployResult {
    val version = params.extras.getString(DEPLOY_IPC_KEY_VERSION)
        ?: throw Tinker.Error(
            Tinker.Error.Deploy.MISSING_VERSION,
            "Version is missing while deploying patch."
        )
    debugLog(TAG) {
        "Version \"${version}\" from deploy request parameters is read."
    }
    val diffPackage = params.extras.getString(DEPLOY_IPC_KEY_DIFF_PACKAGE)
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
        "Diff package \"${diffPackage.absolutePath}\" from deploy request parameters is read."
    }
    val skipCheckingSignature = params.extras.getInt(DEPLOY_IPC_KEY_SKIP_CHECKING_SIGNATURE) == 1
    debugLog(TAG) {
        "Skip checking signature \"${skipCheckingSignature}\" from deploy request parameters is read."
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
        applicationContext = applicationContext,
        version = version,
        diffPackage = diffPackage,
        skipCheckingSignature = skipCheckingSignature,
        deployer = deployer,
    )
    return DeployResult(
        version = version,
        sourceDiffPackage = diffPackage,
    )
}

private const val DEPLOY_IPC_KEY_VERSION = "v"
private const val DEPLOY_IPC_KEY_DIFF_PACKAGE = "d"
private const val DEPLOY_IPC_KEY_SKIP_CHECKING_SIGNATURE = "c"

@DeployProcessOnly
class TinkerDeployService : JobService() {

    private fun runTask(params: JobParameters) {
        thread(name = "tinker-deploy") {
            infoLog(TAG) {
                "Deploying request received. Start deploying."
            }
            val (pair, events) = traceTask("deploy") {
                try {
                    val result = expected<Tinker.Error.Deploy, DeployResult>("deploy patch") {
                        deployPatch(applicationContext, params)
                    }
                    result to null
                } catch (error: Tinker.Error) {
                    if (error.type in errorTypeShouldBeThrown) {
                        throw error
                    }
                    null to error
                }
            }
            val (result, error) = pair
            val app = application as? Tinker.App
                ?: throw Tinker.Error(
                    Tinker.Error.Usage.APP_IS_NOT_TINKER_APP,
                    "Application instance is not a \"${Tinker.App::class.java.name}\" subclass instance."
                )
            app.deployCallback()?.onTaskComplete(
                Tinker.TaskSummary.Deploy(
                    error,
                    events,
                    result?.version,
                    result?.sourceDiffPackage,
                )
            )
            jobFinished(params, false)
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        runTask(params)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val app = application as? Tinker.App
            ?: throw Tinker.Error(
                Tinker.Error.Usage.APP_IS_NOT_TINKER_APP,
                "Application instance is not a \"${Tinker.App::class.java.name}\" subclass instance."
            )
        app.deployCallback()?.onTaskComplete(
            Tinker.TaskSummary.Deploy(
                Tinker.Error(
                    Tinker.Error.Deploy.INTERRUPTED,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Clean task is interrupted with code ${params.stopReason}."
                    } else {
                        "Clean task is interrupted."
                    }
                ),
                emptyList<Tinker.TraceEvent>(),
                null,
                null,
            )
        )
        return false
    }
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
    scheduleJob(
        JobId.DEPLOY.id,
        TinkerDeployService::class.java,
    ) {
        putString(DEPLOY_IPC_KEY_VERSION, version)
        putString(DEPLOY_IPC_KEY_DIFF_PACKAGE, diffPackage.absolutePath)
        putInt(DEPLOY_IPC_KEY_SKIP_CHECKING_SIGNATURE, if (skipCheckingSignature) 1 else 0)
    }
}