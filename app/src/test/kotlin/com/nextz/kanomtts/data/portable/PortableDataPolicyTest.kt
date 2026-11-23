package com.nextz.kanomtts.data.portable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class PortableDataPolicyTest {
    @Test fun `only metadata and cover entries are restored from zip archives`() {
        assertTrue(isRestorablePortableEntry("data.json"))
        assertTrue(isRestorablePortableEntry("covers/book-1.bin"))
        assertFalse(isRestorablePortableEntry("books/book-1.epub"))
        assertFalse(isRestorablePortableEntry("Kanom/books/book-1.epub"))
        assertFalse(isRestorablePortableEntry("portableEpub/book-1.epub"))
        assertFalse(isRestorablePortableEntry("unknown/payload.bin"))
    }
}
