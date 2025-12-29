package com.tencent.tinker.internal.load.resource

import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.Loader

private enum class ErrorType : TinkerError.Type {
    UNEXPECTED,
    NO_VALID_INPUTS;

    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.LOAD
}

internal class ResourceLoader : Loader() {
    override fun load() {
        TODO("Not yet implemented")
    }

    class Factory : Loader.Factory() {
        override fun createLoaderIfNeeded(patch: Patch): ResourceLoader {
            TODO("Not yet implemented")
        }
    }
}