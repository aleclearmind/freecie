package org.freecie.app.diagnostics

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a log line is about; [tag] is what appears in the file. */
enum class Kind(val tag: String) {
    INFO("INFO"),
    STEP("PASSO"),
    APDU("APDU"),
    HTTP("HTTP"),
    /** A PIN event that is neither a success nor a failure (entered, sent). */
    PIN("PIN"),
    PIN_OK("PIN-OK"),
    PIN_KO("PIN-KO"),
    OK("OK"),
    WARN("AVVISO"),
    ERROR("ERRORE"),
    ;

    companion object {
        /** Also reads the English tags written by builds from before the UI became Italian-only. */
        fun fromTag(tag: String): Kind? = entries.firstOrNull { it.tag == tag }
            ?: mapOf("STEP" to STEP, "WARN" to WARN, "ERROR" to ERROR)[tag]
    }
}

data class LogEntry(val time: String, val kind: Kind?, val message: String)

/**
 * Persistent, append-only diagnostic log: one line per event, rotated to a
 * single previous file when it grows past [maxBytes]. Callers must only pass
 * text that is safe to share (see [Redact]).
 */
class DiagnosticLog(
    private val dir: File,
    private val maxBytes: Long = 256 * 1024,
    private val now: () -> Date = ::Date,
) {
    private val current = File(dir, "freecie.log")
    private val previous = File(dir, "freecie.log.1")
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    @Synchronized
    fun add(kind: Kind, message: String) {
        dir.mkdirs()
        if (current.length() > maxBytes) {
            previous.delete()
            current.renameTo(previous)
        }
        // Last line of defence: a PIN is 8 digits, and no legitimate message
        // contains 8 consecutive digits, so mask any such run.
        val oneLine = EIGHT_DIGITS.replace(message.replace("\r", "").replace("\n", " ⏎ ")) { "#".repeat(it.value.length) }
        current.appendText("${timeFormat.format(now())}  ${kind.tag.padEnd(TAG_WIDTH)}  $oneLine\n")
    }

    @Synchronized
    fun text(): String = previous.readOrEmpty() + current.readOrEmpty()

    fun entries(): List<LogEntry> = text().lineSequence().filter { it.isNotBlank() }.map(::parseLine).toList()

    @Synchronized
    fun clear() {
        previous.delete()
        current.delete()
    }

    private fun File.readOrEmpty() = if (exists()) readText() else ""

    companion object {
        private const val TAG_WIDTH = 6
        private const val TIME_LENGTH = 23 // yyyy-MM-dd HH:mm:ss.SSS
        private val EIGHT_DIGITS = Regex("[0-9]{8,}")

        fun parseLine(line: String): LogEntry {
            if (line.length < TIME_LENGTH + 2) return LogEntry("", null, line)
            val time = line.substring(0, TIME_LENGTH)
            val rest = line.substring(TIME_LENGTH).trimStart()
            val tag = rest.substringBefore(' ')
            val kind = Kind.fromTag(tag) ?: return LogEntry(time, null, rest)
            return LogEntry(time, kind, rest.substring(tag.length).trimStart())
        }
    }
}
