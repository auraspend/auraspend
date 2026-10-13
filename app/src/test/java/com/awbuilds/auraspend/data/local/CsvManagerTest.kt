package com.awbuilds.auraspend.data.local

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.awbuilds.auraspend.domain.model.RecurrenceFrequency
import com.awbuilds.auraspend.domain.model.Transaction
import com.awbuilds.auraspend.domain.model.TransactionType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Covers the CSV field encoding rules.
 *
 * The export/import round trip was previously lossy: `escapeCsv` wrote `""` for an
 * embedded quote but `parseCsvLine` treated the pair as two quote *toggles* and dropped
 * both, so any merchant or note containing a quote came back altered. These tests lock
 * the round trip, the formula-injection guard, and the malformed-row accounting that
 * replaced a bare `catch (_: Exception) { }`.
 *
 * The last group goes through the real [CsvManager.exportToCsv] and
 * [CsvManager.importFromCsv] against a Robolectric content resolver, because the
 * defects that survived the field-level tests lived *between* the fields: a
 * record reader that split on newlines inside a quoted note, a dropped
 * `recurrenceFrequency` column, and a formula guard that was never stripped on
 * the way back in. A note is only proven to survive if it survives the file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // SDK 34 explicitly, matching the other Robolectric tests: compileSdk is 37,
    // which this Robolectric cannot provision.
    sdk = [34],
    application = com.awbuilds.auraspend.TestApplication::class
)
class CsvManagerTest {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val header =
        "Date,Amount,Type,CategoryId,Note,Merchant,Bank,IsRecurring,RecurrenceFrequency,SourceSmsId\n"

    private fun txn(
        note: String = "Swiggy order",
        merchant: String? = "Swiggy",
        amount: Double = 149.0
    ) = Transaction(
        id = "tx-1",
        amount = amount,
        categoryId = "cat_food",
        note = note,
        merchant = merchant,
        date = LocalDateTime.of(2026, 8, 1, 12, 0),
        type = TransactionType.EXPENSE
    )

    /** Builds the CSV line exactly as the exporter does. */
    private fun exportRow(t: Transaction): String = listOf(
        t.date.format(dateFormatter),
        t.amount.toString(),
        t.type.name,
        CsvManager.escapeCsv(t.categoryId),
        CsvManager.escapeCsv(t.note),
        CsvManager.escapeCsv(t.merchant ?: ""),
        CsvManager.escapeCsv(t.bankName ?: ""),
        t.isRecurring.toString(),
        t.recurrenceFrequency?.name ?: "",
        CsvManager.escapeCsv(t.sourceSmsId ?: "")
    ).joinToString(",")

    /** Parses a CSV line exactly as the importer does. */
    private fun importRow(line: String): Transaction? {
        val parts = CsvManager.parseCsvLine(line)
        return if (parts.size >= 6) CsvManager.parseTransactionRow(parts) else null
    }

    /** Applies the importer's row loop to a set of lines. */
    private fun importLines(lines: List<String>): CsvManager.ImportResult {
        val imported = mutableListOf<Transaction>()
        var skipped = 0
        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val parsed = importRow(line)
            if (parsed == null) skipped++ else imported.add(parsed)
        }
        return CsvManager.ImportResult(imported = imported, skippedRows = skipped)
    }

    private fun roundTrip(transaction: Transaction): Transaction {
        val restored = importRow(exportRow(transaction))
        assertNotNull("row should parse back to a transaction", restored)
        return restored!!
    }

    /** Writes [transactions] through the real exporter and returns the file text. */
    private fun exportFile(transactions: List<Transaction>): String {
        val uri = Uri.parse("content://auraspend.test/transactions.csv")
        val file = ByteArrayOutputStream()
        Shadows.shadowOf(context.contentResolver).registerOutputStream(uri, file)
        assertEquals(transactions.size, CsvManager.exportToCsv(context, transactions, uri))
        return String(file.toByteArray(), Charsets.UTF_8)
    }

    /** Reads [csv] back through the real importer. */
    private fun importFile(csv: String): CsvManager.ImportResult {
        val uri = Uri.parse("content://auraspend.test/transactions.csv")
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))
        return CsvManager.importFromCsv(context, uri)
    }

    /** The real export -> file -> import round trip. */
    private fun fileRoundTrip(transactions: List<Transaction>): CsvManager.ImportResult =
        importFile(exportFile(transactions))

    @Test
    fun `plain fields survive a round trip`() {
        val original = txn()
        val restored = roundTrip(original)
        assertEquals(original.note, restored.note)
        assertEquals(original.merchant, restored.merchant)
        assertEquals(original.amount, restored.amount, 0.001)
        assertEquals(original.categoryId, restored.categoryId)
        assertEquals(original.type, restored.type)
        assertEquals(original.date, restored.date)
    }

    @Test
    fun `embedded double quote survives a round trip`() {
        // The regression: escapeCsv writes "" and the old parser dropped the pair,
        // turning a note like: say "hi" into say hi.
        val original = txn(note = """Order for "Rahul's" party""")
        assertEquals(original.note, roundTrip(original).note)
    }

    @Test
    fun `comma and newline in a note survive a round trip`() {
        val original = txn(note = "groceries, snacks and drinks")
        assertEquals(original.note, roundTrip(original).note)
    }

    @Test
    fun `formula prefix is neutralised so spreadsheets do not execute it`() {
        val encoded = exportRow(txn(note = "=1+1", merchant = "@merchant"))
        assertTrue("export must escape '=' -> got: $encoded", encoded.contains("'=1+1"))
        assertTrue("export must escape '@' -> got: $encoded", encoded.contains("'@merchant"))
    }

    @Test
    fun `formula guard only applies to the leading character`() {
        // A '+' in the middle of a value is harmless and must not be mangled.
        val original = txn(note = "C++ course")
        assertEquals(original.note, roundTrip(original).note)
    }

    @Test
    fun `a quoted formula prefix is still guarded`() {
        assertEquals("'=1+1", CsvManager.escapeCsv("'=1+1"))
        assertTrue(CsvManager.escapeCsv("=cmd()").startsWith("'="))
    }

    @Test
    fun `malformed rows are counted as skipped rather than aborting the import`() {
        val result = importLines(
            listOf(
                // valid
                "2026-08-01 12:00,149.0,EXPENSE,cat_food,Coffee,Swiggy,HDFC,false,,",
                // unparseable date
                "not-a-date,10.0,EXPENSE,cat_food,Coffee,Swiggy,HDFC,false,,",
                // non-numeric amount
                "2026-08-01 12:00,abc,EXPENSE,cat_food,Coffee,Swiggy,HDFC,false,,",
                // unknown enum
                "2026-08-01 12:00,10.0,SIDEWAYS,cat_food,Coffee,Swiggy,HDFC,false,,",
                // blank category
                "2026-08-01 12:00,10.0,EXPENSE,,Coffee,Swiggy,HDFC,false,,",
                // too few columns
                "2026-08-01 12:00,10.0,EXPENSE"
            )
        )
        assertEquals(1, result.imported.size)
        assertEquals(5, result.skippedRows)
        assertEquals(6, result.totalRows)
    }

    @Test
    fun `blank lines are ignored and not counted as skipped`() {
        val result = importLines(
            listOf(
                "2026-08-01 12:00,149.0,EXPENSE,cat_food,Coffee,Swiggy,HDFC,false,,",
                "",
                "   "
            )
        )
        assertEquals(1, result.imported.size)
        assertEquals(0, result.skippedRows)
    }

    @Test
    fun `negative and zero amounts import correctly`() {
        val result = importLines(
            listOf(
                "2026-08-01 12:00,-50.0,EXPENSE,cat_food,Refund,Swiggy,HDFC,false,,",
                "2026-08-02 12:00,0.0,EXPENSE,cat_food,Zero,Swiggy,HDFC,false,,"
            )
        )
        assertEquals(2, result.imported.size)
        assertEquals(-50.0, result.imported[0].amount, 0.001)
        assertEquals(0.0, result.imported[1].amount, 0.001)
    }

    @Test
    fun `null merchant and bank name import as null not empty string`() {
        val result = importLines(
            listOf("2026-08-01 12:00,10.0,EXPENSE,cat_food,Note,,,,,")
        )
        assertEquals(1, result.imported.size)
        assertEquals(null, result.imported.first().merchant)
        assertEquals(null, result.imported.first().bankName)
    }

    // ─── The real file round trip (export -> bytes -> import) ────────────────

    @Test
    fun `a recurring row, a multi-line note and guarded fields survive a file round trip`() {
        // Every field of this row is a case that used to break: the frequency was
        // never read back (column 9 was skipped), the newline split the record in
        // two, and the guard apostrophe stayed in the stored value.
        val recurring = Transaction(
            id = "tx-recurring",
            amount = 499.0,
            categoryId = "cat_subs",
            note = "Annual plan\nrenews on the 1st",
            merchant = "=StreamCo",
            bankName = "HDFC",
            date = LocalDateTime.of(2026, 8, 1, 12, 0),
            type = TransactionType.EXPENSE,
            isRecurring = true,
            recurrenceFrequency = RecurrenceFrequency.MONTHLY,
            sourceSmsId = "sms-42"
        )
        val minusNote = txn(note = "-50 cashback", merchant = "@wallet").copy(amount = 12.5)

        val csv = exportFile(listOf(recurring, minusNote))
        assertTrue(
            "a note with a newline must be quoted, not split: $csv",
            csv.contains("\"Annual plan\nrenews on the 1st\"")
        )
        assertTrue("a '=' merchant must be guarded: $csv", csv.contains("'=StreamCo"))
        assertTrue("a '-' note must be guarded: $csv", csv.contains("'-50 cashback"))

        val result = importFile(csv)
        assertEquals("no row may be skipped", 0, result.skippedRows)
        assertEquals(2, result.imported.size)

        val restored = result.imported.first { it.note.startsWith("Annual") }
        assertEquals(recurring.note, restored.note)
        assertEquals(recurring.merchant, restored.merchant)
        assertEquals(recurring.bankName, restored.bankName)
        assertEquals(recurring.categoryId, restored.categoryId)
        assertEquals(recurring.amount, restored.amount, 0.001)
        assertEquals(recurring.type, restored.type)
        assertEquals(recurring.date, restored.date)
        assertEquals(recurring.isRecurring, restored.isRecurring)
        assertEquals(
            "the recurrence frequency column must be read back",
            RecurrenceFrequency.MONTHLY,
            restored.recurrenceFrequency
        )
        assertEquals(recurring.sourceSmsId, restored.sourceSmsId)

        val guarded = result.imported.first { it.amount == 12.5 }
        assertEquals("-50 cashback", guarded.note)
        assertEquals("@wallet", guarded.merchant)
    }

    @Test
    fun `a quoted note survives even when it is the only row`() {
        val original = txn(note = "line one\nline two, with a comma")
        val result = fileRoundTrip(listOf(original))
        assertEquals(0, result.skippedRows)
        assertEquals(1, result.imported.size)
        assertEquals(original.note, result.imported.first().note)
    }

    @Test
    fun `CRLF records and a CRLF inside a quoted note both import correctly`() {
        // Excel-style line endings. The CRLF *outside* quotes terminates the
        // record; the one inside quotes is part of the note.
        val csv = header.replace("\n", "\r\n") +
            "2026-08-01 12:00,149.0,EXPENSE,cat_food,\"first\r\nsecond\",Swiggy,HDFC,false,,\r\n"
        val result = importFile(csv)
        assertEquals(0, result.skippedRows)
        assertEquals(1, result.imported.size)
        assertEquals("first\r\nsecond", result.imported.first().note)
    }

    @Test
    fun `the header is skipped even when the file has no trailing newline`() {
        val csv = header + "2026-08-01 12:00,149.0,EXPENSE,cat_food,Coffee,Swiggy,HDFC,false,,"
        val result = importFile(csv)
        assertEquals(0, result.skippedRows)
        assertEquals(1, result.imported.size)
        assertEquals("Coffee", result.imported.first().note)
    }

    @Test
    fun `a short legacy row imports without a frequency or a source id`() {
        // A file written before columns 9 and 10 existed: eight columns, and the
        // missing tail must mean "absent", not "malformed".
        val result = importFile(
            header + "2026-08-01 12:00,149.0,EXPENSE,cat_food,Coffee,Swiggy,HDFC,true"
        )
        assertEquals(0, result.skippedRows)
        assertEquals(1, result.imported.size)
        val row = result.imported.first()
        assertEquals(true, row.isRecurring)
        assertEquals(null, row.recurrenceFrequency)
        assertEquals(null, row.sourceSmsId)
    }

    @Test
    fun `an unknown recurrence frequency costs the frequency, not the row`() {
        // "ANNUAL" is a real value seen in the wild (see Mappers.toEnumOrDefault).
        val result = importFile(
            header + "2026-08-01 12:00,149.0,EXPENSE,cat_food,Coffee,Swiggy,HDFC,true,ANNUAL,"
        )
        assertEquals(0, result.skippedRows)
        assertEquals(1, result.imported.size)
        assertEquals(true, result.imported.first().isRecurring)
        assertEquals(null, result.imported.first().recurrenceFrequency)
    }

    @Test
    fun `a leading-space formula is guarded on export and unguarded on import`() {
        // SEC-03: a spreadsheet trims the leading space, so " =1+1" is a formula.
        assertEquals("' =1+1", CsvManager.escapeCsv(" =1+1"))
        assertEquals(" =1+1", CsvManager.unguardFormula("' =1+1"))
        assertEquals(" =1+1", roundTrip(txn(note = " =1+1")).note)
        // Harmless text after whitespace is not guarded.
        assertEquals(" hello", CsvManager.escapeCsv(" hello"))
    }

    @Test
    fun `the guard apostrophe is stripped only in front of a formula character`() {
        assertEquals("'=1+1", CsvManager.escapeCsv("=1+1"))
        assertEquals("=1+1", CsvManager.unguardFormula("'=1+1"))
        assertEquals("'quoted'", CsvManager.unguardFormula("'quoted'"))
        assertEquals("'", CsvManager.unguardFormula("'"))
        assertEquals("", CsvManager.unguardFormula(""))
        // Guarding is idempotent: an already-guarded value is not guarded twice.
        assertEquals("'=1+1", CsvManager.escapeCsv("'=1+1"))
    }

    @Test
    fun `a value that literally starts with an apostrophe and a formula character is ambiguous`() {
        // Documented, not accidental: the guard and a literal apostrophe are the
        // same bytes, and the export format has no escape for the latter. The
        // guard wins, because guessing the other way would let a formula through
        // on every export.
        assertEquals("=1+1", roundTrip(txn(note = "'=1+1")).note)
    }
}
