package org.opencie.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class HybridAuthTest {

    private val request = QrLoginRequest.parse(
        "https://idserver.servizicie.interno.gov.it/idp/login/livello3QR?opId=_abc123&level=3&SPName=x",
    )

    @Test
    fun signsSha256DigestInfoOfTheExactQrText() {
        val data = HybridAuth.dataToSign(request)
        val prefix = "3031300d060960864801650304020105000420".hexToBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(request.raw.toByteArray(Charsets.UTF_8))
        assertEquals(51, data.size)
        assertArrayEquals(prefix + digest, data)
    }

    @Test
    fun formBodyUsesUpperCaseHexAndRawOpId() {
        val body = HybridAuth.formBody(request, byteArrayOf(0x00, 0x0a, 0xff.toByte()))
        assertEquals("hashJsonFirmato=000AFF&opId=_abc123", body)
    }

    @Test
    fun parsesOneTimeCode() {
        assertEquals(HybridAuth.Result.Otp("4815"), HybridAuth.parseResponse("codice:4815"))
        assertEquals(HybridAuth.Result.Otp("0042"), HybridAuth.parseResponse("OK:0042:"))
    }

    @Test
    fun rejectsMalformedCodes() {
        for (body in listOf("codice:12345", "codice: 1234", "codice:12a4", "1234", "")) {
            val result = HybridAuth.parseResponse(body)
            assert(result is HybridAuth.Result.Refused) { "$body -> $result" }
        }
    }

    @Test
    fun extractsServerErrorMessage() {
        assertEquals(
            HybridAuth.Result.Refused("Operazione scaduta"),
            HybridAuth.parseResponse("""{"error:":"Operazione scaduta"}"""),
        )
    }

    private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
