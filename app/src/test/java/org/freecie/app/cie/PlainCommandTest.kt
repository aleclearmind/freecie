package org.freecie.app.cie

import it.pagopa.io.app.cie.cie.plainCommand
import it.pagopa.io.app.cie.nfc.Utils
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * `plainCommand` (patches/cie-sdk-android/0003) must build exactly what
 * pagoPA's step-by-step concatenation built before the patch.
 */
class PlainCommandTest {

    private fun upstream(head: ByteArray, data: ByteArray, le: ByteArray?): ByteArray {
        var apdu = byteArrayOf()
        apdu = Utils.appendByteArray(apdu, head)
        apdu = Utils.appendByte(apdu, data.size.toByte())
        apdu = Utils.appendByteArray(apdu, data)
        if (le != null) apdu = Utils.appendByteArray(apdu, le)
        return apdu
    }

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun matchesUpstreamForEveryCommandShape() {
        val cases = listOf(
            Triple(hex("00200081"), "12345678".toByteArray(), null), // VERIFY PIN: data, no Le
            Triple(hex("00B00000"), byteArrayOf(), byteArrayOf(0x00)), // READ BINARY: Le only
            Triple(hex("00840000"), byteArrayOf(), byteArrayOf(0x08)), // GET CHALLENGE: Le only
            Triple(hex("002241A4"), hex("800102840181"), null), // MSE SET: data only
            Triple(hex("00880000"), ByteArray(51) { it.toByte() }, null), // SIGN: DigestInfo
            Triple(hex("00A40204"), hex("1003"), byteArrayOf(0x00)), // data and Le
            Triple(hex("00220000"), ByteArray(0xE6) { 0x55 }, null), // largest short command
        )
        for ((head, data, le) in cases) {
            assertArrayEquals(upstream(head, data, le), plainCommand(head, data, le))
        }
    }
}
