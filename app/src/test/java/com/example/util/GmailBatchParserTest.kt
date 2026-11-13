package com.example.util

import com.example.data.model.VmSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailBatchParserTest {

    @Test
    fun parseUniqueEmails_deduplicatesAndFiltersInvalid() {
        val raw = """
            alice01@gmail.com
            not-an-email
            ALICE01@gmail.com
            bob02@gmail.com, charlie03@googlemail.com; dave04@example.org
        """.trimIndent()

        val parsed = GmailBatchParser.parseUniqueEmails(raw)
        assertEquals(
            listOf(
                "alice01@gmail.com",
                "bob02@gmail.com",
                "charlie03@googlemail.com",
                "dave04@example.org"
            ),
            parsed
        )
        assertTrue(GmailBatchParser.isValidEmail("user+01@gmail.com"))
        assertFalse(GmailBatchParser.isValidEmail("invalid@@gmail"))
    }

    @Test
    fun mapEmailsToVms_mapsSequentiallyFromStartSlot() {
        val slots = (1..100).map { i ->
            VmSlot(vmId = "VM-%03d".format(i), slotNumber = i.toLong())
        }.shuffled()

        val emails = listOf("u21@gmail.com", "u22@gmail.com", "u23@gmail.com")
        val mapping = GmailBatchParser.mapEmailsToVms(emails, slots, startSlotNumber = 21)

        assertEquals(3, mapping.size)
        assertEquals("u21@gmail.com", mapping["VM-021"])
        assertEquals("u22@gmail.com", mapping["VM-022"])
        assertEquals("u23@gmail.com", mapping["VM-023"])
    }
}
