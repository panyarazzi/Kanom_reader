package com.nextz.kanomtts.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadiumMetadataExtractorTest {
    @Test fun fallbackTitleRemovesEpubExtensionWithoutCaseSensitivity() {
        assertEquals("Novel", fallbackEpubTitle("content://provider/Novel.EPUB"))
        assertEquals("Novel", fallbackEpubTitle("content://provider/Novel.epub?download=1"))
    }

    @Test fun fallbackTitleUsesUnknownForEmptyFilename() {
        assertEquals("Unknown", fallbackEpubTitle("content://provider/.EPUB"))
        assertEquals("Unknown", fallbackEpubTitle("content://provider/"))
    }
}
