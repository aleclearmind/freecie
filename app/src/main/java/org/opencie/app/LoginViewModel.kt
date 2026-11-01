package org.opencie.app

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.opencie.app.diagnostics.Diagnostics
import org.opencie.app.diagnostics.Kind
import org.opencie.app.diagnostics.Redact
import java.util.concurrent.atomic.AtomicReference

/** What "Try again" does on the error screen. */
sealed interface Retry {
    data object None : Retry
    /** The QR code is wrong or used up: scan a new one. */
    data object Rescan : Retry
    /** The QR code is still good: re-enter the PIN and tap again. */
    data class Pin(val request: QrLoginRequest) : Retry
}

sealed interface Screen {
    data object Home : Screen
    data object Scan : Screen
    data class Pin(val request: QrLoginRequest, val attemptsLeft: Int? = null) : Screen
    /** Waiting for the card ([step] null) or working on it. */
    data class Tap(val request: QrLoginRequest, val step: Step? = null) : Screen
    data class Code(val otp: String) : Screen
    data class Failed(val failure: LoginFailure, val retry: Retry) : Screen
    /** The diagnostic log, opened from [back]. */
    data class Log(val back: Screen) : Screen
}

class LoginViewModel : ViewModel() {

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    /**
     * Request and PIN armed for the next card tap; taken exactly once. The PIN
     * is kept as ASCII bytes only, never as a String, and [wipe]d after use.
     */
    private class Pending(val session: Int, val request: QrLoginRequest, val pin: ByteArray) {
        fun wipe() = pin.fill(0)
    }

    private val pending = AtomicReference<Pending?>(null)
    private val main = Handler(Looper.getMainLooper())

    /** Bumped on every new attempt or cancel; stale card results are dropped. */
    private var session = 0
    private var attempts = 0

    val wantsCard: Boolean
        get() = screen is Screen.Tap

    fun openScanner() {
        discardPin()
        session++
        Diagnostics.add(Kind.INFO, "Scanner QR aperto")
        screen = Screen.Scan
    }

    /** [text] exactly as scanned: it is what the card signs. */
    fun onScanned(text: String) {
        Diagnostics.add(Kind.INFO, "QR letto (${text.length} caratteri): ${Redact.qr(text)}")
        screen = try {
            QrLoginRequest.parse(text).let { request ->
                Diagnostics.add(
                    Kind.INFO,
                    "QR accettato: livello ${request.level ?: "non indicato (trattato come 3)"}, host ${request.host}, " +
                        "l’accesso andrà a ${request.idpBaseUrl}",
                )
                Screen.Pin(request)
            }
        } catch (e: QrLoginRequest.Invalid) {
            Diagnostics.add(Kind.WARN, "QR rifiutato: ${e.message}")
            Screen.Failed(
                when (e) {
                    is QrLoginRequest.Invalid.NotALoginQr -> LoginFailure.QrInvalid
                    is QrLoginRequest.Invalid.ForeignHost -> LoginFailure.QrForeignHost(e.host)
                    is QrLoginRequest.Invalid.NotLevel3 -> LoginFailure.QrNotLevel3(e.level)
                },
                Retry.Rescan,
            )
        }
    }

    fun onPasted(text: String) {
        Diagnostics.add(Kind.INFO, "Link del QR inserito a mano")
        onScanned(text.trim())
    }

    /** Copies the digits of [pin] straight into a byte array; the caller clears its own copy. */
    fun submitPin(pin: CharSequence) {
        val current = screen as? Screen.Pin ?: return
        val bytes = ByteArray(pin.length) { pin[it].code.toByte() }
        pending.getAndSet(Pending(++session, current.request, bytes))?.wipe()
        Diagnostics.add(Kind.STEP, "──────── Tentativo di accesso n. ${++attempts} ────────")
        Diagnostics.add(
            Kind.PIN,
            "PIN inserito (${pin.length} cifre, mai registrato). Verrà inviato alla carta una sola volta, " +
                "quando la carta viene rilevata",
        )
        screen = Screen.Tap(current.request)
    }

    /** Called on the NFC reader thread. */
    fun onTagDiscovered(tag: Tag) {
        val job = pending.getAndSet(null)
        if (job == null) {
            Diagnostics.add(Kind.INFO, "Carta avvicinata senza un accesso in attesa: ignorata (PIN non inviato)")
            return
        }
        val isoDep = IsoDep.get(tag)
        try {
            if (isoDep == null) {
                Diagnostics.add(
                    Kind.ERROR,
                    "Il tag non è ISO-DEP (${tag.techList.joinToString { it.substringAfterLast('.') }}): PIN non inviato",
                )
                post(job, Screen.Failed(LoginFailure.NotACie, Retry.Pin(job.request)))
                return
            }
            val otp = CieLogin.run(isoDep, job.request, job.pin) { step -> post(job, Screen.Tap(job.request, step)) }
            post(job, Screen.Code(otp))
        } catch (e: LoginException) {
            post(
                job,
                when (val failure = e.failure) {
                    is LoginFailure.WrongPin -> Screen.Pin(job.request, attemptsLeft = failure.attemptsLeft)
                    LoginFailure.PinBlocked, LoginFailure.ExtendedApduUnsupported,
                    LoginFailure.CertificateExpired, LoginFailure.CertificateRevoked,
                    -> Screen.Failed(failure, Retry.None)
                    is LoginFailure.Http, is LoginFailure.Refused -> Screen.Failed(failure, Retry.Rescan)
                    else -> Screen.Failed(failure, Retry.Pin(job.request))
                },
            )
        } finally {
            // Every path after the PIN was taken ends here.
            job.wipe()
            runCatching { isoDep?.close() }
        }
    }

    fun retryPin(request: QrLoginRequest) {
        discardPin()
        session++
        Diagnostics.add(Kind.INFO, "Riprova: di nuovo all’inserimento del PIN con lo stesso QR")
        screen = Screen.Pin(request)
    }

    fun reset() {
        when (screen) {
            is Screen.Tap -> Diagnostics.add(Kind.WARN, "Annullato dall’utente durante la lettura della carta")
            is Screen.Pin, Screen.Scan -> Diagnostics.add(Kind.INFO, "Annullato dall’utente")
            else -> Unit
        }
        discardPin()
        session++
        screen = Screen.Home
    }

    /** Forgets (and zeroes) a PIN that was entered but not used. */
    private fun discardPin() {
        pending.getAndSet(null)?.wipe()
    }

    override fun onCleared() {
        discardPin()
    }

    fun openLog() {
        screen = Screen.Log(back = screen)
    }

    fun closeLog() {
        (screen as? Screen.Log)?.let { screen = it.back }
    }

    private fun post(job: Pending, next: Screen) {
        main.post {
            // Drop results of a cancelled attempt, and late progress updates.
            if (job.session != session) return@post
            if (next is Screen.Tap && screen !is Screen.Tap) return@post
            screen = next
        }
    }
}
