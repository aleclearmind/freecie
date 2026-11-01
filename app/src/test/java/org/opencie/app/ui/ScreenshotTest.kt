package org.opencie.app.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Test
import org.opencie.app.LoginFailure
import org.opencie.app.NfcStatus
import org.opencie.app.QrLoginRequest
import org.opencie.app.Retry
import org.opencie.app.Screen
import org.opencie.app.Step
import org.opencie.app.diagnostics.DiagnosticLog

/**
 * Renders the screens of "OpenCIE Mockups" (docs/design-brief.md) on a
 * 360×782 dp phone, light and dark. `gradle recordPaparazziDebug` writes the
 * PNGs to app/src/test/snapshots/images; the flake publishes them.
 */
class ScreenshotTest {
    private val phone = DeviceConfig.PIXEL_5.copy(
        screenWidth = 1080,
        screenHeight = 2346,
        xdpi = 480,
        ydpi = 480,
        density = Density.XXHIGH,
        locale = "it",
    )

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = phone)

    private val request = QrLoginRequest.parse(
        "https://idserver.servizicie.interno.gov.it/idp/login/livello3QR?opId=_x&level=3" +
            "&SPName=https%3A%2F%2Fiam.servizi-online.regione.example.it%2Fshibboleth",
    )

    @Test fun home() = both("home") { Screen.Home to NfcStatus.DISABLED }

    @Test fun homeNoNfc() = shot("home-no-nfc", dark = false, Screen.Home, NfcStatus.MISSING)

    @Test fun pin() = both("pin") { Screen.Pin(request, attemptsLeft = 1) to NfcStatus.READY }

    @Test fun pinTwoAttempts() = shot("pin-two-attempts", dark = false, Screen.Pin(request, attemptsLeft = 2))

    @Test fun holdCard() = both("hold-card") { Screen.Tap(request, Step.CERTIFICATE) to NfcStatus.READY }

    @Test fun code() = both("code") { Screen.Code("4815") to NfcStatus.READY }

    @Test fun error() = both("error") { Screen.Failed(LoginFailure.QrNotLevel3("2"), Retry.Rescan) to NfcStatus.READY }

    @Test fun log() = both("log") { Screen.Log(back = Screen.Home) to NfcStatus.READY }

    @Test fun errorPinBlocked() =
        shot("error-pin-blocked", dark = false, Screen.Failed(LoginFailure.PinBlocked, Retry.None))

    private fun both(name: String, state: () -> Pair<Screen, NfcStatus>) {
        val (screen, nfc) = state()
        shot(name, dark = false, screen, nfc)
        shot(name, dark = true, screen, nfc)
    }

    private fun shot(name: String, dark: Boolean, screen: Screen, nfc: NfcStatus = NfcStatus.READY) {
        paparazzi.unsafeUpdateConfig(
            deviceConfig = phone.copy(nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT),
        )
        paparazzi.snapshot(name = "$name-${if (dark) "dark" else "light"}") {
            WithBackDispatcher {
                OpenCieTheme {
                    OpenCieApp(screen, nfc, {}, {}, {}, {}, {}, {}, {}, logEntries = sampleLog)
                }
            }
        }
    }

    private val sampleLog = listOf(
        "14:05:01.120  INFO    QR letto (212 caratteri): https://idserver.servizicie.interno.gov.it/idp/login/livello3QR?opId=<34 caratteri>&challenge=<40 caratteri>&level=3",
        "14:05:09.877  PASSO   ──────── Tentativo di accesso n. 1 ────────",
        "14:05:09.878  PIN     PIN inserito (8 cifre, mai registrato). Verrà inviato alla carta una sola volta, quando la carta viene rilevata",
        "14:05:12.402  INFO    Carta rilevata: IsoDep, NfcB; APDU estese supportate",
        "14:05:12.455  APDU    → 00 A4 04 0C  SELECT, 18 byte",
        "14:05:12.471  APDU    ← SW 9000 (OK), 0 byte di dati",
        "14:05:13.902  PIN     >>> PIN INVIATO alla carta ora (VERIFY PIN, cifrato). Solo un PIN errato consuma un tentativo",
        "14:05:13.903  APDU    → 0C 20 00 81  VERIFY PIN (messaggistica sicura), 29 byte",
        "14:05:13.961  APDU    ← SW 9000 (OK), 14 byte di dati",
        "14:05:13.962  PIN     Risposta della carta a VERIFY PIN: stato ffc2 = PIN errato, 2 tentativi rimasti",
        "14:05:13.963  PIN-KO  ✘ PIN RIFIUTATO dalla carta: 2 tentativi rimasti",
        "14:05:13.964  ERRORE  ✘ Errore nel passo «canale sicuro e verifica del PIN»: PIN errato, 2 tentativi rimasti",
        "14:06:02.310  PASSO   ──────── Tentativo di accesso n. 2 ────────",
        "14:06:05.118  PIN-OK  ✔ PIN ACCETTATO dalla carta: i tentativi disponibili tornano al massimo",
        "14:06:06.420  HTTP    POST https://idserver.servizicie.interno.gov.it/idp/Authn/SSL/X509AuthIbrido",
        "14:06:07.235  HTTP    ← HTTP 200 OK in 815 ms, text/plain, 11 caratteri",
        "14:06:07.236  OK      ✔ Codice monouso ricevuto (4 cifre, non registrato)",
    ).map { DiagnosticLog.parseLine("2026-10-31 $it") }

    /** `BackHandler` needs a dispatcher owner, which only an Activity provides. */
    @Composable
    private fun WithBackDispatcher(content: @Composable () -> Unit) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val owner = object : OnBackPressedDispatcherOwner {
            override val lifecycle: Lifecycle = lifecycle
            override val onBackPressedDispatcher = OnBackPressedDispatcher()
        }
        CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner, content = content)
    }
}
