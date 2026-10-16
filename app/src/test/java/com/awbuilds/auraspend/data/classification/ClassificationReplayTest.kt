package com.awbuilds.auraspend.data.classification

import java.io.File
import org.junit.Assume
import org.junit.Test

/**
 * Replays a real inbox export through the classifier, so "does the fix fix this
 * inbox?" is one command instead of archaeology.
 *
 * Opt-in and file-path driven: point `AURASPEND_REPLAY_NOTES` at a file of one
 * message per line (the `Note` column of an app CSV export works for the
 * families whose note is the message text), then run this class. Nothing is
 * committed, nothing is printed but aggregate counts — a real inbox never enters
 * the repository, and the numbers are what a release decision needs:
 *
 * - how many rows the classifier now refuses as informational (the phantoms),
 * - how many it parses, by type,
 * - how many it cannot read at all.
 *
 * Every report of a misclassification should end with a fixture in the golden
 * corpus *and* a run of this replay against the export that produced it.
 */
class ClassificationReplayTest {

    @Test
    fun `replays an export through the ingest gate and the parser`() {
        val path = System.getenv("AURASPEND_REPLAY_NOTES")
        Assume.assumeTrue(
            "set AURASPEND_REPLAY_NOTES to a file of one message per line to replay it",
            !path.isNullOrBlank()
        )
        val messages = File(path!!).readLines().map { it.trim() }.filter { it.isNotEmpty() }

        var vetoed = 0
        var parsed = 0
        var unresolved = 0
        // The safety property of every informational veto: a message that names
        // money actually moving must not be refused. Anything counted here is a
        // candidate lost transaction and worth opening before believing the run.
        var refusedDespiteDebit = 0
        var refusedWithCreditWord = 0
        val refusedFamilies = sortedMapOf<String, Int>()
        val byCategory = sortedMapOf<String, Int>()
        val byType = sortedMapOf<String, Int>()

        fun family(message: String): String = when {
            Regex("(?i)\\b(loyalty|reward|bonus)\\s+points?\\b").containsMatchIn(message) -> "points"
            Regex("(?i)\\b(NAV)\\b").containsMatchIn(message) -> "nav"
            Regex("(?i)\\b(pre-?qualified|income\\s+proof|apply|offer|gift|lucky\\s+draw)\\b").containsMatchIn(message) -> "marketing"
            Regex("(?i)\\bexpir").containsMatchIn(message) -> "expiry"
            Regex("(?i)update\\s+has\\s+been\\s+made").containsMatchIn(message) -> "account-update"
            else -> "other"
        }

        messages.forEach { message ->
            if (SmsAutoClassifier.isOtpOrAlertMessage(message)) {
                vetoed++
                refusedFamilies.merge(family(message), 1, Int::plus)
                // A completed debit must never be refused — that is the lost-money
                // bug this counter exists to catch. A *credit* word is different:
                // "reward points have been credited" is a points balance moving,
                // and refusing it is the point.
                if (BankMessageParser.hasDebitVerb(message)) refusedDespiteDebit++
                else if (BankMessageParser.hasMovementVerb(message)) refusedWithCreditWord++
                return@forEach
            }
            val p = BankMessageParser.parse(message)
            if (p.type == null || p.amount == null) {
                unresolved++
            } else {
                parsed++
                byType.merge(p.type!!.name, 1, Int::plus)
                byCategory.merge(p.categoryId ?: "(none)", 1, Int::plus)
            }
        }

        println("===== REPLAY =====")
        println("messages      : ${messages.size}")
        println("refused as informational/alert : $vetoed")
        println("  refused by family            : $refusedFamilies")
        println("  refused DESPITE a completed debit : $refusedDespiteDebit  <- must be 0")
        println("  refused with a credit word        : $refusedWithCreditWord  <- expected for points/expiry notices")
        println("parsed        : $parsed")
        println("unresolved    : $unresolved")
        println("by type       : $byType")
        println("by category   : $byCategory")
    }
}
