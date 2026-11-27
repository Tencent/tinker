package com.tencent.tinker.loader.shareutil;

import android.app.Instrumentation;
import android.os.Bundle;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/** Runs metadata regression tests on Android without adding a platform-mocking dependency. */
public final class PatchConsistencyTestRunner extends Instrumentation {
    @Override
    public void onCreate(
            final Bundle arguments
    ) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        System.setProperty("java.io.tmpdir", getTargetContext().getCacheDir().getAbsolutePath());
        final Result result = JUnitCore.runClasses(SharePatchInfoTest.class);
        final Bundle summary = new Bundle();
        final StringBuilder failures = new StringBuilder();
        for (final Failure failure : result.getFailures()) {
            failures.append(failure.toString()).append('\n').append(failure.getTrace()).append('\n');
        }
        summary.putInt("tests", result.getRunCount());
        summary.putInt("failures", result.getFailureCount());
        summary.putString("stream", failures.toString());
        finish(result.wasSuccessful() ? -1 : 0, summary);
    }
}
