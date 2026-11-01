package org.opencie.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class QrLoginRequestTest {

    // Same shape as a real QR code from the identity server (values are made up).
    private val level3 = "https://idserver.servizicie.interno.gov.it/idp/login/livello3QR" +
        "?opId=_0123456789abcdef0123456789abcdef&challenge=d%2FyXSe%2Fj9P%2BnNJ&level=3" +
        "&SPName=https%3A%2F%2Fiam.example.it%2Fshibboleth" +
        "&SPLogo=https%3A%2F%2Fidserver.servizicie.interno.gov.it%2Fidp%2Fimages%2Fcielogo.png&value=e2s1"

    @Test
    fun parsesLevel3Qr() {
        val request = QrLoginRequest.parse(level3)
        assertEquals(level3, request.raw)
        assertEquals("idserver.servizicie.interno.gov.it", request.host)
        assertEquals("_0123456789abcdef0123456789abcdef", request.opId)
        assertEquals("3", request.level)
        assertEquals("https://iam.example.it/shibboleth", request.spName)
        assertEquals("https://idserver.servizicie.interno.gov.it/idp/", request.idpBaseUrl)
    }

    @Test
    fun qrWithoutLevelIsTreatedAsLevel3() {
        val request = QrLoginRequest.parse(level3.replace("&level=3", ""))
        assertNull(request.level)
    }

    @Test
    fun rejectsLevel1And2() {
        for (level in listOf("1", "2")) {
            val qr = level3.replace("livello3QR", "livello${level}QR").replace("level=3", "level=$level")
            val e = assertThrows(QrLoginRequest.Invalid.NotLevel3::class.java) { QrLoginRequest.parse(qr) }
            assertEquals(level, e.level)
        }
    }

    @Test
    fun oidcQrUsesOidcIdentityServer() {
        for (host in listOf("oidc.idserver.servizicie.interno.gov.it", "ios.oidc.idserver.servizicie.interno.gov.it")) {
            val request = QrLoginRequest.parse(level3.replace("//idserver.servizicie.interno.gov.it", "//$host"))
            assertEquals(host, "https://oidc.idserver.servizicie.interno.gov.it/idp/", request.idpBaseUrl)
        }
    }

    @Test
    fun acceptsOtherMinistryHosts() {
        for (host in listOf("ios.idserver.servizicie.interno.gov.it", "www.cartaidentita.interno.gov.it")) {
            val request = QrLoginRequest.parse(level3.replace("//idserver.servizicie.interno.gov.it", "//$host"))
            assertEquals(host, request.host)
            assertEquals("https://idserver.servizicie.interno.gov.it/idp/", request.idpBaseUrl)
        }
    }

    @Test
    fun acceptsUnencodedCharactersLikeAndroidUri() {
        // Hybrid QR codes may carry a raw opText with spaces (see the italia iOS SDK).
        val qr = "https://idserver.servizicie.interno.gov.it/idp/login/QR?IdPName=CIE&opText=Accesso con CIE" +
            "&opType=x&opId=_abc|1&userId=u&SPName=Comune di Esempio"
        val request = QrLoginRequest.parse(qr)
        assertEquals(qr, request.raw)
        assertEquals("_abc|1", request.opId)
        assertEquals("Comune di Esempio", request.spName)
    }

    @Test
    fun rejectsForeignHost() {
        for (host in listOf("evil.example.com", "interno.gov.it.evil.example.com", "notinterno.gov.it")) {
            val e = assertThrows(QrLoginRequest.Invalid.ForeignHost::class.java) {
                QrLoginRequest.parse(level3.replace("idserver.servizicie.interno.gov.it/idp", "$host/idp"))
            }
            assertEquals(host, e.host)
        }
    }

    @Test
    fun rejectsNonLoginQrCodes() {
        val bad = listOf(
            level3.replace("opId=_0123456789abcdef0123456789abcdef&", ""),
            level3.replace("opId=_0123456789abcdef0123456789abcdef", "opId="),
            "WIFI:S:home;T:WPA;P:secret;;",
            "not a url at all",
            "https:///idp/login?opId=_x",
            "",
        )
        for (text in bad) {
            assertThrows(text, QrLoginRequest.Invalid.NotALoginQr::class.java) { QrLoginRequest.parse(text) }
        }
    }

    @Test
    fun decodesQueryLikeAndroidUri() {
        // Android's Uri.getQueryParameter keeps '+' and decodes %XX (UTF-8).
        val params = QrLoginRequest.queryParameters("a=x+y%2B&b=caf%C3%A8&a=second&c&d=100%")
        assertEquals("x+y+", params["a"])
        assertEquals("cafè", params["b"])
        assertEquals("", params["c"])
        assertEquals("100%", params["d"])
    }
}
