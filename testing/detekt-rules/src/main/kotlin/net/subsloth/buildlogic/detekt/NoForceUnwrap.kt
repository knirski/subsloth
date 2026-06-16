package net.subsloth.buildlogic.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtPostfixExpression

/**
 * Reports `!!` (force-unwrap) usage.
 *
 * Force-unwrap throws NullPointerException at runtime; the FC/IS
 * philosophy expects every fallible operation to be modelled as a
 * typed value (`Outcome<T>`, sealed `DomainError`, etc.) and handled
 * explicitly. Tests are expected to use `!!` to assert
 * preconditions, so this rule is wired up only against production
 * source sets in `config/detekt.yml`.
 */
public class NoForceUnwrap(config: Config) :
    Rule(
        config,
        description = "Force-unwrap (!!) is forbidden in production code; " +
            "use requireNotNull/checkNotNull or model the null case explicitly.",
    ) {

    override fun visitPostfixExpression(expression: KtPostfixExpression) {
        if (expression.operationToken == KtTokens.EXCLEXCL) {
            report(
                Finding(
                    entity = Entity.from(expression),
                    message = "Force-unwrap (!!) is forbidden in production code; " +
                        "use requireNotNull or checkNotNull with a message, " +
                        "fold the call into a typed error, or handle null at the type level.",
                ),
            )
        }
        super.visitPostfixExpression(expression)
    }
}
