package com.awbuilds.auraspend.ui.designsystem

import com.awbuilds.auraspend.domain.model.hierarchicalCategoryMap
import com.awbuilds.auraspend.ui.classification.UNMAPPED_CATEGORY_ID
import com.awbuilds.auraspend.ui.classification.mapHierarchicalToLocalId
import com.awbuilds.auraspend.ui.classification.taxonomyLabelRes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the design-system rules from AGENTS.md at build time.
 *
 * These rules were documented but never checked, which is why the codebase
 * accumulated 68 hardcoded `fontSize` values and a raw hex in the chrome despite
 * a complete Material3 type scale sitting right next to them. A test is the only
 * enforcement that survives: a review comment does not.
 *
 * The checks are deliberately source-level rather than runtime — they run on every
 * unit-test invocation, need no emulator, and fail in under a second.
 */
class DesignSystemGuardTest {

    private val uiRoot: File = File("src/main/java/com/awbuilds/auraspend/ui")

    /** Files that DEFINE the tokens, so literal sizes/colors are correct there. */
    private val tokenDefinitions = setOf("AuraTokens.kt", "Theme.kt", "Color.kt", "Shape.kt")

    /** A fixed height; `.heightIn(` deliberately does not match. */
    private val fixedHeight = Regex("""\.height\s*\(""")

    /** A text renderer; `TextButton(` and friends deliberately do not match. */
    private val textCall = Regex("""\bText\s*\(""")

    private fun screenFiles(): List<File> =
        uiRoot.walkTopDown()
            .filter { it.extension == "kt" && it.name !in tokenDefinitions }
            .toList()

    private fun assertNoMatches(description: String, pattern: Regex, skipDesignSystem: Boolean) {
        val offenders = screenFiles()
            .filter { !skipDesignSystem || !it.path.contains("${File.separator}designsystem${File.separator}") }
            .filter { file ->
                file.readLines().any { line ->
                    // Ignore commented-out/prose lines so a disabled feature or a doc
                    // comment does not block the build.
                    !line.isCommentLine() && pattern.containsMatchIn(line)
                }
            }
            .map { it.name }
            .distinct()

        assertTrue(
            "$description — found in: ${offenders.joinToString()}. " +
                "Use ui/designsystem tokens instead.",
            offenders.isEmpty()
        )
    }

    @Test
    fun `screens do not hardcode font sizes`() {
        // The type scale in ui/theme/Theme.kt is complete (displayLarge..labelSmall);
        // a literal size bypasses it and breaks large-font accessibility.
        assertNoMatches("Hardcoded `fontSize = N.sp`", Regex("""fontSize\s*=\s*\d+(\.\d+)?\.sp"""), false)
    }

    @Test
    fun `screens do not use raw hex colors`() {
        // Only ui/theme/Color.kt and the token files in designsystem/ may define
        // literal colors; a screen reaching for a hex breaks theming in every
        // non-light mode.
        assertNoMatches("Raw hex color `Color(0x...)`", Regex("""Color\(\s*0x"""), true)
    }

    @Test
    fun `content surfaces do not use Modifier shadow`() {
        // Floating chrome (FAB, sheets) uses the sanctioned `softShadow` helper;
        // content cards must be tonal/outlined, not elevated with a shadow.
        assertNoMatches("Raw `.shadow(` on content", Regex("""\.shadow\s*\("""), true)
    }

    @Test
    fun `no emoji in chrome`() {
        // AGENTS.md rule 5 — the old tag-emoji fallback in CategoryAvatar broke this.
        // Iterates code points rather than using a regex: a surrogate range in a
        // Java character class matches almost everything.
        val offenders = screenFiles()
            .filter { file ->
                file.readLines().any { line -> !line.isCommentLine() && line.containsEmoji() }
            }
            .map { it.name }
            .distinct()
        assertTrue("Emoji in UI code — found in: ${offenders.joinToString()}", offenders.isEmpty())
    }

    @Test
    fun `design system does not fix the height of a scope that renders text`() {
        // AGENTS.md rule 5. `AuraSegmentedControl` kept its option labels inside a
        // `.height(44.dp)` box, so at the largest system font scale the labels
        // clipped — on all five screens that use the control. The source-level shape
        // of that bug is a fixed height and a `Text(` in the same scope; an explicit
        // `Spacer` height is the one exception, because a Spacer holds nothing.
        //
        // Honest limits: this reads source text, so it cannot measure a real layout,
        // it treats a class/object body as one scope, and it catches only the shape
        // above. It is a tripwire for the regression, not proof of no clipping.
        val offenders = designSystemFiles().flatMap { file ->
            topLevelScopes(file.readText()).flatMap { scope ->
                val fixedHeights = scope.lines.withIndex().filter { (index, line) ->
                    !line.isCommentLine() &&
                        fixedHeight.containsMatchIn(line) &&
                        !scope.lines.sizesASpacer(index)
                }
                val rendersText = scope.lines.any { !it.isCommentLine() && textCall.containsMatchIn(it) }
                if (fixedHeights.isEmpty() || !rendersText) {
                    emptyList()
                } else {
                    fixedHeights.map { "${file.name}:${scope.name}" }
                }
            }
        }.distinct()

        assertTrue(
            "Fixed `.height(` in a scope that also renders text — found in: ${offenders.joinToString()}. " +
                "Use `heightIn(min = …)` so it survives a 200% font scale (`Spacer` heights are exempt).",
            offenders.isEmpty()
        )
    }

    /** Files whose rules the design system enforces on itself. */
    private fun designSystemFiles(): List<File> =
        File(uiRoot, "designsystem")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .toList()

    /** True if any code point is a pictographic symbol or emoji. */
    private fun String.containsEmoji(): Boolean {
        var i = 0
        while (i < length) {
            val cp = codePointAt(i)
            val isEmoji = when {
                cp in 0x1F300..0x1FAFF -> true   // pictographs, symbols, extended-A
                cp in 0x1F000..0x1F2FF -> true   // mahjong, dominoes, enclosed
                cp in 0x2600..0x27BF -> true     // misc symbols + dingbats
                cp in 0xFE00..0xFE0F -> true     // variation selectors
                cp in 0x1F1E6..0x1F1FF -> true   // regional indicators (flags)
                else -> false
            }
            if (isEmoji) return true
            i += Character.charCount(cp)
        }
        return false
    }

    @Test
    fun `design system has an error state to go with its empty state`() {
        // A failure must be visually distinct from "no data yet" — a user staring at
        // a bank balance needs to tell the two apart.
        val feedback = File(uiRoot, "designsystem/Feedback.kt")
        val state = File(uiRoot, "designsystem/State.kt")
        val all = listOf(feedback, state).filter { it.exists() }.joinToString("\n") { it.readText() }
        assertTrue("AuraErrorState is missing from the design system", all.contains("fun AuraErrorState"))
    }

    @Test
    fun `ui has no empty catch blocks`() {
        // AGENTS.md hard requirement 1. Empty catches swallow real failures.
        val offenders = screenFiles()
            .filter { file ->
                file.readText().contains(Regex("""catch\s*\([^)]*\)\s*\{\s*\}"""))
            }
            .map { it.name }
            .distinct()
        assertTrue("Empty catch block — found in: ${offenders.joinToString()}", offenders.isEmpty())
    }
}

/** True for a line that is prose rather than code (line or block comment). */
internal fun String.isCommentLine(): Boolean {
    val t = trimStart()
    return t.startsWith("//") || t.startsWith("*") || t.startsWith("/")
}

/** A `{ … }` block opened at brace depth 0: a top-level function, class or object body. */
private data class SourceScope(val name: String, val lines: List<String>)

private val funName = Regex("""\bfun\s+(\w+)""")

/**
 * The brace-depth-0 blocks of [source], as (name, lines) pairs.
 *
 * A scan, not a parser: comments and string/char literals are blanked out first, so
 * a brace inside them cannot shift the depth. Only a block opened at depth 0 starts
 * a scope, so a class's members count as part of the class and a nested lambda
 * belongs to the function around it. Unbalanced input yields no scope rather than an
 * exception — the compiler is the thing that reports an unbalanced file.
 */
private fun topLevelScopes(source: String): List<SourceScope> {
    val lines = source.lines()
    val scopes = mutableListOf<SourceScope>()
    var depth = 0
    var scopeStart = -1
    var scopeName = "?"
    var pendingName: String? = null
    var pendingStart = -1
    var inBlockComment = false

    lines.forEachIndexed { index, raw ->
        val (code, endsInBlockComment) = raw.withoutCommentsAndLiterals(inBlockComment)
        inBlockComment = endsInBlockComment
        // Updated on every declaration, so an expression-bodied function before the
        // block below cannot leave its own name on it.
        funName.find(code)?.let {
            pendingName = it.groupValues[1]
            pendingStart = index
        }
        code.forEach { char ->
            when (char) {
                '{' -> {
                    if (depth == 0) {
                        // Start the scope at the declaration, not at the brace, so a
                        // fixed height in the signature is part of the same scope.
                        scopeStart = if (pendingStart in 0..index) pendingStart else index
                        scopeName = pendingName ?: "?"
                    }
                    depth++
                }
                '}' -> if (depth > 0) {
                    depth--
                    if (depth == 0) {
                        if (scopeStart in 0..index) {
                            scopes += SourceScope(scopeName, lines.subList(scopeStart, index + 1))
                        }
                        scopeStart = -1
                        pendingName = null
                        pendingStart = -1
                    }
                }
            }
        }
    }
    return scopes
}

/**
 * [this] line with comments and the contents of string/char literals removed.
 *
 * The second value is whether the line ends inside a block comment; pass it back in
 * for the next line. String templates (`"${…}"`) are dropped along with their
 * braces, which is safe because they are balanced by construction; triple-quoted
 * strings are skipped as one unit.
 */
private fun String.withoutCommentsAndLiterals(inBlockComment: Boolean): Pair<String, Boolean> {
    val out = StringBuilder()
    var i = 0
    var block = inBlockComment
    while (i < length) {
        val c = this[i]
        when {
            block -> if (c == '*' && i + 1 < length && this[i + 1] == '/') {
                block = false
                i++
            }
            c == '/' && i + 1 < length && this[i + 1] == '/' -> return out.toString() to false
            c == '/' && i + 1 < length && this[i + 1] == '*' -> {
                block = true
                i++
            }
            c == '"' && i + 2 < length && this[i + 1] == '"' && this[i + 2] == '"' -> {
                i += 2
                while (i + 2 < length && !(this[i] == '"' && this[i + 1] == '"' && this[i + 2] == '"')) i++
                i += 2
            }
            c == '"' -> {
                i++
                while (i < length && this[i] != '"') {
                    if (this[i] == '\\') i++
                    i++
                }
            }
            c == '\'' -> {
                i++
                while (i < length && this[i] != '\'') {
                    if (this[i] == '\\') i++
                    i++
                }
            }
            else -> out.append(c)
        }
        i++
    }
    return out.toString() to block
}

/** True when the fixed height at [index] sizes a `Spacer`, which holds nothing. */
private fun List<String>.sizesASpacer(index: Int): Boolean =
    this[index].contains("Spacer(") || (index > 0 && this[index - 1].contains("Spacer("))

/**
 * Guards the taxonomy join between `hierarchicalCategoryMap` (English display names,
 * with no ids of its own) and the local categories those branches file into.
 *
 * `mapHierarchicalToLocalId` used to match with `contains(...)`, so renaming a
 * branch in the taxonomy filed every transaction under it into `cat_other` without
 * a trace — a month of spending in the wrong bucket, noticed late if at all. These
 * assertions fail the build instead. They live in this file because it is the
 * source-level guard suite.
 */
class TaxonomyMappingGuardTest {

    /** The id each branch already resolved to before the exact-match table. */
    private val pinnedBranchIds = mapOf(
        "Food & Dining" to "cat_food",
        "Transport" to "cat_transport",
        "Shopping" to "cat_shopping",
        "Bills & Utilities" to "cat_bills",
        "Entertainment" to "cat_entertainment",
        "Healthcare" to "cat_healthcare",
        "Education" to "cat_education",
        "Subscriptions" to "cat_subscription",
        "Travel & Accommodation" to "cat_transport",
        "Personal Care" to "cat_personal_care",
        "Transfers" to "cat_transfer",
        "Salary & Income" to "cat_income"
    )

    @Test
    fun `every taxonomy branch still files into the id it filed into before`() {
        val missing = hierarchicalCategoryMap.keys - pinnedBranchIds.keys
        assertTrue(
            "The taxonomy gained a branch with no pinned id: ${missing.joinToString()}. " +
                "Add it to branchCategoryIds in HierarchicalCategorySelector.kt and pin it here.",
            missing.isEmpty()
        )

        val drifted = pinnedBranchIds.filter { (branch, id) -> mapHierarchicalToLocalId(branch) != id }
        assertTrue(
            "Taxonomy branch ids changed for: ${drifted.keys.joinToString()}. Existing " +
                "transactions store these ids, so changing one re-files history.",
            drifted.isEmpty()
        )
    }

    @Test
    fun `no taxonomy branch silently falls back to cat_other`() {
        val unmapped = hierarchicalCategoryMap.keys
            .filter { mapHierarchicalToLocalId(it) == UNMAPPED_CATEGORY_ID }
        assertTrue(
            "These taxonomy branches have no mapping and would land in $UNMAPPED_CATEGORY_ID: " +
                "${unmapped.joinToString()}",
            unmapped.isEmpty()
        )
    }

    @Test
    fun `every branch and leaf has a localized label`() {
        val names = hierarchicalCategoryMap.entries.flatMap { (parent, leaves) ->
            listOf(parent) + leaves
        }
        val unlabelled = names.filter { taxonomyLabelRes(it) == null }
        assertTrue(
            "No string resource for: ${unlabelled.joinToString()}. Add one to values/ and " +
                "values-hi/ and map it in HierarchicalCategorySelector.kt.",
            unlabelled.isEmpty()
        )
    }

    @Test
    fun `an unrecognised branch degrades instead of crashing`() {
        assertEquals(UNMAPPED_CATEGORY_ID, mapHierarchicalToLocalId("Renamed Branch"))
    }
}

/**
 * A screen state can carry an error the screen never renders.
 *
 * This happened twice: `DashboardViewState.error` and `BudgetViewState.error` were
 * both populated by their ViewModels and then silently dropped, so a failed load
 * rendered as an empty-looking screen and a failed save made the sheet simply stop
 * responding. Nothing failed loudly, so nothing was caught.
 *
 * The rule: if a screen's state class declares an `error` field, some composable in
 * the same feature must read it.
 */
class ErrorStateWiringTest {

    private val featureDir = File("src/main/java/com/awbuilds/auraspend/ui")

    private fun featuresWithErrorState(): List<Pair<String, String>> =
        (featureDir.listFiles()?.toList() ?: emptyList())
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.listFiles { f -> f.name.endsWith("Mvi.kt") }
                    ?.toList()
                    .orEmpty()
                    .filter { it.readText().contains("val error:") }
                    .map { dir.name to dir.absolutePath }
            }

    @Test
    fun `every screen state that declares an error is actually rendered`() {
        val unrendered = featuresWithErrorState().filter { (feature, statePath) ->
            val screens = featureDir.resolve(feature)
                .listFiles { f -> f.name.endsWith("Screen.kt") || f.name.endsWith("Tab.kt") }
                ?.toList()
                .orEmpty()
            // The MVI file itself declares the field, so exclude it from the search.
            val screenText = screens
                .filterNot { it.absolutePath == statePath }
                .joinToString("\n") { it.readText() }
            !screenText.contains(".error")
        }.map { it.first }

        assertTrue(
            "These features declare an error state that no composable reads: " +
                "${unrendered.joinToString()}. Render it or remove the field.",
            unrendered.isEmpty()
        )
    }

    @Test
    fun `no screen renders a raw exception message as user-facing error text`() {
        // The ViewModels used to do `error = e.message`, which put things like
        // "SQLiteConstraintException: ..." in front of users. Screen state must carry
        // a typed UiError whose string is resolved from resources.
        val offenders = featureDir.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file ->
                // Skip comment lines: UiError.kt documents the old bug in its KDoc.
                file.readLines().any { line ->
                    !line.isCommentLine() &&
                        Regex("""error\s*=\s*[a-zA-Z]*\.message""").containsMatchIn(line)
                }
            }
            .map { it.name }
            .distinct()
            .toList()

        assertTrue(
            "Raw exception message assigned to screen state — found in: " +
                "${offenders.joinToString()}. Use UiError and log the cause instead.",
            offenders.isEmpty()
        )
    }
}
