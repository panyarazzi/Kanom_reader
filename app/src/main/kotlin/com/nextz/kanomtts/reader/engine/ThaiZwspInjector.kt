package com.nextz.kanomtts.reader.engine

import com.nextz.kanomtts.text.pipeline.WordTokenizer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * Inserts U+200B at ICU Thai word boundaries inside text nodes only (skip tags/attrs/script/style),
 * so the WebView can line-break Thai (Blink does not dictionary-break Thai from lang alone — recon §6).
 * Idempotent: a boundary already preceded by U+200B is not doubled.
 */
object ThaiZwspInjector {
    private const val ZWSP = '​'

    fun inject(html: String): String {
        val doc = Jsoup.parse(html, "", Parser.xmlParser())
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node !is TextNode) return
                val parent = (node.parent() as? Element)?.tagName()?.lowercase()
                if (parent == "script" || parent == "style") return
                val text = node.wholeText
                if (text.isBlank()) return
                val transformed = injectText(text)
                if (transformed != text) node.text(transformed)
            }
            override fun tail(node: Node, depth: Int) {}
        }, doc)
        return doc.outerHtml()
    }

    /**
     * Injects ZWSP into a plain text string (not HTML) at the same Thai word boundaries the
     * display DOM uses. Lets a TTS highlight quote match the ZWSP-peppered rendered text, so
     * Readium's TextQuoteAnchor search (used for both page-turn scroll and the decoration)
     * succeeds — otherwise the clean quote fails to match and navigation falls back to the
     * paragraph start (wrong page). Mirror this with the [thaiWordBreak] pref at the call site.
     *
     * IMPORTANT: inject over the SAME contiguous text the DOM tokenises (a whole text node), not
     * per-fragment — ICU Thai boundaries are context-sensitive, so tokenising a substring yields
     * different ZWSP positions and the quote fails to match. Use [injectTextMapped] when you need
     * to re-split the result (before/highlight/after) at original character offsets.
     */
    fun injectText(text: String): String = injectTextMapped(text).first

    /**
     * Like [injectText] but also returns a map from each ORIGINAL character index to its index in
     * the injected string (size = text.length + 1, so map[length] = injected length). Lets a caller
     * inject over a full string and then re-split it at original offsets with ZWSP-correct positions.
     */
    fun injectTextMapped(text: String): Pair<String, IntArray> {
        val map = IntArray(text.length + 1)
        val ranges = WordTokenizer.tokenize(text)
        if (ranges.size < 2) {
            for (i in map.indices) map[i] = i
            return text to map
        }
        // Interior boundaries: the end (exclusive) of each range except the last
        val boundaries = ranges.dropLast(1).mapTo(mutableSetOf()) { it.last + 1 }
        val sb = StringBuilder(text.length + ranges.size)
        for (i in text.indices) {
            map[i] = sb.length
            sb.append(text[i])
            if ((i + 1) in boundaries) {
                // Only inject ZWSP when both adjacent characters are Thai script
                val prevChar = text[i]
                val nextChar = if (i + 1 < text.length) text[i + 1] else null
                if (prevChar.isThai() && nextChar?.isThai() == true && prevChar != ZWSP && nextChar != ZWSP) {
                    sb.append(ZWSP)
                }
            }
        }
        map[text.length] = sb.length
        return sb.toString() to map
    }

    private fun Char.isThai(): Boolean = this in '฀'..'๿'
}
