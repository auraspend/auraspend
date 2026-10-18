package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awbuilds.auraspend.ui.theme.extendedColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Subtle elevation for floating chrome (FAB, sheets) — never content cards. */
fun Modifier.softShadow(
    shape: Shape,
    elevation: Dp = 6.dp
): Modifier = composed {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    this.shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = if (dark) Color(0x2E000000) else Color(0x1A1C1B1F),
        spotColor = if (dark) Color(0x40000000) else Color(0x33201A24)
    )
}

private fun Color.luminance(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue

/**
 * Uppercase micro-label: the date line, "TOTAL BALANCE", a section overline.
 *
 * The caps and the wide tracking are a *style*, so the transform lives here
 * rather than in the string resources — the strings stay normal sentences and
 * the same label can be reused un-capitalised elsewhere.
 */
@Composable
fun AuraEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    // `faint`, not `muted`: an eyebrow labels the thing below it and must lose
    // to it. Muted is the meta-text grey (a row's category line); faint is the
    // label grey, one step quieter again.
    color: Color = MaterialTheme.extendedColors.faint
) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = remember(text, locale) { text.uppercase(locale) },
        style = AuraType.eyebrow,
        color = color,
        modifier = modifier
    )
}

/** "October" in the user's locale — the month the screen is describing. */
@Composable
fun rememberMonthName(): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM", locale))
    }
}

/** Section header with an optional trailing slot. */
@Composable
fun AuraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.gutter)
            // 10dp below the head, none above: the gap *between* blocks is the
            // block's own 26dp top margin, and padding both sides of the header
            // made the rhythm depend on which block came first.
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = AuraType.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            // Marked as a heading so TalkBack's heading navigation can jump between
            // sections instead of reading the screen as one flat run of text.
            modifier = Modifier.semantics { heading() }
        )
        trailing?.invoke()
    }
}

/** The trailing action on a section header ("All budgets", "View all"). */
@Composable
fun AuraSectionLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = AuraType.sectionAction,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            // 12dp leading, not symmetric: the link must not sit closer to the
            // screen edge than the section title it belongs to.
            .padding(start = AuraSpacing.md, top = AuraSpacing.xs, bottom = AuraSpacing.xs)
    )
}
