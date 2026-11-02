package org.freecie.app

import java.io.ByteArrayOutputStream

/**
 * A login request read from the QR code that the CIE identity server shows
 * on the computer ("desktop + smartphone" mode).
 *
 * Example (level 2, from a real login page):
 * `https://idserver.servizicie.interno.gov.it/idp/login/livello2QR?opId=_f5…&challenge=…&level=2&SPName=…&SPLogo=…&value=…`
 *
 * The official app treats `level=1` and `level=2` as CieID-credential logins
 * and anything else as level 3 (card + PIN); we accept only the latter.
 */
data class QrLoginRequest(
    /** The scanned text, unchanged: its SHA-256 is what the card signs. */
    val raw: String,
    val host: String,
    val opId: String,
    val level: String?,
    val spName: String?,
) {
    /** Base URL of the identity server that issued the QR code (same rule as the official app). */
    val idpBaseUrl: String
        get() = if (host.contains(OIDC_HOST, ignoreCase = true)) "https://$OIDC_HOST/idp/" else "https://$IDP_HOST/idp/"

    sealed class Invalid(message: String) : Exception(message) {
        class NotALoginQr : Invalid("non è un QR di accesso CIE")
        class ForeignHost(val host: String) : Invalid("host inatteso $host")
        class NotLevel3(val level: String) : Invalid("QR di livello $level")
    }

    companion object {
        const val IDP_HOST = "idserver.servizicie.interno.gov.it"
        const val OIDC_HOST = "oidc.idserver.servizicie.interno.gov.it"

        /** Ministry of the Interior domain: idserver, oidc.idserver, ios.idserver, … */
        private const val MINISTRY_DOMAIN = "interno.gov.it"

        /**
         * Parses as leniently as the official app (`Uri.parse` + `opId`): the
         * text is not required to be a strict RFC 3986 URI, e.g. it may contain
         * raw spaces. Only the host is sanity-checked; the login request always
         * goes to the fixed identity server, never to the QR host.
         */
        @Throws(Invalid::class)
        fun parse(text: String): QrLoginRequest {
            val schemeEnd = text.indexOf("://")
            if (schemeEnd <= 0) throw Invalid.NotALoginQr()
            val rest = text.substring(schemeEnd + 3)
            val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
            val host = rest.substring(0, authorityEnd).substringAfterLast('@').substringBefore(':').lowercase()
            if (host.isEmpty()) throw Invalid.NotALoginQr()
            if (host != MINISTRY_DOMAIN && !host.endsWith(".$MINISTRY_DOMAIN")) throw Invalid.ForeignHost(host)

            val query = rest.substring(authorityEnd).substringAfter('?', "").substringBefore('#')
            val params = queryParameters(query)
            val opId = params["opId"]?.takeIf { it.isNotEmpty() } ?: throw Invalid.NotALoginQr()
            val level = params["level"]
            if (level == "1" || level == "2") throw Invalid.NotLevel3(level)

            return QrLoginRequest(
                raw = text,
                host = host,
                opId = opId,
                level = level,
                spName = params["SPName"]?.takeIf { it.isNotBlank() },
            )
        }

        /**
         * First value of each query parameter, decoded like Android's
         * `Uri.getQueryParameter`: `%XX` escapes are decoded, `+` is kept.
         */
        internal fun queryParameters(rawQuery: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            for (pair in rawQuery.split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                val name = percentDecode(if (eq < 0) pair else pair.substring(0, eq))
                val value = if (eq < 0) "" else percentDecode(pair.substring(eq + 1))
                result.putIfAbsent(name, value)
            }
            return result
        }

        private fun percentDecode(s: String): String {
            if ('%' !in s) return s
            val out = ByteArrayOutputStream(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '%' && i + 2 < s.length && isHex(s[i + 1]) && isHex(s[i + 2])) {
                    out.write(s.substring(i + 1, i + 3).toInt(16))
                    i += 3
                } else {
                    out.write(c.toString().toByteArray(Charsets.UTF_8))
                    i++
                }
            }
            return out.toString(Charsets.UTF_8.name())
        }

        private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    }
}
