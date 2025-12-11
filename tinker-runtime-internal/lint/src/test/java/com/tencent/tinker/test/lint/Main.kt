package com.tencent.tinker.test.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.tencent.tinker.lint.ProcessContextDetector
import org.junit.Test

class ProcessContextDetectorTest {

    /**
     * Tests calling functions annotated with @MainProcessOnly from invalid callers can raise errors
     * expected.
     */
    @Test
    fun mainProcessOnlyFunctionCalledFromInvalidCaller() {
        lint()
            .files(
                kotlin(
                    """
                        package com.tencent.tinker.internal.annotation
                        
                        @Retention(AnnotationRetention.SOURCE)
                        annotation class MainProcessOnly
                    """.trimIndent()
                ).indented(),
                kotlin(
                    """
                        import com.tencent.tinker.internal.annotation.MainProcessOnly
                        
                        @MainProcessOnly
                        fun shouldCallInMainProcess() {
                        }
                        
                        fun anotherFunction() {
                            shouldCallInMainProcess() // Should report error.
                        }
                    """.trimIndent()
                ).indented(),
            )
            .issues(ProcessContextDetector.ISSUE_MAIN_PROCESS_ONLY)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expect(
                """
                src/test.kt:8: Error: This member should only be called from a @MainProcessOnly annotated member, or when Context.isInMainProcess returns true. [MainProcessOnlyViolation]
                    shouldCallInMainProcess() // Should report error.
                    ~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    /**
     * Tests calling functions annotated with @MainProcessOnly from invalid branches can raise
     * errors expected.
     */
    @Test
    fun mainProcessOnlyFunctionCalledInInvalidRemainBranch() {
        lint()
            .files(
                kotlin(
                    """
                        package com.tencent.tinker.internal.annotation
                        
                        @Retention(AnnotationRetention.SOURCE)
                        annotation class MainProcessOnly
                    """.trimIndent()
                ).indented(),
                kotlin(
                    """
                        package android.content
                        
                        class Context
                        
                        val Context.isInMainProcess: Boolean
                            get() = true
                    """.trimIndent()
                ).indented(),
                kotlin(
                    """
                        import android.content.Context
                        import com.tencent.tinker.internal.annotation.MainProcessOnly
                        
                        @MainProcessOnly
                        fun shouldCallInMainProcess() {
                        }
                        
                        fun anyCheck(): Boolean {
                            return false
                        }
                        
                        fun anotherFunction(context: Context) {
                            if (anyCheck()) {
                                if (context.isInMainProcess) {
                                    return
                                }
                                shouldCallInMainProcess() // Should report error.
                            }
                        }
                    """.trimIndent()
                ).indented(),
            )
            .issues(ProcessContextDetector.ISSUE_MAIN_PROCESS_ONLY)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expect(
                """
                src/test.kt:17: Error: This member should only be called from a @MainProcessOnly annotated member, or when Context.isInMainProcess returns true. [MainProcessOnlyViolation]
                        shouldCallInMainProcess() // Should report error.
                        ~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent()
            )
    }

    // TODO: Complete test cases.
}
