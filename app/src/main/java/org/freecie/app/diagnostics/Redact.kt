package org.freecie.app.diagnostics

import org.freecie.app.HybridAuth

/**
 * Turns sensitive values into shareable (Italian) descriptions. Nothing here
 * may return a PIN, the one-time code, QR parameter values, APDU data or
 * personal data.
 */
object Redact {

    /** Scheme, host and path of a scanned QR code; parameter values become lengths, except `level`. */
    fun qr(raw: String): String {
        val withoutFragment = raw.substringBefore('#')
        val base = scrub(withoutFragment.substringBefore('?'))
        val query = withoutFragment.substringAfter('?', "")
        if (query.isEmpty()) return base
        val params = query.split('&').filter { it.isNotEmpty() }.joinToString("&") { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (name == "level") pair else "$name=<${value.length} caratteri>"
        }
        return "$base?$params"
    }

    /** `→ 0C 20 00 81  VERIFY PIN (messaggistica sicura), 29 byte`: header only, never the data. */
    fun apduCommand(header: ByteArray, length: Int): String {
        val bytes = header.joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
        val cla = header.getOrNull(0)?.toInt()?.and(0xff) ?: 0
        val ins = header.getOrNull(1)?.toInt()?.and(0xff) ?: -1
        val p2 = header.getOrNull(3)?.toInt()?.and(0xff) ?: -1
        val flags = buildList {
            if (cla and 0x0C == 0x0C) add("messaggistica sicura")
            if (cla and 0x10 != 0) add("concatenato")
        }
        val suffix = if (flags.isEmpty()) "" else " (${flags.joinToString(", ")})"
        return "→ $bytes  ${commandName(ins, p2)}$suffix, $length byte"
    }

    fun apduResponse(dataLength: Int, statusWord: ByteArray): String {
        val sw = statusWord.joinToString("") { "%02X".format(it.toInt() and 0xff) }
        return "← SW $sw ${statusMeaning(sw)}, $dataLength byte di dati"
    }

    fun isVerifyPin(header: ByteArray): Boolean =
        header.size >= 4 && header[1] == 0x20.toByte() && header[3] == 0x81.toByte()

    /** Meaning of the status `Ias.verifyPin` maps to attempts ("ffcX" is how it sees 63CX). */
    fun pinStatus(status: String): String = when (status.lowercase()) {
        "9000" -> "PIN corretto"
        "63c2", "ffc2" -> "PIN errato, 2 tentativi rimasti"
        "63c1", "ffc1" -> "PIN errato, 1 tentativo rimasto"
        "63c0", "ffc0" -> "PIN errato, nessun tentativo rimasto: PIN bloccato"
        "6983", "ff83" -> "PIN bloccato"
        else -> "stato inatteso"
    }

    fun pinStatusMeansBlocked(status: String?): Boolean =
        status?.lowercase() in setOf("63c0", "ffc0", "6983", "ff83")

    /** "1 tentativo rimasto", "2 tentativi rimasti". */
    fun attemptsLeft(n: Int): String = if (n == 1) "1 tentativo rimasto" else "$n tentativi rimasti"

    /** Response body of the identity server, with the one-time code hidden. */
    fun responseBody(body: String): String {
        if (body.isEmpty()) return "(vuoto)"
        return when (val parsed = HybridAuth.parseResponse(body)) {
            is HybridAuth.Result.Otp ->
                "${body.length} caratteri, termina con ':****' (codice monouso di 4 cifre, nascosto)"
            is HybridAuth.Result.Refused ->
                "${body.length} caratteri: \"${scrub(parsed.message).take(300)}\""
        }
    }

    /** Exception chain, class names and messages only. */
    fun throwable(e: Throwable): String =
        generateSequence(e) { it.cause }.take(6).joinToString(" ← ") { t ->
            val message = t.message?.let { ": ${scrub(it).take(200)}" }.orEmpty()
            "${t.javaClass.simpleName}$message"
        }

    /**
     * Hides what could identify a session or a person in free text: tokens of
     * 16+ letters/digits (session ids, hashes) and runs of 4+ digits (codes).
     */
    fun scrub(text: String): String =
        Regex("[0-9]{4,}").replace(
            Regex("[A-Za-z0-9_\\-]{16,}").replace(text) { "<${it.value.length} caratteri>" },
        ) { "#".repeat(it.value.length) }

    // ISO 7816 / IAS command names, kept as in the specifications.
    private fun commandName(ins: Int, p2: Int): String = when (ins) {
        0xA4 -> "SELECT"
        0xB0 -> "READ BINARY"
        0xCB -> "GET DATA"
        0x22 -> "MANAGE SECURITY ENVIRONMENT"
        0x2A -> "PSO VERIFY CERTIFICATE"
        0x84 -> "GET CHALLENGE"
        0x82 -> "EXTERNAL AUTHENTICATE"
        0x88 -> "INTERNAL AUTHENTICATE / SIGN"
        0x20 -> when (p2) {
            0x81 -> "VERIFY PIN"
            0x82 -> "VERIFY PUK"
            else -> "VERIFY"
        }
        0xC0 -> "GET RESPONSE"
        else -> "INS %02X".format(ins)
    }

    private fun statusMeaning(sw: String): String = when {
        sw == "9000" -> "(OK)"
        sw.startsWith("61") -> "(altri dati disponibili)"
        sw == "6282" -> "(fine del file)"
        sw.startsWith("63C") -> "(${attemptsLeft(sw[3].digitToInt(16))})"
        sw == "6700" -> "(lunghezza errata)"
        sw == "6982" -> "(condizioni di sicurezza non soddisfatte)"
        sw == "6983" -> "(metodo di autenticazione bloccato)"
        sw == "6985" -> "(condizioni d’uso non soddisfatte)"
        sw == "6988" -> "(oggetti di messaggistica sicura errati)"
        sw == "6A82" -> "(file non trovato)"
        sw == "6A86" -> "(P1/P2 errati)"
        sw == "6A88" -> "(dati di riferimento non trovati)"
        sw == "6B00" -> "(offset errato)"
        sw.startsWith("6C") -> "(lunghezza errata, da ripetere)"
        sw == "6D00" -> "(istruzione non supportata)"
        sw == "6E00" -> "(classe non supportata)"
        else -> ""
    }
}
