package org.opencie.app

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import it.pagopa.io.app.cie.cie.ApduResponse
import it.pagopa.io.app.cie.cie.CieSdkException
import it.pagopa.io.app.cie.cie.NfcError
import it.pagopa.io.app.cie.cie.NfcEvent
import it.pagopa.io.app.cie.cie.OnTransmit
import it.pagopa.io.app.cie.cie.commands.CieCommands
import it.pagopa.io.app.cie.cie.key_store.CieSigner
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.opencie.app.diagnostics.Diagnostics
import org.opencie.app.diagnostics.Kind
import org.opencie.app.diagnostics.Redact
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale

enum class Step(val label: String) {
    CONNECTING("connessione alla carta"),
    SECURE_CHANNEL("canale sicuro e verifica del PIN"),
    CERTIFICATE("lettura del certificato"),
    SIGNING("firma della richiesta"),
    SERVER("contatto con il server di identità"),
}

sealed interface LoginFailure {
    data object QrInvalid : LoginFailure
    data class QrForeignHost(val host: String) : LoginFailure
    data class QrNotLevel3(val level: String) : LoginFailure
    data class WrongPin(val attemptsLeft: Int) : LoginFailure
    data object PinBlocked : LoginFailure
    data object NotACie : LoginFailure
    data object ExtendedApduUnsupported : LoginFailure
    data object TagLost : LoginFailure
    data object NoNetwork : LoginFailure
    data object CertificateExpired : LoginFailure
    data object CertificateRevoked : LoginFailure
    data class Http(val code: Int) : LoginFailure
    data class Refused(val message: String) : LoginFailure
    data class Card(val detail: String) : LoginFailure
    data class Unexpected(val detail: String) : LoginFailure
}

class LoginException(val failure: LoginFailure, cause: Throwable? = null) : Exception(failure.toString(), cause)

/** The level 3 "desktop + smartphone" login, card side and server side. */
object CieLogin {
    private const val ISO_DEP_TIMEOUT_MS = 10_000

    /**
     * Runs the whole login on a freshly discovered card and returns the
     * one-time code to type on the computer. Blocking: call it on the NFC
     * reader thread. The PIN is verified exactly once; [pin] is not copied
     * and the caller wipes it.
     */
    fun run(isoDep: IsoDep, request: QrLoginRequest, pin: ByteArray, onStep: (Step) -> Unit): String {
        val channel = CardChannel(isoDep)
        var commands: CieCommands? = null
        var step = Step.CONNECTING
        fun enter(next: Step) {
            step = next
            Diagnostics.add(Kind.STEP, "Passo: ${next.label}")
            onStep(next)
        }
        try {
            enter(Step.CONNECTING)
            logTag(isoDep)
            isoDep.timeout = ISO_DEP_TIMEOUT_MS
            if (!isoDep.isConnected) isoDep.connect()
            if (!isoDep.isExtendedLengthApduSupported) throw LoginException(LoginFailure.ExtendedApduUnsupported)
            val card = CieCommands(channel).also { commands = it }
            card.getServiceID() // selects the CIE applets; NOT_A_CIE otherwise
            Diagnostics.add(Kind.INFO, "La carta ha risposto come CIE (applet dei servizi trovata)")

            enter(Step.SECURE_CHANNEL)
            card.startSecureChannel(pin)
            logPinStatus(card.lastPinStatus)
            Diagnostics.add(Kind.PIN_OK, "✔ PIN ACCETTATO dalla carta: i tentativi disponibili tornano al massimo")

            enter(Step.CERTIFICATE)
            val certificate = card.readCertCie()
            logCertificate(certificate)

            enter(Step.SIGNING)
            val signature = card.sign(HybridAuth.dataToSign(request), NfcEvent.SIGN)
                ?.takeIf { it.isNotEmpty() }
                ?: throw LoginException(LoginFailure.Card("firma vuota"))
            Diagnostics.add(Kind.INFO, "La carta ha firmato lo SHA-256 del testo del QR: firma di ${signature.size} byte")

            enter(Step.SERVER)
            CieSigner.commands = card
            val result = try {
                HybridClient(request, certificate).send(signature)
            } finally {
                CieSigner.commands = null
            }
            return when (result) {
                is HybridAuth.Result.Otp -> {
                    Diagnostics.add(Kind.OK, "✔ Codice monouso ricevuto (4 cifre, non registrato)")
                    result.code
                }
                is HybridAuth.Result.Refused -> throw LoginException(LoginFailure.Refused(result.message))
            }
        } catch (e: Throwable) {
            val pinStatus = commands?.lastPinStatus
            if (channel.pinSent && e.nfcError() in setOf(NfcError.WRONG_PIN, NfcError.PIN_BLOCKED)) logPinStatus(pinStatus)
            val failure = (e as? LoginException)?.failure ?: classify(e, step, pinStatus)
            logPinOutcome(e, step, channel.pinSent, pinStatus)
            Diagnostics.add(Kind.ERROR, "✘ Errore nel passo «${step.label}»: ${Redact.scrub(describe(failure))}")
            if (e !is LoginException) Diagnostics.add(Kind.ERROR, "  causa: ${Redact.throwable(e)}")
            throw e as? LoginException ?: LoginException(failure, e)
        }
    }

    private fun logPinStatus(status: String?) {
        if (status != null) {
            Diagnostics.add(Kind.PIN, "Risposta della carta a VERIFY PIN: stato $status = ${Redact.pinStatus(status)}")
        }
    }

    private fun Throwable.nfcError(): NfcError? = (this as? CieSdkException)?.getError()

    /** Makes the PIN's fate explicit in the log whenever the login fails. */
    private fun logPinOutcome(e: Throwable, step: Step, pinSent: Boolean, pinStatus: String?) {
        val error = e.nfcError()
        when {
            error == NfcError.WRONG_PIN ->
                Diagnostics.add(Kind.PIN_KO, "✘ PIN RIFIUTATO dalla carta: ${Redact.attemptsLeft(error.numberOfAttempts ?: -1)}")
            error == NfcError.PIN_BLOCKED && Redact.pinStatusMeansBlocked(pinStatus) ->
                Diagnostics.add(Kind.PIN_KO, "✘ PIN BLOCCATO: nessun tentativo rimasto, serve il PUK")
            error == NfcError.PIN_BLOCKED ->
                Diagnostics.add(
                    Kind.PIN_KO,
                    "✘ Verifica del PIN con stato inatteso della carta ($pinStatus): tentativi rimasti sconosciuti",
                )
            pinSent && pinStatus == null ->
                Diagnostics.add(
                    Kind.PIN_KO,
                    "!!! Connessione persa mentre la carta verificava il PIN: se il PIN era errato, " +
                        "un tentativo potrebbe essere stato consumato",
                )
            !pinSent && step <= Step.SECURE_CHANNEL ->
                Diagnostics.add(Kind.PIN, "PIN NON INVIATO: l’errore è avvenuto prima della verifica, nessun tentativo consumato")
        }
    }

    private fun logTag(isoDep: IsoDep) {
        val tag = isoDep.tag
        val techs = tag.techList.joinToString(", ") { it.substringAfterLast('.') }
        val historical = isoDep.historicalBytes?.toHex() ?: "nessuno"
        val hiLayer = isoDep.hiLayerResponse?.toHex() ?: "nessuna"
        Diagnostics.add(
            Kind.INFO,
            "Carta rilevata: $techs; byte storici $historical; risposta di livello superiore $hiLayer; " +
                "transceive massimo ${isoDep.maxTransceiveLength} byte; APDU estese " +
                (if (isoDep.isExtendedLengthApduSupported) "supportate" else "NON supportate"),
        )
    }

    /** Issuer and validity only: the subject holds the holder's name and tax code. */
    private fun logCertificate(certificate: ByteArray) {
        val description = runCatching {
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certificate)) as X509Certificate
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            val issuerCn = cert.issuerX500Principal.name.split(',')
                .firstOrNull { it.trim().startsWith("CN=") }?.trim()?.removePrefix("CN=") ?: "?"
            "emittente \"$issuerCn\", valido dal ${format.format(cert.notBefore)} al ${format.format(cert.notAfter)}, " +
                "${cert.sigAlgName}, chiave a ${(cert.publicKey as? java.security.interfaces.RSAPublicKey)?.modulus?.bitLength() ?: "?"} bit"
        }.getOrElse { "non interpretabile (${it.javaClass.simpleName})" }
        Diagnostics.add(
            Kind.INFO,
            "Certificato di autenticazione letto: ${certificate.size} byte; $description (titolare non registrato)",
        )
    }

    private fun classify(e: Throwable, step: Step, pinStatus: String?): LoginFailure {
        val chain = generateSequence(e) { it.cause }.take(10).toList()
        fun has(predicate: (Throwable) -> Boolean) = chain.any(predicate)
        val messages = chain.joinToString(" ") { it.message.orEmpty() }

        val error = e.nfcError()
        return when {
            error == NfcError.WRONG_PIN -> LoginFailure.WrongPin(error.numberOfAttempts ?: -1)
            // verifyPin reports every unknown status as "0 attempts": only
            // call it blocked when the card actually said so.
            error == NfcError.PIN_BLOCKED && !Redact.pinStatusMeansBlocked(pinStatus) ->
                LoginFailure.Card("stato inatteso $pinStatus durante la verifica del PIN")
            error == NfcError.PIN_BLOCKED -> LoginFailure.PinBlocked
            error == NfcError.NOT_A_CIE -> LoginFailure.NotACie
            error == NfcError.TAG_LOST -> LoginFailure.TagLost
            error != null -> LoginFailure.Card(error.msg?.let { "${error.name}: $it" } ?: error.name)
            e is HybridClient.HttpError -> LoginFailure.Http(e.code)
            has { it is TagLostException } -> LoginFailure.TagLost
            "CERTIFICATE_EXPIRED" in messages -> LoginFailure.CertificateExpired
            "CERTIFICATE_REVOKED" in messages -> LoginFailure.CertificateRevoked
            has { it is UnknownHostException || it is SocketTimeoutException || it is ConnectException } ->
                LoginFailure.NoNetwork
            step != Step.SERVER && e is IOException -> LoginFailure.TagLost
            step == Step.SERVER && e is SSLException -> LoginFailure.Unexpected("TLS: ${e.message}")
            step == Step.SERVER && e is IOException -> LoginFailure.NoNetwork
            step != Step.SERVER -> LoginFailure.Card(e.message ?: e.javaClass.simpleName)
            else -> LoginFailure.Unexpected(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Italian, log-friendly description of a failure (the UI uses string resources). */
    private fun describe(failure: LoginFailure): String = when (failure) {
        LoginFailure.QrInvalid -> "QR non valido"
        is LoginFailure.QrForeignHost -> "QR da un host inatteso (${failure.host})"
        is LoginFailure.QrNotLevel3 -> "QR di livello ${failure.level}"
        is LoginFailure.WrongPin -> "PIN errato, ${Redact.attemptsLeft(failure.attemptsLeft)}"
        LoginFailure.PinBlocked -> "PIN bloccato"
        LoginFailure.NotACie -> "la carta non è una CIE"
        LoginFailure.ExtendedApduUnsupported -> "l’NFC del telefono non supporta le APDU estese"
        LoginFailure.TagLost -> "carta allontanata o connessione NFC persa"
        LoginFailure.NoNetwork -> "server non raggiungibile"
        LoginFailure.CertificateExpired -> "certificato della carta scaduto"
        LoginFailure.CertificateRevoked -> "certificato della carta revocato"
        is LoginFailure.Http -> "risposta HTTP ${failure.code}"
        is LoginFailure.Refused -> "rifiutato dal server: ${failure.message}"
        is LoginFailure.Card -> "errore della carta: ${failure.detail}"
        is LoginFailure.Unexpected -> "errore imprevisto: ${failure.detail}"
    }

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
}

/**
 * Sends APDUs to the card for pagoPA's `CieCommands`, and logs the shape of the
 * exchange: command header and status word only, never the data.
 */
private class CardChannel(private val isoDep: IsoDep) : OnTransmit {
    @Volatile
    var pinSent = false
        private set

    override fun sendCommand(apdu: ByteArray, nfcEvents: NfcEvent): ApduResponse {
        val header = apdu.copyOf(minOf(4, apdu.size))
        if (Redact.isVerifyPin(header)) {
            pinSent = true
            Diagnostics.add(Kind.PIN, ">>> PIN INVIATO alla carta ora (VERIFY PIN, cifrato). Solo un PIN errato consuma un tentativo")
        }
        Diagnostics.add(Kind.APDU, "${Redact.apduCommand(header, apdu.size)}  [${nfcEvents.name}]")
        val response = ApduResponse(isoDep.transceive(apdu))
        Diagnostics.add(Kind.APDU, Redact.apduResponse(response.response.size, response.swByte))
        return response
    }

    override fun error(error: NfcError) {
        Diagnostics.add(Kind.ERROR, "La libreria della carta ha segnalato: ${error.name}")
    }
}
