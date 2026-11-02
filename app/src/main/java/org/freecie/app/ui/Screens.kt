package org.freecie.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.freecie.app.LoginFailure
import org.freecie.app.NfcStatus
import org.freecie.app.QrLoginRequest
import org.freecie.app.R
import org.freecie.app.Retry
import org.freecie.app.Screen
import org.freecie.app.Step
import org.freecie.app.diagnostics.LogEntry
import java.net.URI

private const val PIN_LENGTH = 8

@Composable
fun FreeCieApp(
    screen: Screen,
    nfcStatus: NfcStatus,
    onScanQr: () -> Unit,
    onScanned: (String) -> Unit,
    onPasteLink: (String) -> Unit,
    onOpenNfcSettings: () -> Unit,
    onSubmitPin: (CharSequence) -> Unit,
    onRetryPin: (QrLoginRequest) -> Unit,
    onReset: () -> Unit,
    logEntries: List<LogEntry> = emptyList(),
    onOpenLog: () -> Unit = {},
    onCloseLog: () -> Unit = {},
    onShareLog: () -> Unit = {},
    onCopyLog: () -> Unit = {},
    onClearLog: () -> Unit = {},
) {
    BackHandler(enabled = screen != Screen.Home, onBack = if (screen is Screen.Log) onCloseLog else onReset)
    if (screen == Screen.Scan) {
        QrScanner(onResult = onScanned, onClose = onReset)
        return
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (screen) {
                Screen.Home, Screen.Scan -> HomeScreen(nfcStatus, onScanQr, onPasteLink, onOpenNfcSettings, onOpenLog)
                is Screen.Pin -> PinScreen(screen, onSubmitPin, onReset)
                is Screen.Tap -> HoldCardScreen(screen.step, onReset)
                is Screen.Code -> CodeScreen(screen.otp, onReset)
                is Screen.Failed -> ErrorScreen(
                    screen,
                    onRetry = {
                        when (val retry = screen.retry) {
                            Retry.Rescan -> onScanQr()
                            is Retry.Pin -> onRetryPin(retry.request)
                            Retry.None -> onReset()
                        }
                    },
                    onCancel = onReset,
                    onShowLog = onOpenLog,
                )
                is Screen.Log -> LogScreen(logEntries, onCloseLog, onShareLog, onCopyLog, onClearLog)
            }
        }
    }
}

// 01 · Home

@Composable
private fun HomeScreen(
    nfcStatus: NfcStatus,
    onScanQr: () -> Unit,
    onPasteLink: (String) -> Unit,
    onOpenNfcSettings: () -> Unit,
    onOpenLog: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var pasting by remember { mutableStateOf(false) }
    val usable = nfcStatus != NfcStatus.MISSING
    ScreenFrame(
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        contentArrangement = Arrangement.spacedBy(14.dp),
        bottom = {
            PrimaryButton(stringResource(R.string.scan_qr), onScanQr, enabled = usable, icon = R.drawable.ms_qr_code_scanner)
            TextPillButton(stringResource(R.string.paste_link), { pasting = true }, enabled = usable)
            Text(
                stringResource(R.string.unofficial_notice),
                style = Type.caption,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Symbol(R.drawable.ms_contactless, 20.dp, colors.onPrimaryContainer) }
            Text(
                stringResource(R.string.app_name),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenLog, modifier = Modifier.size(48.dp)) {
                Symbol(
                    R.drawable.ms_receipt_long, 24.dp, colors.onSurfaceVariant,
                    contentDescription = stringResource(R.string.log_title),
                )
            }
        }
        Text(stringResource(R.string.home_title), style = Type.headline)

        when (nfcStatus) {
            NfcStatus.READY -> Unit
            NfcStatus.DISABLED -> NfcOffBanner(onOpenNfcSettings)
            NfcStatus.MISSING -> NfcMissingBanner()
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(R.string.home_step1, R.string.home_step2, R.string.home_step3, R.string.home_step4)
                .forEachIndexed { index, step -> NumberedStep(index + 1, stringResource(step)) }
        }
    }
    if (pasting) {
        PasteLinkDialog(
            onDismiss = { pasting = false },
            onSubmit = {
                pasting = false
                onPasteLink(it)
            },
        )
    }
}

@Composable
private fun NfcOffBanner(onOpenNfcSettings: () -> Unit) {
    val extra = LocalExtraColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(extra.warningContainer)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Symbol(R.drawable.ms_nfc, 22.dp, extra.warningIcon)
            Text(
                stringResource(R.string.nfc_disabled),
                style = Type.body.copy(fontWeight = FontWeight.Medium),
                color = extra.onWarningContainer,
            )
        }
        TextButton(
            onClick = onOpenNfcSettings,
            modifier = Modifier
                .align(Alignment.End)
                .height(44.dp),
        ) {
            Text(
                stringResource(R.string.nfc_open_settings),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = extra.onWarningContainer,
            )
        }
    }
}

@Composable
private fun NfcMissingBanner() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.errorContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(R.drawable.ms_error_fill, 22.dp, colors.onErrorContainer)
        Text(
            stringResource(R.string.nfc_missing),
            style = Type.body.copy(fontWeight = FontWeight.Medium),
            color = colors.onErrorContainer,
        )
    }
}

@Composable
private fun NumberedStep(number: Int, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = colors.primary,
            )
        }
        Text(text, style = Type.body, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun PasteLinkDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_link)) },
        text = {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = { Text(stringResource(R.string.paste_link_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(link) }, enabled = link.isNotBlank()) {
                Text(stringResource(R.string.continue_button))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// 03 · Request + PIN

@Composable
private fun PinScreen(screen: Screen.Pin, onSubmitPin: (CharSequence) -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Deliberately not rememberSaveable: the PIN must never reach saved state.
    var pin by remember(screen) { mutableStateOf("") }
    var tooShort by remember(screen) { mutableStateOf(false) }
    val attemptsLeft = screen.attemptsLeft
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Open with the field focused and the numeric keyboard up; on leaving the
    // screen, drop the typed digits and close the keyboard.
    LaunchedEffect(screen) {
        focus.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(screen) {
        onDispose {
            pin = ""
            keyboard?.hide()
        }
    }

    fun submit() {
        if (pin.length != PIN_LENGTH) {
            tooShort = true
            return
        }
        keyboard?.hide()
        onSubmitPin(pin)
        pin = ""
    }

    ScreenFrame(
        modifier = Modifier.imePadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        contentArrangement = Arrangement.spacedBy(16.dp),
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                    Symbol(R.drawable.ms_close, 24.dp, colors.onSurface, contentDescription = stringResource(R.string.cancel))
                }
            }
        },
        bottom = {
            PrimaryButton(stringResource(R.string.continue_button), ::submit, enabled = pin.length == PIN_LENGTH)
            TextPillButton(stringResource(R.string.cancel), onCancel)
        },
    ) {
        Text(stringResource(R.string.request_title), style = Type.headline)
        ServiceCard(screen.request.spName)
        WarningCallout(stringResource(R.string.request_warning))

        val error = when {
            tooShort -> stringResource(R.string.pin_invalid)
            attemptsLeft == 1 -> stringResource(R.string.pin_reenter)
            attemptsLeft != null -> stringResource(R.string.pin_attempts_left, attemptsLeft)
            else -> null
        }
        OutlinedTextField(
            value = pin,
            onValueChange = { value ->
                pin = value.filter(Char::isDigit).take(PIN_LENGTH)
                tooShort = false
            },
            label = { Text(stringResource(R.string.pin_label)) },
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(fontSize = 22.sp, letterSpacing = 0.32.em),
            // Password type: keyboards must not learn, suggest or store it.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            singleLine = true,
            isError = error != null,
            trailingIcon = if (error != null) {
                { Symbol(R.drawable.ms_error_fill, 22.dp, colors.error) }
            } else {
                null
            },
            supportingText = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (error != null) {
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (tooShort) Symbol(R.drawable.ms_error_fill, 16.dp, colors.error)
                            Text(error, color = colors.error)
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text("${pin.length}/$PIN_LENGTH", color = colors.onSurfaceVariant)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .focusRequester(focus),
        )
        if (attemptsLeft == 1) {
            SevereAlert(
                title = stringResource(R.string.pin_attempts_left, 1),
                body = stringResource(R.string.pin_last_attempt),
            )
        }
    }
}

@Composable
private fun ServiceCard(spName: String?) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.request_service),
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em),
            color = colors.onSurfaceVariant,
        )
        Text(
            serviceLabel(spName) ?: stringResource(R.string.request_unknown_service),
            style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        )
        Row(
            Modifier
                .padding(top = 6.dp)
                .height(32.dp)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Symbol(R.drawable.ms_id_card, 18.dp, colors.primary)
            Text(
                stringResource(R.string.request_level3),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** `SPName` is often an entity ID such as `https://iam.example.it/shibboleth`. */
private fun serviceLabel(spName: String?): String? {
    if (spName.isNullOrBlank()) return null
    return runCatching { URI(spName).host }.getOrNull() ?: spName
}

// 04 · Hold the card

@Composable
private fun HoldCardScreen(step: Step?, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ScreenFrame(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        contentArrangement = Arrangement.spacedBy(16.dp),
        bottom = { OutlinedPillButton(stringResource(R.string.cancel), onCancel) },
    ) {
        HoldCardIllustration()
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.tap_title),
                style = TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium),
            )
            Text(stringResource(R.string.tap_body), style = Type.bodySmall, color = colors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val current = step?.ordinal ?: -1
            listOf(
                Step.CONNECTING to R.string.step_connecting,
                Step.SECURE_CHANNEL to R.string.step_secure_channel,
                Step.CERTIFICATE to R.string.step_certificate,
                Step.SIGNING to R.string.step_signing,
                Step.SERVER to R.string.step_server,
            ).forEach { (s, label) ->
                StepItem(
                    stringResource(label),
                    when {
                        s.ordinal < current -> StepState.DONE
                        s.ordinal == current -> StepState.CURRENT
                        else -> StepState.PENDING
                    },
                )
            }
        }
    }
}

// 05 · Code

@Composable
private fun CodeScreen(otp: String, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ScreenFrame(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        contentArrangement = Arrangement.spacedBy(24.dp),
        bottom = { PrimaryButton(stringResource(R.string.done), onDone) },
    ) {
        // Mockup: flexible space 1 above, 1.4 below.
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTile(R.drawable.ms_computer, colors.primaryContainer, colors.onPrimaryContainer)
            Text(
                stringResource(R.string.otp_title),
                style = TextStyle(fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
            )
        }
        CodeCells(otp)
        Text(
            stringResource(R.string.otp_body),
            style = Type.body,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1.4f))
    }
}

// 06 · Error

@Composable
private fun ErrorScreen(screen: Screen.Failed, onRetry: () -> Unit, onCancel: () -> Unit, onShowLog: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val canRetry = screen.retry != Retry.None
    ScreenFrame(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        contentArrangement = Arrangement.Top,
        bottom = {
            if (canRetry) {
                PrimaryButton(stringResource(R.string.retry), onRetry)
                TextPillButton(stringResource(R.string.cancel), onCancel)
            } else {
                OutlinedPillButton(stringResource(R.string.cancel), onCancel)
            }
            TextPillButton(stringResource(R.string.log_show), onShowLog)
        },
    ) {
        // Mockup: flexible space 1 above, 2 below.
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTile(failureIcon(screen.failure), colors.errorContainer, colors.onErrorContainer)
            Text(stringResource(R.string.error_title), style = Type.headline)
            Text(
                failureMessage(screen.failure),
                style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(2f))
    }
}

private fun failureIcon(failure: LoginFailure): Int = when (failure) {
    LoginFailure.QrInvalid, is LoginFailure.QrForeignHost, is LoginFailure.QrNotLevel3 -> R.drawable.ms_qr_code
    LoginFailure.PinBlocked, is LoginFailure.WrongPin -> R.drawable.ms_lock
    LoginFailure.TagLost -> R.drawable.ms_contactless
    LoginFailure.NotACie, is LoginFailure.Card,
    LoginFailure.CertificateExpired, LoginFailure.CertificateRevoked,
    -> R.drawable.ms_id_card
    LoginFailure.ExtendedApduUnsupported -> R.drawable.ms_nfc
    LoginFailure.NoNetwork -> R.drawable.ms_wifi_off
    is LoginFailure.Http, is LoginFailure.Refused -> R.drawable.ms_cloud_off
    is LoginFailure.Unexpected -> R.drawable.ms_error_fill
}

@Composable
private fun failureMessage(failure: LoginFailure): String = when (failure) {
    LoginFailure.QrInvalid -> stringResource(R.string.error_qr_invalid)
    is LoginFailure.QrForeignHost -> stringResource(R.string.error_qr_host, failure.host)
    is LoginFailure.QrNotLevel3 -> stringResource(R.string.error_qr_level, failure.level)
    is LoginFailure.WrongPin -> stringResource(R.string.pin_attempts_left, failure.attemptsLeft)
    LoginFailure.PinBlocked -> stringResource(R.string.error_pin_blocked)
    LoginFailure.NotACie -> stringResource(R.string.error_not_cie)
    LoginFailure.ExtendedApduUnsupported -> stringResource(R.string.error_extended_apdu)
    LoginFailure.TagLost -> stringResource(R.string.error_tag_lost)
    LoginFailure.NoNetwork -> stringResource(R.string.error_no_network)
    LoginFailure.CertificateExpired -> stringResource(R.string.error_cert_expired)
    LoginFailure.CertificateRevoked -> stringResource(R.string.error_cert_revoked)
    is LoginFailure.Http -> stringResource(R.string.error_http, failure.code)
    is LoginFailure.Refused -> stringResource(R.string.error_server, failure.message)
    is LoginFailure.Card -> stringResource(R.string.error_card, failure.detail)
    is LoginFailure.Unexpected -> stringResource(R.string.error_generic, failure.detail)
}
