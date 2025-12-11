// TODO:
//   Remove when Kotlin version is upgraded.
@file:Suppress("CONTEXT_RECEIVERS_DEPRECATED")

package com.tencent.tinker.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMember
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.resolveToUElement

private inline fun <reified T : UElement> UElement.parentOfType(): T? {
    var current: UElement = uastParent ?: return null
    while (true) {
        current
            .let { it as? T }
            ?.let { return it }
        current = current.uastParent ?: return null
    }
}

private val UCallExpression.caller: UMethod?
    get() = parentOfType<UMethod>()

private val UMethod.clazz: UClass?
    get() = parentOfType<UClass>()


class ProcessContextDetector : Detector(), Detector.UastScanner {

    companion object {

        val ISSUE_MAIN_PROCESS_ONLY = Issue.create(
            id = "MainProcessOnlyViolation",
            briefDescription = "Member annotated with @MainProcessOnly called in wrong context",
            explanation = """
                Members, like functions and properties, annotated as main process only can only be
                called from another member annotated as main process only, or a code branch where
                android.content.Context.isInMainProcess returns true.
            """.trimIndent().replace('\n', ' '),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ProcessContextDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val ISSUE_PATCH_PROCESS_ONLY = Issue.create(
            id = "PatchProcessOnlyViolation",
            briefDescription = "Member annotated with @PatchProcessOnly called in wrong context",
            explanation = """
                Members, like functions and properties, annotated as patch process only can only be
                called from another member annotated as patch process only, or a code branch where
                android.content.Context.isInPatchProcess returns true.
            """.trimIndent().replace('\n', ' '),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ProcessContextDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val ISSUE_NON_PATCH_PROCESS_ONLY = Issue.create(
            id = "NonPatchProcessOnlyViolation",
            briefDescription = "Member annotated with @NonPatchProcessOnly called in wrong context",
            explanation = """
                Members, like functions and properties, annotated as non-patch process only can only
                be called from another member annotated as main process only or non-patch process
                only, or a code branch where android.content.Context.isInMainProcess returns true or
                android.content.Context.isInPatchProcess returns false.
            """.trimIndent().replace('\n', ' '),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ProcessContextDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    class Registry : IssueRegistry() {

        override val vendor: Vendor = Vendor(
            vendorName = "Tencent WeChat",
            feedbackUrl = "https://github.com/Tencent/tinker/issues",
        )

        override val issues = listOf(
            ISSUE_MAIN_PROCESS_ONLY,
            ISSUE_PATCH_PROCESS_ONLY,
            ISSUE_NON_PATCH_PROCESS_ONLY,
        )
    }

    private enum class ProcessAnnotations(val clazz: String) {
        MAIN_PROCESS_ONLY("com.tencent.tinker.internal.annotation.MainProcessOnly"),
        PATCH_PROCESS_ONLY("com.tencent.tinker.internal.annotation.PatchProcessOnly"),
        NON_PATCH_PROCESS_ONLY("com.tencent.tinker.internal.annotation.NonPatchProcessOnly");

        companion object {

            private val byClassMapping by lazy {
                entries.associateBy { it.clazz }
            }

            fun byClass(clazz: String): ProcessAnnotations? =
                byClassMapping[clazz]
        }
    }

    private enum class ProcessCheckProperties(
        val property: String,
    ) {
        IS_IN_MAIN_PROCESS("isInMainProcess"),
        IS_IN_PATCH_PROCESS("isInPatchProcess");

        companion object {
            const val DEFINE_CLASS_NAME = "com.tencent.tinker.internal.util.SystemKt"
            const val RECEIVER_CLASS_NAME = "android.content.Context"
        }
    }

    context(JavaContext)
    private val PsiMember.processAnnotation: ProcessAnnotations?
        get() = evaluator.getAnnotations(this).firstNotNullOfOrNull {
            it.qualifiedName?.let(ProcessAnnotations::byClass)
        }

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                context.apply {
                    val annotation = node.resolve()
                        ?.run {
                            processAnnotation ?: containingClass?.processAnnotation
                        }
                        ?: return
                    when (annotation) {
                        ProcessAnnotations.MAIN_PROCESS_ONLY ->
                            node.checkCallIsInMainProcess()

                        ProcessAnnotations.PATCH_PROCESS_ONLY ->
                            node.checkCallIsInPatchProcess()

                        ProcessAnnotations.NON_PATCH_PROCESS_ONLY ->
                            node.checkCallIsInNonPatchProcess()
                    }
                }
            }
        }
    }

    context(JavaContext)
    private fun UCallExpression.checkCallIsInMainProcess() {
        if (isInValidMainProcessContext) {
            return
        }
        report(
            ISSUE_MAIN_PROCESS_ONLY,
            this,
            getLocation(this),
            """
                This member should only be called from a `@MainProcessOnly` annotated member, or
                when `Context.isInMainProcess` returns `true`.
            """.trimIndent().replace('\n', ' '),
        )
    }

    context(JavaContext)
    private val UCallExpression.isInValidMainProcessContext: Boolean
        get() = isInAnnotatedScope(ProcessAnnotations.MAIN_PROCESS_ONLY)
                || isCheckedBy(ProcessCheckProperties.IS_IN_MAIN_PROCESS, true)

    context(JavaContext)
    private fun UCallExpression.checkCallIsInPatchProcess() {
        if (isInValidPatchProcessContext) {
            return
        }
        report(
            ISSUE_PATCH_PROCESS_ONLY,
            this,
            getLocation(this),
            """
                This member should only be called from a `@PatchProcessOnly` annotated member, or
                when `Context.isInPatchProcess` returns `true`.
            """.trimIndent().replace('\n', ' '),
        )
    }

    context(JavaContext)
    private val UCallExpression.isInValidPatchProcessContext: Boolean
        get() = isInAnnotatedScope(ProcessAnnotations.PATCH_PROCESS_ONLY)
                || isCheckedBy(ProcessCheckProperties.IS_IN_PATCH_PROCESS, true)

    context(JavaContext)
    private fun UCallExpression.checkCallIsInNonPatchProcess() {
        if (isInValidNonPatchProcessContext) {
            return
        }
        report(
            ISSUE_NON_PATCH_PROCESS_ONLY,
            this,
            getLocation(this),
            """
                This member should only be called from a `@MainProcessOnly` annotated or
                `@NonPatchProcessOnly` annotated member, or when `Context.isInMainProcess` returns
                `true` or `Context.isInPatchProcess` returns `false`.
            """.trimIndent().replace('\n', ' '),
        )
    }

    context(JavaContext)
    private val UCallExpression.isInValidNonPatchProcessContext: Boolean
        get() = isInAnnotatedScope(ProcessAnnotations.NON_PATCH_PROCESS_ONLY)
                || isInAnnotatedScope(ProcessAnnotations.MAIN_PROCESS_ONLY)
                || isCheckedBy(ProcessCheckProperties.IS_IN_PATCH_PROCESS, false)
                || isCheckedBy(ProcessCheckProperties.IS_IN_MAIN_PROCESS, true)

    context(JavaContext)
    private fun UCallExpression.isInAnnotatedScope(expected: ProcessAnnotations): Boolean {
        caller?.run {
            if (processAnnotation == expected) {
                return true
            }
            clazz?.run {
                if (processAnnotation == expected) {
                    return true
                }
            }
        }
        return false
    }

    context(JavaContext)
    private fun UCallExpression.isCheckedBy(
        property: ProcessCheckProperties,
        expected: Boolean
    ): Boolean = isInCheckBranch(property, expected)
            || isInRemainingCodeOfReturnedCheck(property, expected)

    /**
     * Check if expression is in the if-check match branch. For example:
     *
     * ```
     * if (property == expected) {
     *     ...
     *     expression()
     *     ...
     * }
     * ```
     */
    context(JavaContext)
    private fun UCallExpression.isInCheckBranch(
        property: ProcessCheckProperties,
        expected: Boolean
    ): Boolean {
        var current: UElement = this
        while (true) {
            current
                .let { it as? UIfExpression }
                ?.apply {
                    val thenBranch = condition.booleanProperty(property)
                        ?.let { it == expected }
                        ?: return@apply
                    return if (thenBranch) {
                        thenExpression?.isAncestorOf(this@isInCheckBranch) == true
                    } else {
                        elseExpression?.isAncestorOf(this@isInCheckBranch) == true
                    }
                }
            current = current.uastParent ?: return false
        }
    }

    /**
     * Check if expression is in the remaining code of a returned check.
     *
     * For example, the remaining code of a if-check.
     *
     * ```
     * if (property != expected) {
     *     return  // Returned by return statement or throw statement.
     * }
     * ...
     * expression()  // Expression is in the remaining code of the check.
     * ```
     *
     * Or remaining code of a Kotlin condition check.
     *
     * ```
     * require(property == expected) {
     *     "Thrown exception message."
     * }
     * ...
     * expression()  // Expression is in the remaining code of the check.
     * ```
     */
    context(JavaContext)
    private tailrec fun UExpression.isInRemainingCodeOfReturnedCheck(
        property: ProcessCheckProperties,
        expected: Boolean
    ): Boolean {
        val blockParent = parentOfType<UBlockExpression>() ?: return false
        blockParent.expressions.forEach { expression ->
            if (expression === this) {
                return false
            }
            when (expression) {
                is UIfExpression -> {
                    val shouldCheckThenBranch =
                        expression.condition.booleanProperty(property) == !expected
                    if (!shouldCheckThenBranch) {
                        return@forEach
                    }
                    if (expression.thenExpression?.isTerminated != true) {
                        return@forEach
                    }
                }

                is UCallExpression -> {
                    val method = expression.resolveToUElement() as? UMethod
                        ?: return@forEach
                    if (method.clazz?.qualifiedName != "kotlin.PreconditionsKt__PreconditionsKt") {
                        return@forEach
                    }
                    if (method.name != "require" && expression.methodName != "check") {
                        return@forEach
                    }
                    val condition = expression.valueArguments.firstOrNull()
                        ?: return@forEach
                    if (condition.booleanProperty(property) != expected) {
                        return@forEach
                    }
                }

                else -> return@forEach
            }

            return true
        }
        return blockParent.isInRemainingCodeOfReturnedCheck(property, expected)
    }

    context(JavaContext)
    private val UExpression.isTerminated: Boolean
        get() = when (this) {
            is UReturnExpression, is UThrowExpression -> true

            is UIfExpression -> thenExpression?.isTerminated != false && elseExpression?.isTerminated != false

            is UBlockExpression -> expressions.any { it.isTerminated }

            else -> false
        }

    context(JavaContext)
    private fun UExpression.booleanProperty(property: ProcessCheckProperties): Boolean? {
        return when (this) {
            // Expressions like `if (property)`.
            is UQualifiedReferenceExpression -> selector.booleanProperty(property)

            // Expressions like `if (property)` in this-with scope.
            is USimpleNameReferenceExpression -> run {
                val method = resolveToUElement() as? UMethod
                    ?: return@run null
                val defineClassName = method.clazz?.qualifiedName
                    ?: return@run null
                if (defineClassName != ProcessCheckProperties.DEFINE_CLASS_NAME) {
                    return@run null
                }
                val receiverClassName = method.uastParameters.takeIf { it.size == 1 }
                    ?.first()
                    ?.typeReference
                    ?.getQualifiedName()
                    ?: return@run null
                if (receiverClassName != ProcessCheckProperties.RECEIVER_CLASS_NAME) {
                    return@run null
                }
                val methodName = method.name
                if (methodName != property.property) {
                    return@run null
                }
                return@run true
            }

            // Expressions like `if (!context.property)`.
            is UUnaryExpression -> takeIf { it.operator.text == "!" }
                ?.operand?.booleanProperty(property)
                ?.let { !it }

            // Expressions like `if (context.property == true)` or `(context.property || others)`.
            is UBinaryExpression -> run {
                when (operator) {
                    UastBinaryOperator.EQUALS -> {
                        leftOperand.booleanProperty(property)
                            ?.let { actual ->
                                val expected = rightOperand
                                    .let { it as? ULiteralExpression }
                                    ?.let { it.value as? Boolean }
                                    ?: return@run null
                                return@run expected == actual
                            }
                        rightOperand.booleanProperty(property)
                            ?.let { actual ->
                                val expected = leftOperand
                                    .let { it as? ULiteralExpression }
                                    ?.let { it.value as? Boolean }
                                    ?: return@run null
                                return@run expected == actual
                            }
                        return@run null
                    }

                    UastBinaryOperator.NOT_EQUALS -> {
                        leftOperand.booleanProperty(property)
                            ?.let { actual ->
                                val expected = rightOperand
                                    .let { it as? ULiteralExpression }
                                    ?.let { it.value as? Boolean }
                                    ?: return@run null
                                return@run expected != actual
                            }
                        rightOperand.booleanProperty(property)
                            ?.let { actual ->
                                val expected = leftOperand
                                    .let { it as? ULiteralExpression }
                                    ?.let { it.value as? Boolean }
                                    ?: return@run null
                                return@run expected != actual
                            }
                        return@run null
                    }

                    UastBinaryOperator.LOGICAL_AND -> {
                        if (leftOperand.booleanProperty(property) == false) {
                            return@run false
                        }
                        if (rightOperand.booleanProperty(property) == false) {
                            return@run false
                        }
                        return@run null
                    }

                    UastBinaryOperator.LOGICAL_OR -> {
                        if (leftOperand.booleanProperty(property) == true) {
                            return@run true
                        }
                        if (rightOperand.booleanProperty(property) == true) {
                            return@run true
                        }
                        return@run null
                    }

                    else -> {
                        return@run null
                    }
                }
            }

            else -> null
        }
    }

    private val UExpression.typeName: String?
        get() = getExpressionType()
            ?.let { it as? PsiClassType }
            ?.resolve()
            ?.qualifiedName

    private fun UElement.isAncestorOf(node: UElement): Boolean {
        var current = node
        while (true) {
            if (current == this) {
                return true
            }
            current = current.uastParent ?: return false
        }
    }
}