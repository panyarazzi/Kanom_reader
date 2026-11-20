package com.nextz.kanomtts.text.pipeline

object ScriptRunSplitter {

    /** Splits [input] into maximal runs of a single non-neutral script.
     *  NEUTRAL code points (spaces, digits, punctuation) attach to the current
     *  open run; a leading neutral prefix attaches to the first real run. */
    fun split(input: String): List<ScriptRun> {
        if (input.isEmpty()) return emptyList()
        val runs = mutableListOf<ScriptRun>()
        var runStart = 0
        var runScript: Script? = null   // null = only neutrals seen so far in this run
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val charCount = Character.charCount(cp)
            val s = scriptOf(cp)
            if (s != Script.NEUTRAL) {
                if (runScript == null) {
                    runScript = s
                } else if (s != runScript) {
                    // close current run [runStart, i) and open a new one at i
                    runs += ScriptRun(input.substring(runStart, i), runScript, runStart, i)
                    runStart = i
                    runScript = s
                }
            }
            i += charCount
        }
        val finalScript = runScript ?: Script.NEUTRAL
        runs += ScriptRun(input.substring(runStart, input.length), finalScript, runStart, input.length)
        return runs
    }
}
