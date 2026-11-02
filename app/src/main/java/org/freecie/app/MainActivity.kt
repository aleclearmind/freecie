package org.freecie.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import org.freecie.app.diagnostics.Diagnostics
import org.freecie.app.diagnostics.Kind
import org.freecie.app.diagnostics.LogEntry
import org.freecie.app.diagnostics.Redact
import org.freecie.app.ui.FreeCieApp
import org.freecie.app.ui.FreeCieTheme

enum class NfcStatus { READY, DISABLED, MISSING }

class MainActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private var nfcStatus by mutableStateOf(NfcStatus.READY)
    private var readerEnabled = false
    private var loggedNfcStatus: NfcStatus? = null
    private var logEntries by mutableStateOf(emptyList<LogEntry>())

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Diagnostics.add(Kind.INFO, "Permesso fotocamera concesso")
            viewModel.openScanner()
        } else {
            Diagnostics.add(Kind.WARN, "Permesso fotocamera negato")
            Toast.makeText(this, R.string.camera_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.init(this)
        if (savedInstanceState == null) Diagnostics.add(Kind.INFO, "App avviata: ${Diagnostics.environment(this)}")
        enableEdgeToEdge()
        // Keep the PIN and the one-time code out of screenshots and recents,
        // and away from autofill (password managers) and content capture.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        }
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            FreeCieTheme {
                val wantsCard = viewModel.wantsCard
                LaunchedEffect(wantsCard) { updateReaderMode() }
                FreeCieApp(
                    screen = viewModel.screen,
                    nfcStatus = nfcStatus,
                    onScanQr = ::startScanner,
                    onScanned = viewModel::onScanned,
                    onPasteLink = viewModel::onPasted,
                    onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                    onSubmitPin = viewModel::submitPin,
                    onRetryPin = viewModel::retryPin,
                    onReset = viewModel::reset,
                    logEntries = logEntries,
                    onOpenLog = {
                        logEntries = Diagnostics.entries()
                        viewModel.openLog()
                    },
                    onCloseLog = viewModel::closeLog,
                    onShareLog = ::shareLog,
                    onCopyLog = ::copyLog,
                    onClearLog = {
                        Diagnostics.clear()
                        logEntries = emptyList()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter
        nfcStatus = when {
            adapter == null -> NfcStatus.MISSING
            !adapter.isEnabled -> NfcStatus.DISABLED
            else -> NfcStatus.READY
        }
        if (nfcStatus != loggedNfcStatus) {
            loggedNfcStatus = nfcStatus
            val label = when (nfcStatus) {
                NfcStatus.READY -> "pronto"
                NfcStatus.DISABLED -> "disattivato"
                NfcStatus.MISSING -> "assente"
            }
            Diagnostics.add(if (nfcStatus == NfcStatus.READY) Kind.INFO else Kind.WARN, "NFC: $label")
        }
        updateReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableReaderMode()
    }

    private fun startScanner() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.openScanner() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    /** Reader mode is on only while the app waits for, or talks to, the card. */
    private fun updateReaderMode() {
        val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (viewModel.wantsCard && resumed) enableReaderMode() else disableReaderMode()
    }

    private fun enableReaderMode() {
        if (readerEnabled) return
        val adapter = nfcAdapter ?: return
        runCatching {
            adapter.enableReaderMode(
                this,
                { tag -> viewModel.onTagDiscovered(tag) },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null,
            )
            readerEnabled = true
            Diagnostics.add(Kind.INFO, "Lettura NFC attiva: in attesa della carta")
        }.onFailure { Diagnostics.add(Kind.ERROR, "Impossibile attivare la lettura NFC: ${Redact.throwable(it)}") }
    }

    private fun disableReaderMode() {
        if (!readerEnabled) return
        runCatching { nfcAdapter?.disableReaderMode(this) }
        readerEnabled = false
        Diagnostics.add(Kind.INFO, "Lettura NFC disattivata")
    }

    private fun shareLog() {
        val file = Diagnostics.exportForSharing(this)
        val uri = FileProvider.getUriForFile(this, "$packageName.logs", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_share_subject))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        startActivity(Intent.createChooser(send, getString(R.string.log_share)))
    }

    private fun copyLog() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.log_title), "${Diagnostics.environment(this)}\n\n${Diagnostics.text()}"),
        )
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
        }
    }
}
