package com.awbuilds.auraspend.data.classification

import java.io.File
import java.util.Base64
import org.junit.Test

/**
 * Evaluation harness: runs the **production** classifier over an external
 * corpus and writes one result line per message.
 *
 * The corpus is a TSV of base64-encoded fields, so nothing has to be escaped
 * and the file can hold any message text:
 *
 *     <base64 body>\t<base64 sender>
 *
 * and the output adds the parsed fields:
 *
 *     <base64 body>\t<base64 sender>\t<amount>\t<type>\t<merchant>\t<categoryId>\t<date>\t<confidence>
 *
 * Both paths are given as system properties, and the test asserts nothing: it
 * is a tool, not a gate.
 *
 *     AURASPEND_CORPUS=/tmp/corpus.tsv AURASPEND_OUT=/tmp/auraspend.tsv \
 *       ./gradlew :app:testProdDebugUnitTest --tests '*ExternalCorpusEval*'
 */
class ExternalCorpusEvalTest {

    @Test
    fun `evaluate an external corpus`() {
        // Environment variables, not -D: the test JVM is a separate process and
        // does not inherit the Gradle process's system properties.
        val corpusPath = System.getenv("AURASPEND_CORPUS")
            ?: System.getProperty("auraspend.corpus")
            ?: return
        val outPath = System.getenv("AURASPEND_OUT")
            ?: System.getProperty("auraspend.out")
            ?: return

        val decoder = Base64.getDecoder()
        val encoder = Base64.getEncoder()
        val out = File(outPath).bufferedWriter()

        var parsed = 0
        var withAmount = 0
        File(corpusPath).forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val parts = line.split("\t")
            val body = String(decoder.decode(parts[0]), Charsets.UTF_8)
            val sender = parts.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?.let { String(decoder.decode(it), Charsets.UTF_8) }

            val parsedMessage = TransactionClassifier.classify(body, sender)
            parsed++
            if (parsedMessage.amount != null) withAmount++

            val fields = listOf(
                encoder.encodeToString(body.toByteArray(Charsets.UTF_8)),
                sender?.let { encoder.encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: "",
                parsedMessage.amount?.toString() ?: "",
                parsedMessage.type?.name ?: "",
                parsedMessage.merchant ?: "",
                parsedMessage.categoryId ?: "",
                parsedMessage.date?.toString() ?: "",
                parsedMessage.confidence.toString()
            )
            out.write(fields.joinToString("\t"))
            out.newLine()
        }
        out.flush()
        out.close()
        println("EXTERNAL CORPUS: $parsed messages, $withAmount with an amount -> $outPath")
    }
}
