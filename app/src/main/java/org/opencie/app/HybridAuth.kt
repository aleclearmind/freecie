package org.opencie.app

import java.security.MessageDigest

/**
 * The phone side of the "desktop + smartphone" level 3 login, as performed by
 * the official CieID app (1.7.28, `CieIDSdk.callHybrid`):
 *
 * 1. the card signs SHA-256 of the scanned QR text (PKCS#1 v1.5 DigestInfo);
 * 2. `POST {idp}/Authn/SSL/X509AuthIbrido` over TLS 1.2 with the card
 *    certificate as client certificate, form fields `hashJsonFirmato`
 *    (signature, upper-case hex) and `opId` (from the QR);
 * 3. the body ends in `:NNNN`, the one-time code the user types on the computer.
 */
object HybridAuth {
    const val ENDPOINT = "Authn/SSL/X509AuthIbrido"

    /** DER prefix of a DigestInfo for SHA-256 (RFC 8017, section 9.2). */
    private val SHA256_DIGEST_INFO = byteArrayOf(
        0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01,
        0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20,
    )

    /** What the card must sign for [request]. */
    fun dataToSign(request: QrLoginRequest): ByteArray =
        SHA256_DIGEST_INFO + MessageDigest.getInstance("SHA-256").digest(request.raw.toByteArray(Charsets.UTF_8))

    /**
     * Form body, values sent verbatim like the official client
     * (Retrofit `@FieldMap(encoded = true)`): `opId` comes from the QR as-is.
     */
    fun formBody(request: QrLoginRequest, signature: ByteArray): String =
        "hashJsonFirmato=${signature.toHexUpper()}&opId=${request.opId}"

    sealed interface Result {
        data class Otp(val code: String) : Result
        data class Refused(val message: String) : Result
    }

    private val OTP = Regex("^[0-9]{4}$")
    private val JSON_ERROR = Regex("\"error:?\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** Interprets a successful (HTTP 2xx) response body. */
    fun parseResponse(body: String): Result {
        val parts = body.split(':').dropLastWhile { it.isEmpty() }
        val candidate = parts.getOrNull(1)
        if (candidate != null && OTP.matches(candidate)) return Result.Otp(candidate)
        JSON_ERROR.find(body)?.let { return Result.Refused(it.groupValues[1]) }
        return Result.Refused(body.trim().take(200).ifEmpty { "risposta vuota" })
    }

    private fun ByteArray.toHexUpper(): String =
        joinToString("") { "%02X".format(it.toInt() and 0xff) }
}
