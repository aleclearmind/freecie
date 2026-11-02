package org.freecie.app.cie

import it.pagopa.io.app.cie.nfc.Algorithms
import it.pagopa.io.app.cie.nfc.Utils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The CIE secure messaging in pagopa/cie-sdk-android (`ApduSecureMessageManager`)
 * uses the same primitives and MAC layout as ICAO 9303 SM: 2-key 3DES-CBC with
 * zero IV, ISO 9797-1 padding method 2 and retail MAC (MAC algorithm 3) over
 * SSC || header || DOs. Checked against ICAO Doc 9303 part 11, appendix D.4.
 */
class SecureMessagingCryptoTest {

    private val ksEnc = "979EC13B1CBFE9DCD01AB0FED307EAE5".hex()
    private val ksMac = "F1CB1F1FB5ADF208806B89DC579DC1F8".hex()

    @Test
    fun encryptsCommandData() {
        val padded = Utils.getIsoPad("011E".hex())
        assertEquals("011E800000000000", padded.toHex())
        assertEquals("6375432908C044F6", Algorithms.desEnc(ksEnc, padded).toHex())
        assertEquals("011E", Utils.isoRemove(Algorithms.desDec(ksEnc, "6375432908C044F6".hex())).toHex())
    }

    @Test
    fun macsCommandLikeTheSdk() {
        // sm(): isoPad(SSC || header) followed by the data objects, then isoPad again.
        val ssc = "887022120C06C227".hex()
        val header = "0CA4020C".hex()
        val do87 = Utils.asn1Tag(byteArrayOf(0x01) + "6375432908C044F6".hex(), 0x87)
        assertEquals("8709016375432908C044F6", do87.toHex())
        val macInput = Utils.getIsoPad(Utils.getIsoPad(ssc + header) + do87)
        assertEquals("BF8B92D635FF24F8", Algorithms.macEnc(ksMac, macInput).toHex())
    }

    @Test
    fun macsResponseLikeTheSdk() {
        // respSM(): MAC over isoPad(SSC || DO99).
        val ssc = "887022120C06C228".hex()
        val do99 = "99029000".hex()
        assertEquals("FA855A5D4C50A8ED", Algorithms.macEnc(ksMac, Utils.getIsoPad(ssc + do99)).toHex())
    }

    private fun String.hex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it.toInt() and 0xff) }
}
