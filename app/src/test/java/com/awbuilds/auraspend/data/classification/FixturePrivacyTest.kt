package com.awbuilds.auraspend.data.classification

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tripwire for fixture hygiene: this repository is public, and every test
 * fixture must be synthetic. Real mobile numbers, production payee names and
 * non-example email addresses arrive silently inside copied fixture strings,
 * and nothing else in the build would notice.
 *
 * The suite's allowed vocabulary:
 *  - mobiles: 9000000042, 9123456789, 9876543210, 9988776655
 *  - emails: only `example.com` (masked forms like `a*****h@example.com` scan
 *    as `h@example.com` and pass)
 *  - names: no denylisted production names anywhere.
 *
 * When this fails, replace the offending fixture value with the vocabulary —
 * never add it to the allow-list unless it is genuinely synthetic.
 */
class FixturePrivacyTest {

    private val mobileNumber = Regex("""(?<!\d)[6-9]\d{9}(?!\d)""")
    private val email = Regex("""[A-Za-z0-9._%+-]{2,}@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val deniedNames = listOf("DINESHBHAI", "ANNAPOORNESWARI", "WARRIER", "ABHIJITH")

    private val allowedMobiles = setOf("9000000042", "9123456789", "9876543210", "9988776655")
    private val allowedEmailDomains = setOf("example.com")
    private val scannedExtensions = setOf("kt", "jsonl", "json", "txt", "md", "xml")

    @Test
    fun `test fixtures contain no real phone numbers emails or names`() {
        val root = sequenceOf(File("src/test"), File("app/src/test"))
            .firstOrNull { it.isDirectory }
            ?: error("FixturePrivacyTest: cannot locate src/test from ${File(".").absolutePath}")

        val problems = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension in scannedExtensions }
            .filter { it.name != "FixturePrivacyTest.kt" }
            .forEach { file ->
                file.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
                    mobileNumber.findAll(line).forEach { m ->
                        if (m.value !in allowedMobiles) {
                            problems += "${file.path}:${index + 1} mobile ${m.value}"
                        }
                    }
                    email.findAll(line).forEach { m ->
                        if (m.value.substringAfterLast('@') !in allowedEmailDomains) {
                            problems += "${file.path}:${index + 1} email ${m.value}"
                        }
                    }
                    deniedNames.forEach { name ->
                        if (line.contains(name, ignoreCase = true)) {
                            problems += "${file.path}:${index + 1} name $name"
                        }
                    }
                }
            }

        assertTrue(
            "fixture privacy violations (use the synthetic vocabulary):\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }
}
