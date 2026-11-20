package com.nextz.kanomtts.reader
/** Remove U+200B (zero-width space) injected by ThaiZwspInjector before any text-consuming action. */
fun stripZwsp(text: String): String = text.replace("​", "")
