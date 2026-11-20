package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkSnippetTest {

    @Test fun `strips ZWSP characters`() {
        // U+200B injected by the Thai word-break transform must be removed.
        val raw = "สวัสดี​ชาว​โลก"
        assertEquals("สวัสดีชาวโลก", cleanSnippet(raw))
    }

    @Test fun `collapses whitespace runs to single spaces and trims`() {
        val raw = "  hello   \n\t world  \n"
        assertEquals("hello world", cleanSnippet(raw))
    }

    @Test fun `returns empty string for blank input`() {
        assertEquals("", cleanSnippet("   \n​  "))
    }

    @Test fun `passes through short clean text unchanged`() {
        assertEquals("a spoken sentence", cleanSnippet("a spoken sentence"))
    }

    @Test fun `caps over-length text and appends single-char ellipsis`() {
        val raw = "ก".repeat(200)
        val out = cleanSnippet(raw)
        // 100 content chars + 1 ellipsis char (U+2026).
        assertEquals(101, out.length)
        assertEquals('…', out.last())
        assertEquals("ก".repeat(100), out.dropLast(1))
    }

    @Test fun `does not append ellipsis at exactly the max length`() {
        val raw = "ก".repeat(100)
        val out = cleanSnippet(raw)
        assertEquals(100, out.length)
        assertEquals("ก".repeat(100), out)
    }

    @Test fun `normalizes before capping so whitespace does not inflate length`() {
        // 60 chars + many spaces + 60 chars -> collapses to "...a... ...b..." under 100.
        val raw = "a".repeat(50) + "      " + "b".repeat(40)
        val out = cleanSnippet(raw)
        assertEquals("a".repeat(50) + " " + "b".repeat(40), out)
    }
}
