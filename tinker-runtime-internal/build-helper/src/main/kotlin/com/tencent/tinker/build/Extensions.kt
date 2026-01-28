package com.tencent.tinker.build

internal val String.capitalized: String
    get() = replaceFirstChar { it.uppercase() }