package com.nextz.kanomtts.reader.engine

import com.nextz.kanomtts.text.replacement.ReplacementEngine
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** Applies a ReplacementEngine to every visible text node of an XHTML document (skips script/style). */
object ReplacementInjector {
    fun inject(html: String, engine: ReplacementEngine): String {
        val doc = Jsoup.parse(html, "", Parser.xmlParser())
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node !is TextNode) return
                val parent = (node.parent() as? Element)?.tagName()?.lowercase()
                if (parent == "script" || parent == "style") return
                val text = node.wholeText
                if (text.isBlank()) return
                val transformed = engine.apply(text)
                if (transformed != text) node.text(transformed)
            }
            override fun tail(node: Node, depth: Int) {}
        }, doc)
        return doc.outerHtml()
    }
}
