package com.nextz.kanomtts.text.pipeline

object HanDisambiguator {

    fun assign(runs: List<ScriptRun>, primary: Lang): List<LangRun> {
        if (runs.isEmpty()) return emptyList()
        val hasKana = runs.any { it.script == Script.HIRAGANA || it.script == Script.KATAKANA }
        // A book that mixes kana with Han is Japanese; otherwise lone Han follows the
        // primary hint (a Chinese book stays ZH, a Japanese book stays JA).
        val hanLang = when {
            hasKana -> Lang.JA
            primary == Lang.JA -> Lang.JA
            else -> Lang.ZH
        }
        val mapped = runs.map { run ->
            val lang = when (run.script) {
                Script.THAI -> Lang.TH
                Script.LATIN -> Lang.EN
                Script.HIRAGANA, Script.KATAKANA -> Lang.JA
                Script.HAN -> hanLang
                Script.NEUTRAL -> primary
            }
            LangRun(run.text, lang, run.start, run.end)
        }
        return mergeAdjacent(mapped)
    }

    private fun mergeAdjacent(runs: List<LangRun>): List<LangRun> {
        val out = mutableListOf<LangRun>()
        for (r in runs) {
            val last = out.lastOrNull()
            if (last != null && last.lang == r.lang && last.end == r.start) {
                out[out.size - 1] = last.copy(text = last.text + r.text, end = r.end)
            } else {
                out += r
            }
        }
        return out
    }
}
