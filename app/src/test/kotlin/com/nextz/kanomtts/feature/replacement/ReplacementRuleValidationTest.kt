package com.nextz.kanomtts.feature.replacement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplacementRuleValidationTest {
    @Test fun `blank pattern is EmptyPattern`() {
        assertEquals(RuleError.EmptyPattern, ReplacementRuleValidation.validate("  ", isRegex = false))
        assertEquals(RuleError.EmptyPattern, ReplacementRuleValidation.validate("", isRegex = true))
    }
    @Test fun `valid literal and regex pass`() {
        assertNull(ReplacementRuleValidation.validate("กรุงเทพ", isRegex = false))
        assertNull(ReplacementRuleValidation.validate("\\d+", isRegex = true))
    }
    @Test fun `invalid regex is InvalidRegex but only when isRegex`() {
        assertEquals(RuleError.InvalidRegex, ReplacementRuleValidation.validate("[unclosed", isRegex = true))
        assertNull(ReplacementRuleValidation.validate("[unclosed", isRegex = false)) // literal: any string is valid
    }
}
