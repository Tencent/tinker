package com.tencent.tinker.internal.clean

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.annotation.DeployProcessOnly
import com.tencent.tinker.internal.errorTypeShouldBeThrown
import com.tencent.tinker.internal.module.oat.OatManager
import com.tencent.tinker.internal.module.patch.CleanedRawPatch
import com.tencent.tinker.internal.module.patch.RawPatchManager
import com.tencent.tinker.internal.util.debugLog
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.infoLog
import com.tencent.tinker.internal.util.isInDeployProcess
import com.tencent.tinker.internal.util.traceE
import com.tencent.tinker.internal.util.traceS
import com.tencent.tinker.internal.util.traceTask
import kotlin.concurrent.thread

private const val TAG = "Tinker.Clean"

@DeployProcessOnly
private fun cleanOatDirectories(
    applicationContext: Context,
    cleaned: Iterable<CleanedRawPatch>,
    oatManager: OatManager = OatManager.with(applicationContext),
) {
    cleaned.forEach {
        traceS("clean.oat.clean(dir = ${it.version})") {
            oatManager.clean(it.directory)
        }
    }
}

@DeployProcessOnly
private fun cleanPatches(
    applicationContext: Context,
    strategy: Strategy,
    rawPatchManager: RawPatchManager = RawPatchManager.with(applicationContext),
): List<String> {
    val cleaned = traceE("clean.clean(strategy = ${strategy.key})") {
        when (strategy) {
            Strategy.CLEAN_ALL -> rawPatchManager.cleanAll()
            Strategy.CLEAN_OBSOLETE -> rawPatchManager.cleanObsolete()
        }
    }
    cleanOatDirectories(applicationContext, cleaned)
    return cleaned.map { it.version }
}

@DeployProcessOnly
private fun cleanPatches(
    applicationContext: Context,
    intent: Intent,
): List<String> {
    val strategyIndex = intent.getIntExtra(CLEAN_IPC_KEY_STRATEGY, -1)
    if (strategyIndex == -1) {
        throw Tinker.Error(
            Tinker.Error.Clean.MISSING_STRATEGY,
            "Strategy is missing while cleaning patch."
        )
    }
    val strategy = Strategy.entries.getOrNull(strategyIndex)
        ?: throw Tinker.Error(
            Tinker.Error.Clean.INVALID_STRATEGY,
            "Strategy index $strategyIndex is out of range."
        )
    debugLog(TAG) {
        "Cleaning patches with strategy \"${strategy.name.lowercase()}\"."
    }
    return cleanPatches(applicationContext, strategy)
}

private const val CLEAN_IPC_KEY_STRATEGY = "s"

private enum class Strategy(val key: String) {
    CLEAN_ALL("clean_all"),
    CLEAN_OBSOLETE("clean_obsolete"),
}

@DeployProcessOnly
class TinkerCleanService : Service() {

    private fun runTask(intent: Intent) {
        thread(name = "tinker-clean") {
            infoLog(TAG) {
                "Cleaning request received. Start cleaning."
            }
            val (pair, events) = traceTask("clean") {
                try {
                    val versions = expected<Tinker.Error.Clean, List<String>>("clean patch") {
                        cleanPatches(applicationContext, intent)
                    }
                    versions to null
                } catch (error: Tinker.Error) {
                    if (error.type in errorTypeShouldBeThrown) {
                        throw error
                    }
                    null to error
                }
            }
            val (versions, error) = pair
            val app = application as? Tinker.App
                ?: throw Tinker.Error(
                    Tinker.Error.Usage.APP_IS_NOT_TINKER_APP,
                    "Application instance is not a \"${Tinker.App::class.java.name}\" subclass instance."
                )
            app.cleanCallback()?.onTaskComplete(
                Tinker.TaskSummary.Clean(
                    error,
                    events,
                    versions,
                )
            )
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

internal fun Context.cleanAllPatchesByRemote() {
    infoLog(TAG) {
        "Send clean all patches request to remote."
    }
    Intent(this, TinkerCleanService::class.java)
        .apply {
            putExtra(CLEAN_IPC_KEY_STRATEGY, Strategy.CLEAN_ALL.ordinal)
        }
        .let(::startService)
}

internal fun Context.cleanObsoletePatchesByRemote() {
    infoLog(TAG) {
        "Send clean obsolete patches request to remote."
    }
    Intent(this, TinkerCleanService::class.java)
        .apply {
            putExtra(CLEAN_IPC_KEY_STRATEGY, Strategy.CLEAN_OBSOLETE.ordinal)
        }
        .let(::startService)
}

@JvmOverloads
internal fun Context.requestPatchAsUnavailable(
    version: String,
    rawPatchManager: RawPatchManager = RawPatchManager.with(this.applicationContext),
) {
    require(!isInDeployProcess) {
        "Cannot request patch as unavailable in deploy process."
    }
    infoLog(TAG) {
        "Request patch with version \"${version}\" as unavailable."
    }
    rawPatchManager.requestUnavailable(version)
}