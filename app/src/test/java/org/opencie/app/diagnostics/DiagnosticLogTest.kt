package org.opencie.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Date

class DiagnosticLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val fixedTime = Date(1_793_450_000_000)

    @Test
    fun persistsAndParsesEntries() {
        val dir = tmp.newFolder("logs")
        DiagnosticLog(dir, now = { fixedTime }).apply {
            add(Kind.PIN, ">>> PIN SENT to the card")
            add(Kind.PIN_OK, "✔ PIN ACCEPTED")
            add(Kind.HTTP, "POST https://idserver.servizicie.interno.gov.it/idp/Authn/SSL/X509AuthIbrido")
            add(Kind.ERROR, "multi\nline")
        }
        // A new instance (e.g. after an app restart) sees the same log.
        val entries = DiagnosticLog(dir).entries()
        assertEquals(listOf(Kind.PIN, Kind.PIN_OK, Kind.HTTP, Kind.ERROR), entries.map { it.kind })
        assertEquals(">>> PIN SENT to the card", entries[0].message)
        assertEquals("POST https://idserver.servizicie.interno.gov.it/idp/Authn/SSL/X509AuthIbrido", entries[2].message)
        assertEquals("multi ⏎ line", entries[3].message)
        assertTrue(entries.all { it.time.length == 23 })
    }

    @Test
    fun rotatesKeepingOnePreviousFile() {
        val dir = tmp.newFolder("logs")
        val log = DiagnosticLog(dir, maxBytes = 200)
        repeat(30) { log.add(Kind.INFO, "event $it ".padEnd(40, '.')) }
        val text = log.text()
        assertTrue("newest kept", "event 29 " in text)
        assertFalse("oldest dropped", "event 0 " in text)
        assertTrue(dir.resolve("opencie.log.1").exists())
        assertTrue(text.length < 600)
    }

    @Test
    fun clearRemovesEverything() {
        val dir = tmp.newFolder("logs")
        val log = DiagnosticLog(dir, maxBytes = 100)
        repeat(10) { log.add(Kind.INFO, "x".repeat(40)) }
        log.clear()
        assertEquals("", log.text())
        assertTrue(log.entries().isEmpty())
    }

    @Test
    fun unknownLinesAreKeptVerbatim() {
        assertEquals(LogEntry("", null, "garbage"), DiagnosticLog.parseLine("garbage"))
        val line = "2026-10-31 14:05:12.345  PIN-KO  ✘ PIN REJECTED by the card: 2 attempt(s) left"
        assertEquals(
            LogEntry("2026-10-31 14:05:12.345", Kind.PIN_KO, "✘ PIN REJECTED by the card: 2 attempt(s) left"),
            DiagnosticLog.parseLine(line),
        )
    }

    @Test
    fun neverWritesEightDigitRuns() {
        val dir = tmp.newFolder("logs")
        val log = DiagnosticLog(dir)
        log.add(Kind.INFO, "x 12345678 y 1234567890 SW 9000 port 443 ip 93.184.216.34")
        val text = log.text()
        assertFalse(text, "12345678" in text)
        assertTrue(text, "x ######## y ########## SW 9000 port 443 ip 93.184.216.34" in text)
    }

    @Test
    fun readsTagsWrittenByEarlierVersions() {
        assertEquals(Kind.ERROR, DiagnosticLog.parseLine("2026-10-31 14:05:12.345  ERROR   boom").kind)
        assertEquals(Kind.WARN, DiagnosticLog.parseLine("2026-10-31 14:05:12.345  WARN    hmm").kind)
        assertEquals(Kind.STEP, DiagnosticLog.parseLine("2026-10-31 14:05:12.345  STEP    go").kind)
        assertEquals(Kind.ERROR, DiagnosticLog.parseLine("2026-10-31 14:05:12.345  ERRORE  boom").kind)
    }
}
