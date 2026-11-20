package com.nextz.kanomtts.text.replacement

/** Trie-based longest-match lookup at a given position. (Goto + output is enough
 *  for our "match at index" query; full failure links aren't needed because the
 *  engine advances the cursor past each match itself.) */
class AhoCorasick(literals: List<String>) {
    private class Node {
        val next = HashMap<Char, Node>()
        var wordLen = -1 // length of a keyword ending here, or -1
    }
    private val root = Node()

    init {
        for (w in literals) if (w.isNotEmpty()) {
            var n = root
            for (c in w) n = n.next.getOrPut(c) { Node() }
            if (n.wordLen < w.length) n.wordLen = w.length
        }
    }

    // Indexing is by UTF-16 code unit (Char), not code point. This stays correct:
    // keyword and haystack are walked the same way and matched substrings are
    // returned verbatim, so supplementary-plane literals match byte-for-byte.
    /** Longest keyword length matching starting exactly at [index], else -1. */
    fun firstMatchAt(text: String, index: Int): Int {
        var n = root
        var i = index
        var best = -1
        while (i < text.length) {
            n = n.next[text[i]] ?: break
            if (n.wordLen > best) best = n.wordLen
            i++
        }
        return best
    }
}
