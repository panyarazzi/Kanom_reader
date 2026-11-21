package com.nextz.kanomtts.reader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class HighlightAnchorMatcherTest {
    @Test fun exactUniqueQuoteMatches() {
        assertTrue(HighlightAnchorMatcher.hasUnambiguousExactMatch("ต้นข้อความที่ต้องการไฮไลท์ท้ายข้อความ","ข้อความที่ต้องการไฮไลท์","ต้น","ท้าย"))
    }
    @Test fun approximateOrChangedQuoteDoesNotMatch() {
        assertFalse(HighlightAnchorMatcher.hasUnambiguousExactMatch("ข้อความที่เปลี่ยนไปเล็กน้อย","ข้อความที่ต้องการไฮไลท์",null,null))
    }
    @Test fun duplicateQuoteRequiresUniqueContext() {
        assertTrue(HighlightAnchorMatcher.hasUnambiguousExactMatch("ก่อนชุดหนึ่งคำซ้ำหลังชุดหนึ่ง กลางข้อความคำซ้ำท้ายอีกชุด","คำซ้ำ","ก่อนชุดหนึ่ง","หลังชุดหนึ่ง"))
    }
    @Test fun duplicateQuoteWithAmbiguousContextFailsClosed() {
        assertFalse(HighlightAnchorMatcher.hasUnambiguousExactMatch("ก่อนคำซ้ำหลัง ก่อนคำซ้ำหลัง","คำซ้ำ",null,null))
    }
    @Test fun zeroWidthSpacesRemainPartOfTheAnchor() {
        assertTrue(HighlightAnchorMatcher.hasUnambiguousExactMatch("กรุง​เทพมหานคร","กรุง​เทพ",null,null))
        assertFalse(HighlightAnchorMatcher.hasUnambiguousExactMatch("กรุงเทพมหานคร","กรุง​เทพ",null,null))
    }
}
