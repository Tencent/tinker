package com.tencent.tinker.internal.clean

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.annotation.DeployProcessOnly
import com.tencent.tinker.internal.module.oat.OatManager
import com.tencent.tinker.internal.module.patch.CleanedRawPatch
import com.tencent.tinker.internal.module.patch.RawPatchManager
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.isInDeployProcess
import kotlin.concurrent.thread

private const val TAG = "Tinker.Clean"

@DeployProcessOnly
private fun cleanOatDirectories(
    context: Context,
    cleaned: Iterable<CleanedRawPatch>,
    oatManager: OatManager = OatManager.with(context),
) {
    cleaned.forEach {
        oatManager.clean(it.directory)
    }
}

@DeployProcessOnly
private fun cleanPatches(
    context: Context,
    strategy: Strategy,
    rawPatchManager: RawPatchManager = RawPatchManager.with(context),
) {
    val cleaned = when (strategy) {
        Strategy.CLEAN_ALL -> rawPatchManager.cleanAll()
        Strategy.CLEAN_OBSOLETE -> rawPatchManager.cleanObsolete()
    }
    cleanOatDirectories(context, cleaned)
}

@DeployProcessOnly
private fun cleanPatches(
    context: Context,
    intent: Intent,
) {
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
    cleanPatches(context, strategy)
}

private const val CLEAN_IPC_KEY_STRATEGY = "s"

private enum class Strategy {
    CLEAN_ALL,
    CLEAN_OBSOLETE,
}

@DeployProcessOnly
class TinkerCleanService : Service() {

    private fun runTask(intent: Intent) {
        thread(name = "tinker-clean") {
            val error = try {
                expected<Tinker.Error.Clean>("clean patch") {
                    cleanPatches(this, intent)
                }
                null
            } catch (error: Tinker.Error) {
                error
            }
            application
                .let { it as? Tinker.App }
                ?.cleanCallback
                ?.onTaskComplete(error)
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
    Intent(this, TinkerCleanService::class.java)
        .apply {
            putExtra(CLEAN_IPC_KEY_STRATEGY, Strategy.CLEAN_ALL.ordinal)
        }
        .let(::startService)
}

internal fun Context.cleanObsoletePatchesByRemote() {
    Intent(this, TinkerCleanService::class.java)
        .apply {
            putExtra(CLEAN_IPC_KEY_STRATEGY, Strategy.CLEAN_OBSOLETE.ordinal)
        }
        .let(::startService)
}

internal fun Context.requestPatchAsUnavailable(
    version: String,
    rawPatchManager: RawPatchManager = RawPatchManager.with(this),
) {
    require(!isInDeployProcess) {
        "Cannot request patch as unavailable in deploy process."
    }
    rawPatchManager.requestUnavailable(version)
}