package net.subsloth.buildlogic.detekt

import dev.detekt.api.Config
import dev.detekt.api.Configuration
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Reports fully-qualified type references in source code.
 *
 * All types should be imported and referenced by their short name.
 * This rule detects dotted expressions and user types that follow a
 * package-like prefix (e.g. `com.example.Foo`) outside of import/package
 * directives.
 */
public class NoFullyQualifiedNames(config: Config) :
    Rule(
        config,
        description = "Fully-qualified names should be replaced with imports.",
    ) {

    @Configuration("Package prefixes that are allowed to use fully qualified.")
    private val allowedPrefixes: List<String> by config(defaultValue = DEFAULT_ALLOWED)

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        val fqnExpression = findFqnExpression(expression)
        val target = fqnExpression ?: expression
        if (expression.getStrictParentOfType<KtImportDirective>() == null &&
            expression.getStrictParentOfType<KtPackageDirective>() == null &&
            !expression.isNestedReceiver() &&
            expression.isPackageQualified() &&
            !isAllowed(target.text)
        ) {
            report(
                Finding(
                    entity = entityAt(target),
                    message = "Use imports instead of '${target.text}'.",
                ),
            )
        }
        super.visitDotQualifiedExpression(expression)
    }

    override fun visitUserType(type: KtUserType) {
        if (type.parent !is KtUserType &&
            type.isPackageQualified() &&
            !isAllowed(type.text)
        ) {
            report(
                Finding(
                    entity = entityAt(type),
                    message = "Use imports instead of '${type.text}'.",
                ),
            )
        }
        super.visitUserType(type)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Creates a detekt [Entity] from a PSI element for error reporting. */
    private fun entityAt(element: KtElement): Entity = Entity.from(element)

    /**
     * Walks the receiver chain to find the sub-expression that represents
     * just the fully-qualified type name, excluding any method calls.
     *
     * For example, given `java.nio.file.Paths.get(path).normalize().toString()`,
     * returns the sub-expression for `java.nio.file.Paths`.
     * The type-name sub-expression is identified by having a selector whose
     * text starts with an uppercase letter (type-like identifier).
     *
     * Returns `null` when no type-like selector is found in the chain.
     */
    private fun findFqnExpression(expression: KtDotQualifiedExpression): KtDotQualifiedExpression? {
        var current: KtExpression = expression
        while (current is KtDotQualifiedExpression) {
            val selector = current.selectorExpression
            when {
                selector is KtReferenceExpression && selector.text.isTypeLikeIdentifier() -> return current
                selector is KtCallExpression -> {
                    val callee = selector.calleeExpression
                    if (callee is KtReferenceExpression && callee.text.isTypeLikeIdentifier()) {
                        return current
                    }
                }
            }
            current = current.receiverExpression
        }
        return null
    }

    /** Returns `true` when this expression is the receiver of a parent [KtDotQualifiedExpression]. */
    private fun KtDotQualifiedExpression.isNestedReceiver(): Boolean {
        val parent = parent as? KtDotQualifiedExpression ?: return false
        return parent.receiverExpression == this
    }

    /** Returns `true` when the receiver chain of this expression looks like a package-qualified name. */
    private fun KtDotQualifiedExpression.isPackageQualified(): Boolean =
        hasPackageLikePrefix(receiverSegments(this))

    /** Returns `true` when the qualifier chain of this type looks like a package-qualified name. */
    private fun KtUserType.isPackageQualified(): Boolean {
        if (qualifier == null) return false
        return hasPackageLikePrefix(typeSegments(this))
    }

    /**
     * Extracts package-segments from the receiver chain of a dot-qualified expression.
     *
     * For `java.nio.file.Paths.get(path)`, returns `["java", "nio", "file", "Paths"]`.
     * Method-call and property-access selectors are excluded from the result.
     */
    private fun receiverSegments(expression: KtDotQualifiedExpression): List<String> {
        val segments = mutableListOf<String>()
        var current: KtExpression = expression
        while (current is KtQualifiedExpression) {
            val receiver =
                try {
                    current.receiverExpression
                } catch (_: IllegalStateException) {
                    null
                } ?: break
            val selector = current.selectorExpression
            when (selector) {
                is KtReferenceExpression -> segments.add(selector.text)
                is KtCallExpression -> {
                    val callee = selector.calleeExpression
                    if (callee is KtReferenceExpression) {
                        segments.add(callee.text)
                    }
                }
            }
            current = receiver
        }
        val rootReference = current as? KtReferenceExpression ?: return emptyList()
        segments.add(rootReference.text)
        return segments.asReversed()
    }

    /**
     * Extracts qualifier segments from a [KtUserType].
     *
     * For `java.util.List`, returns `["java", "util", "List"]`.
     */
    private fun typeSegments(type: KtUserType): List<String> {
        val segments = mutableListOf<String>()
        var current: KtUserType? = type
        while (current != null) {
            val reference = current.referenceExpression?.text
            if (reference != null) {
                segments.add(reference)
            }
            current = current.qualifier
        }
        return segments.asReversed()
    }

    /**
     * Returns `true` when the segment list has a package-like prefix (e.g. `com.example`)
     * followed by a type-like segment (e.g. `Foo`), or starts with a known root package
     * such as `java`, `kotlin`, or `com`.
     */
    private fun hasPackageLikePrefix(segments: List<String>): Boolean {
        if (segments.size < 2) return false
        if (!segments[0].isLowercaseIdentifier() || !segments[1].isLowercaseIdentifier()) {
            return false
        }
        if (segments.any { it.isTypeLikeIdentifier() }) {
            return true
        }
        return segments[0] in ROOT_PACKAGE_PREFIXES
    }

    /** Returns `true` when [text] is an exact match or starts with one of the [allowedPrefixes] config values. */
    private fun isAllowed(text: String): Boolean {
        val trimmed = text.trim()
        return allowedPrefixes.any { prefix ->
            trimmed == prefix || trimmed.startsWith("$prefix.")
        }
    }

    /** Returns `true` when the string is a lower-case identifier (starts with lower-case letter). */
    private fun String.isLowercaseIdentifier(): Boolean {
        if (isEmpty() || !first().isLowerCase()) return false
        return all { it == '_' || it.isLowerCase() || it.isDigit() }
    }

    /** Returns `true` when the string looks like a type name (starts with upper-case letter). */
    private fun String.isTypeLikeIdentifier(): Boolean =
        isNotEmpty() && first().isUpperCase()

    private companion object {
        private val DEFAULT_ALLOWED: List<String> = emptyList()
        private val ROOT_PACKAGE_PREFIXES = setOf(
            "java", "javax", "kotlin", "kotlinx",
            "io", "org", "com", "net",
        )
    }
}
