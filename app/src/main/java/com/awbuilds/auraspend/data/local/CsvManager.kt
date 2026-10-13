package com.awbuilds.auraspend.data.local

import android.content.Context
import android.net.Uri
import com.awbuilds.auraspend.core.AuraLog
import com.awbuilds.auraspend.domain.model.RecurrenceFrequency
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import kotlinx.coroutines.CancellationException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object CsvManager {

    private const val TAG = "CsvManager"

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private const val CSV_HEADER =
        "Date,Amount,Type,CategoryId,Note,Merchant,Bank,IsRecurring,RecurrenceFrequency,SourceSmsId\n"

    /** Outcome of an import so the UI can report what actually happened. */
    data class ImportResult(
        val imported: List<Transaction>,
        val skippedRows: Int
    ) {
        val totalRows: Int get() = imported.size + skippedRows
    }

    /**
     * Writes the export straight to the user-picked SAF [uri].
     *
     * There is deliberately no public-Downloads fallback: on API 30+ an app
     * cannot write there without MANAGE_EXTERNAL_STORAGE, so that path threw
     * FileNotFoundException on every launch configuration. If [uri] cannot be
     * opened the caller is told via the return value instead of a crash.
     *
     * @return the number of rows written, or -1 if the stream could not be opened.
     */
    fun exportToCsv(context: Context, transactions: List<Transaction>, uri: Uri): Int {
        val stream = try {
            context.contentResolver.openOutputStream(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuraLog.e(TAG, "Could not open export destination", e)
            null
        }
        if (stream == null) {
            AuraLog.e(TAG, "Export destination unavailable: $uri")
            return -1
        }

        return try {
            OutputStreamWriter(stream, Charsets.UTF_8).buffered().use { writer ->
                writer.write(CSV_HEADER)
                transactions.forEach { t ->
                    writer.write(
                        buildString {
                            append(t.date.format(DATE_FORMATTER))
                            append(",")
                            append(t.amount)
                            append(",")
                            append(t.type.name)
                            append(",")
                            append(escapeCsv(t.categoryId))
                            append(",")
                            append(escapeCsv(t.note))
                            append(",")
                            append(escapeCsv(t.merchant ?: ""))
                            append(",")
                            append(escapeCsv(t.bankName ?: ""))
                            append(",")
                            append(t.isRecurring)
                            append(",")
                            append(t.recurrenceFrequency?.name ?: "")
                            append(",")
                            append(escapeCsv(t.sourceSmsId ?: ""))
                            append("\n")
                        }
                    )
                }
                writer.flush()
            }
            transactions.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuraLog.e(TAG, "CSV export failed", e)
            -1
        }
    }

    /**
     * Parses a CSV previously produced by [exportToCsv]. Malformed rows are
     * counted and skipped rather than aborting the whole import, and the result
     * reports both numbers so the UI can tell the user what was dropped.
     *
     * Reading is record-based, not line-based: a quoted field may contain
     * newlines (the exporter quotes them on purpose), so splitting on `\n` first
     * shredded such a note into two malformed rows. Missing trailing columns are
     * tolerated, which keeps files written by older versions importable.
     */
    fun importFromCsv(context: Context, uri: Uri): ImportResult {
        val transactions = mutableListOf<Transaction>()
        var skipped = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    // Skip header (one record, so a quoted first row cannot
                    // desynchronise the reader).
                    readRecord(reader)

                    var record = readRecord(reader)
                    while (record != null) {
                        if (record.isNotBlank()) {
                            val parts = parseCsvLine(record)
                            if (parts.size < 6) {
                                skipped++
                            } else {
                                val parsed = parseTransactionRow(parts)
                                if (parsed == null) skipped++ else transactions.add(parsed)
                            }
                        }
                        record = readRecord(reader)
                    }
                }
            } ?: throw IllegalStateException("Could not open import file: $uri")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep whatever parsed before the failure; the count is reported to the user.
            AuraLog.e(TAG, "CSV import failed", e)
        }

        return ImportResult(imported = transactions, skippedRows = skipped)
    }

    /**
     * Reads one CSV record, which may span several lines when a quoted field
     * contains a newline, and returns null at end of file.
     *
     * A bare `\n`, `\r` or `\r\n` outside quotes terminates the record; anything
     * inside quotes is kept verbatim (RFC 4180), which is what makes the
     * export/import pair lossless for a multi-line note.
     */
    private fun readRecord(reader: BufferedReader): String? {
        val record = StringBuilder()
        var inQuotes = false
        var started = false

        while (true) {
            val read = reader.read()
            if (read == -1) return if (started) record.toString() else null
            val char = read.toChar()
            started = true
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    record.append(char)
                }
                !inQuotes && (char == '\n' || char == '\r') -> {
                    // A CRLF pair is one terminator: consume the LF so the next
                    // record does not start with a phantom blank line.
                    if (char == '\r') {
                        reader.mark(1)
                        if (reader.read() != '\n'.code) reader.reset()
                    }
                    return record.toString()
                }
                else -> record.append(char)
            }
        }
    }

    /** Returns null for any row that cannot be represented as a [Transaction]. */
    internal fun parseTransactionRow(parts: List<String>): Transaction? {
        val date = try {
            LocalDateTime.parse(parts[0], DATE_FORMATTER)
        } catch (_: Exception) {
            return null
        }
        val amount = parts[1].toDoubleOrNull() ?: return null
        val type = try {
            TransactionType.valueOf(parts[2])
        } catch (_: Exception) {
            return null
        }
        val categoryId = unguardFormula(parts[3])
        if (categoryId.isBlank()) return null

        return Transaction(
            id = UUID.randomUUID().toString(),
            amount = amount,
            categoryId = categoryId,
            note = unguardFormula(parts[4]),
            merchant = unguardFormula(parts[5]).ifBlank { null },
            bankName = unguardFormula(parts.getOrElse(6) { "" }).ifBlank { null },
            date = date,
            type = type,
            isRecurring = parts.getOrElse(7) { "false" }.toBoolean(),
            // Column 9 of the export (index 8). Without this the frequency was
            // dropped on every re-import and a recurring row lost its period.
            recurrenceFrequency = parseRecurrenceFrequency(parts.getOrElse(8) { "" }),
            sourceSmsId = unguardFormula(parts.getOrElse(9) { "" }).ifBlank { null }
        )
    }

    /**
     * An unknown or blank frequency costs the row its frequency, never the row:
     * a legacy or hand-edited file must still import its amount and date.
     */
    private fun parseRecurrenceFrequency(raw: String): RecurrenceFrequency? {
        if (raw.isBlank()) return null
        return try {
            RecurrenceFrequency.valueOf(raw)
        } catch (e: Exception) {
            AuraLog.w(TAG, "Unknown recurrence frequency '$raw' in CSV — importing without it", e)
            null
        }
    }

    /**
     * Quotes a field when it contains a delimiter, quote or newline, and
     * neutralises spreadsheet formula injection (`=`, `+`, `-`, `@`).
     *
     * The guard fires on the first *non-whitespace* character: a field like
     * `" =1+1"` is a formula to a spreadsheet once it trims the leading space,
     * and guarding only on `value.first()` let that through.
     */
    internal fun escapeCsv(value: String): String {
        val firstMeaningful = value.trimStart().firstOrNull()
        val guarded = if (firstMeaningful != null && firstMeaningful in FORMULA_PREFIXES) {
            "'$value"
        } else {
            value
        }
        return if (guarded.contains(",") || guarded.contains("\"") || guarded.contains("\n") || guarded.contains("\r")) {
            "\"${guarded.replace("\"", "\"\"")}\""
        } else {
            guarded
        }
    }

    /**
     * Undoes the spreadsheet formula guard [escapeCsv] adds.
     *
     * Symmetric with the export rule: a leading apostrophe is a guard only when
     * the value behind it starts (after leading whitespace) with a formula
     * character. Anything else is user data and is returned untouched, so a note
     * like `'quoted'` survives. The one ambiguous input is a value that literally
     * begins with `'` followed by a formula character — it is indistinguishable
     * from a guarded formula, and the guard wins; the export format has no escape
     * for that case, and inventing one would change the file format.
     */
    internal fun unguardFormula(value: String): String {
        if (!value.startsWith("'")) return value
        val body = value.substring(1)
        val firstMeaningful = body.trimStart().firstOrNull()
        return if (firstMeaningful != null && firstMeaningful in FORMULA_PREFIXES) body else value
    }

    /**
     * RFC 4180 field splitter. A doubled quote inside a quoted field is a
     * literal quote, so `""` must not be treated as two quote toggles — the
     * previous implementation dropped it, which broke the export/import
     * round trip for any merchant containing a quote.
     */
    internal fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        result.add(current.toString())
        return result
    }

    private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')
}
