package com.tencent.tinker.internal.deploy

import android.content.Context
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
 * Deploy a patch with provided [version] and [diffPackage] by remote service.
 */
internal fun Context.deployPatchByRemote(
    version: String,
    diffPackage: File,
) {
}