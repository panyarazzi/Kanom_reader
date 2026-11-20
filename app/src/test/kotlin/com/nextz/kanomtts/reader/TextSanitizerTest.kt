package com.nextz.kanomtts.reader
import org.junit.Assert.assertEquals
import org.junit.Test
class TextSanitizerTest {
    @Test fun `strips zero-width spaces`() {
        assertEquals("กรุงเทพมหานคร", stripZwsp("กรุง​เทพ​มหานคร"))
    }
    @Test fun `leaves ordinary text and whitespace`() {
        assertEquals("hello world", stripZwsp("hello world"))
    }
    @Test fun `empty stays empty`() {
        assertEquals("", stripZwsp(""))
    }
}
