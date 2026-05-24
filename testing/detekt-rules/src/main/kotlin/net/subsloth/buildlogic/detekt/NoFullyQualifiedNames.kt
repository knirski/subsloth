package net.subsloth.buildlogic.detekt

import dev.detekt.api.Config
import dev.detekt.api.Configuration
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
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
        if (expression.getStrictParentOfType<KtImportDirective>() == null &&
            expression.getStrictParentOfType<KtPackageDirective>() == null &&
            !expression.isNestedReceiver() &&
            expression.isPackageQualified() &&
            !isAllowed(expression.text)
        ) {
            report(
                Finding(
                    entity = entityAt(expression),
                    message = "Use imports instead of '${expression.text}'.",
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

    private fun entityAt(element: KtElement): Entity = Entity.from(element)

    private fun KtDotQualifiedExpression.isNestedReceiver(): Boolean {
        val parent = parent as? KtDotQualifiedExpression ?: return false
        return parent.receiverExpression == this
    }

    private fun KtDotQualifiedExpression.isPackageQualified(): Boolean =
        hasPackageLikePrefix(receiverSegments(this))

    private fun KtUserType.isPackageQualified(): Boolean {
        if (qualifier == null) return false
        return hasPackageLikePrefix(typeSegments(this))
    }

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
            val selectorReference = current.selectorExpression as? KtReferenceExpression
            if (selectorReference != null) {
                segments.add(selectorReference.text)
            }
            current = receiver
        }
        val rootReference = current as? KtReferenceExpression ?: return emptyList()
        segments.add(rootReference.text)
        return segments.asReversed()
    }

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

    private fun isAllowed(text: String): Boolean {
        val trimmed = text.trim()
        return allowedPrefixes.any { prefix ->
            trimmed == prefix || trimmed.startsWith("$prefix.")
        }
    }

    private fun String.isLowercaseIdentifier(): Boolean {
        if (isEmpty() || !first().isLowerCase()) return false
        return all { it == '_' || it.isLowerCase() || it.isDigit() }
    }

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
