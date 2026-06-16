package net.subsloth.buildlogic.detekt

import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

public class AppRuleSetProvider : RuleSetProvider {

    override val ruleSetId: RuleSetId = RuleSetId("subsloth")

    override fun instance(): RuleSet =
        RuleSet(
            id = ruleSetId,
            rules = mapOf(
                RuleName("NoFullyQualifiedNames") to ::NoFullyQualifiedNames,
                RuleName("NoForceUnwrap") to ::NoForceUnwrap,
            ),
        )
}
