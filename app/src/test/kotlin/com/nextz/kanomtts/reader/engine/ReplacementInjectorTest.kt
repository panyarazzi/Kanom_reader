package com.nextz.kanomtts.reader.engine

import com.nextz.kanomtts.text.replacement.ReplacementEngine
import com.nextz.kanomtts.text.replacement.ReplacementRule
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementInjectorTest {
    private fun engine(vararg r: ReplacementRule) = ReplacementEngine(r.toList())

    @Test fun `replaces text node content`() {
        val out = ReplacementInjector.inject("<html><body><p>hello world</p></body></html>",
            engine(ReplacementRule("hello", "hi", showInReader = true)))
        assertTrue(out.contains("hi world"))
        assertTrue(!out.contains("hello world"))
    }
    @Test fun `skips script and style`() {
        val html = "<html><body><script>hello</script><style>hello</style><p>hello</p></body></html>"
        val out = ReplacementInjector.inject(html, engine(ReplacementRule("hello", "hi", showInReader = true)))
        assertTrue(out.contains("<script>hello</script>") || out.contains("hello</script>"))
        assertTrue(out.contains("hi</p>") || out.contains(">hi<"))
    }
    @Test fun `no-op engine leaves text unchanged`() {
        val html = "<html><body><p>keep me</p></body></html>"
        assertEquals(true, ReplacementInjector.inject(html, engine()).contains("keep me"))
    }
}
