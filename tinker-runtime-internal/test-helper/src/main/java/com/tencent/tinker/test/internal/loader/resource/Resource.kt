package com.tencent.tinker.test.internal.loader.resource

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView

class TestResourceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this),
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
}