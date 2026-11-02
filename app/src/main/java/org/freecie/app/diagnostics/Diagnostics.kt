package org.freecie.app.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** App-wide access to the diagnostic log; a no-op until [init] (e.g. in JVM tests). */
object Diagnostics {
    @Volatile
    private var log: DiagnosticLog? = null

    fun init(context: Context) {
        if (log != null) return
        synchronized(this) {
            if (log == null) log = DiagnosticLog(File(context.filesDir, "logs"))
        }
    }

    fun add(kind: Kind, message: String) {
        log?.add(kind, message)
    }

    fun entries(): List<LogEntry> = log?.entries().orEmpty()

    fun text(): String = log?.text().orEmpty()

    fun clear() {
        log?.clear()
    }

    /** "FreeCIE 0.1.0 (1), Android 14 (API 34), Google Pixel 7". */
    fun environment(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = info.versionCode
        return "FreeCIE ${info.versionName} ($versionCode), Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /** Writes the log with a short header to a file for sharing. */
    fun exportForSharing(context: Context): File {
        val dir = File(context.cacheDir, "shared-logs").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val now = Date()
        val readable = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY).format(now)
        val zone = SimpleDateFormat("Z", Locale.ROOT).format(now)
        val file = File(dir, "freecie-log-$stamp.txt")
        file.writeText(
            buildString {
                appendLine("Registro diagnostico FreeCIE")
                appendLine(environment(context))
                appendLine("Esportato il $readable, orari locali (UTC$zone)")
                appendLine("Non contiene PIN, codici monouso, valori del QR né dati personali.")
                appendLine()
                append(text())
            },
        )
        return file
    }
}
