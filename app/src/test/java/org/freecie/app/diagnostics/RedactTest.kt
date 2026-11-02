package org.freecie.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactTest {

    private val opId = "_0123456789abcdef0123456789abcdef"
    private val challenge = "d%2FyXSe%2Fj9P%2BnNJXbmIcnsQJ76pK"
    private val qr = "https://idserver.servizicie.interno.gov.it/idp/login/livello3QR" +
        "?opId=$opId&challenge=$challenge&level=3&SPName=https%3A%2F%2Fiam.example.it%2Fshibboleth"

    @Test
    fun qrKeepsShapeAndLevelButNoValues() {
        val redacted = Redact.qr(qr)
        assertEquals(
            "https://idserver.servizicie.interno.gov.it/idp/login/livello3QR" +
                "?opId=<${opId.length} caratteri>&challenge=<${challenge.length} caratteri>&level=3&SPName=<41 caratteri>",
            redacted,
        )
        for (secret in listOf(opId, challenge, "iam.example.it")) assertFalse(secret, secret in redacted)
    }

    @Test
    fun qrWithRawSpacesAndNoQuery() {
        assertEquals(
            "https://idserver.servizicie.interno.gov.it/idp/QR?opText=<15 caratteri>&opId=<4 caratteri>",
            Redact.qr("https://idserver.servizicie.interno.gov.it/idp/QR?opText=Accesso con CIE&opId=_abc"),
        )
        assertEquals("https://example.it/path", Redact.qr("https://example.it/path"))
    }

    @Test
    fun apduCommandShowsHeaderAndName() {
        val verify = byteArrayOf(0x0C, 0x20, 0x00, 0x81.toByte())
        assertEquals("→ 0C 20 00 81  VERIFY PIN (messaggistica sicura), 29 byte", Redact.apduCommand(verify, 29))
        assertTrue(Redact.isVerifyPin(verify))
        assertTrue(Redact.isVerifyPin(byteArrayOf(0x00, 0x20, 0x00, 0x81.toByte())))
        assertFalse(Redact.isVerifyPin(byteArrayOf(0x00, 0x20, 0x00, 0x82.toByte())))
        assertFalse(Redact.isVerifyPin(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x81.toByte())))
        assertEquals(
            "→ 1C 2A 00 AE  PSO VERIFY CERTIFICATE (messaggistica sicura, concatenato), 255 byte",
            Redact.apduCommand(byteArrayOf(0x1C, 0x2A, 0x00, 0xAE.toByte()), 255),
        )
    }

    @Test
    fun apduResponseExplainsStatus() {
        assertEquals("← SW 9000 (OK), 12 byte di dati", Redact.apduResponse(12, byteArrayOf(0x90.toByte(), 0x00)))
        assertEquals("← SW 63C2 (2 tentativi rimasti), 0 byte di dati", Redact.apduResponse(0, byteArrayOf(0x63, 0xC2.toByte())))
        assertEquals("← SW 6983 (metodo di autenticazione bloccato), 0 byte di dati", Redact.apduResponse(0, byteArrayOf(0x69, 0x83.toByte())))
    }

    @Test
    fun pinStatusIncludesTheSdkSignExtendedForms() {
        assertEquals("PIN corretto", Redact.pinStatus("9000"))
        assertEquals("PIN errato, 2 tentativi rimasti", Redact.pinStatus("ffc2"))
        assertEquals("PIN errato, 1 tentativo rimasto", Redact.pinStatus("63C1"))
        assertEquals("PIN bloccato", Redact.pinStatus("ff83"))
        assertEquals("stato inatteso", Redact.pinStatus("6982"))
        assertTrue(Redact.pinStatusMeansBlocked("6983"))
        assertTrue(Redact.pinStatusMeansBlocked("FFC0"))
        assertFalse(Redact.pinStatusMeansBlocked("6982"))
        assertFalse(Redact.pinStatusMeansBlocked(null))
    }

    @Test
    fun responseBodyNeverShowsTheOneTimeCode() {
        val redacted = Redact.responseBody("codice:4815")
        assertFalse(redacted, "4815" in redacted)
        assertTrue(redacted, "codice monouso" in redacted)
        assertEquals("(vuoto)", Redact.responseBody(""))
    }

    @Test
    fun responseBodyKeepsServerErrorsButScrubsTokens() {
        assertEquals(
            "31 caratteri: \"Operazione scaduta\"",
            Redact.responseBody("""{"error:":"Operazione scaduta"}"""),
        )
        val html = "<html>Errore sessione JSESSIONID=ABCDEF0123456789ABCDEF codice 123456</html>"
        val redacted = Redact.responseBody(html)
        assertFalse(redacted, "ABCDEF0123456789ABCDEF" in redacted)
        assertFalse(redacted, "123456" in redacted)
        assertTrue(redacted, "Errore sessione" in redacted)
    }

    @Test
    fun throwableChainIsScrubbed() {
        val e = RuntimeException("outer", java.io.IOException("Transceive failed for card 0123456789"))
        assertEquals("RuntimeException: outer ← IOException: Transceive failed for card ##########", Redact.throwable(e))
    }
}
