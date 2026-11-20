package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.text.replacement.ReplacementRule
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleMapperTest {
    @Test fun `toEntity maps order to position and carries scope`() {
        val domain = ReplacementRule(pattern = "p", replacement = "r", isRegex = true, enabled = false, order = 4)
        val entity = RuleMapper.toEntity(domain, scope = "book", bookId = "b1", id = 7)
        assertEquals(7L, entity.id)
        assertEquals("book", entity.scope)
        assertEquals("b1", entity.bookId)
        assertEquals(4, entity.position)
        assertEquals(true, entity.isRegex)
        assertEquals(false, entity.enabled)
    }
    @Test fun `toDomain then toEntity round-trips the rule fields`() {
        val entity = ReplacementRuleEntity(id = 1, scope = "global", bookId = null,
            pattern = "p", replacement = "r", isRegex = false, enabled = true, position = 3)
        val back = RuleMapper.toEntity(RuleMapper.toDomain(entity), scope = "global", bookId = null, id = 1)
        assertEquals(entity, back)
    }
    @Test fun `toEntity and toDomain carry showInReader`() {
        val domain = com.nextz.kanomtts.text.replacement.ReplacementRule("p", "r", isRegex = false, enabled = true, order = 0, showInReader = true)
        val entity = RuleMapper.toEntity(domain, scope = "global", bookId = null)
        assertEquals(true, entity.showInReader)
        assertEquals(true, RuleMapper.toDomain(entity).showInReader)
    }
}
