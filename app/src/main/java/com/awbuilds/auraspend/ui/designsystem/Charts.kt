package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.awbuilds.auraspend.ui.theme.extendedColors

/** A single donut slice. Kept tiny and allocation-free at draw time. */
data class AuraSlice(
    val label: String,
    val value: Double,
    val color: Color
)

/**
 * Horizontal progress bar — the app's comparison primitive.
 *
 * A ring asks you to estimate an arc against a circle; a bar asks you to compare
 * two lengths. Stacked budgets and ranked spending both need the second, so this
 * is the shape the design system reaches for wherever several values are read
 * against each other. [progress] is not clamped here: the caller decides whether
 * an over-budget value should read as a full bar, and the arc/label honesty rule
 * still applies — the number beside it must be the real one.
 */
@Composable
fun AuraProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    trackColor: Color = MaterialTheme.extendedColors.track,
    height: Dp = 5.dp,
    contentDescription: String? = null
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = AuraMotion.standard(),
        label = "progressBar"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * Daily column strip: one thin bar per day, the most recent in ink, the rest in
 * the track colour. [values] are normalised to 0..1; the strip is decoration
 * around a number the screen also states, so it carries one description rather
 * than a focusable node per bar.
 */
@Composable
fun AuraSparkColumns(
    values: List<Float>,
    modifier: Modifier = Modifier,
    highlightLast: Boolean = true,
    contentDescription: String? = null
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val lastColor = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }
    ) {
        if (values.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        val radius = CornerRadius(3.dp.toPx())
        val minHeight = 2.dp.toPx()
        values.forEachIndexed { index, value ->
            val barHeight = (value.coerceIn(0f, 1f) * size.height).coerceAtLeast(minHeight)
            drawRoundRect(
                color = if (highlightLast && index == values.lastIndex) lastColor else trackColor,
                topLeft = Offset(
                    x = index * (barWidth + gap),
                    y = size.height - barHeight
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )
        }
    }
}

/**
 * Progress ring that animates to [progress] with a spring. Use for budgets and
 * goals; the sweep eases in instead of snapping.
 */
@Composable
fun AuraProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 4.dp,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
    contentDescription: String? = null,
    content: @Composable () -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = AuraMotion.standard(),
        label = "progressRing"
    )

    Box(
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = animated * 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/**
 * Donut chart that sweeps in when its data changes. Slices are separated by
 * small gaps and animate as one settle pass (single float, no per-slice work).
 */
@Composable
fun AuraDonutChart(
    data: List<AuraSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 28.dp,
    gapDegrees: Float = 2.4f,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
    contentDescription: String? = null,
    centerContent: @Composable () -> Unit = {}
) {
    val settle = remember { Animatable(1f) }
    LaunchedEffect(data) {
        settle.snapTo(0f)
        settle.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                AuraMotion.DURATION_CHART,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val total = data.sumOf { it.value }
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.minDimension - strokePx, size.minDimension - strokePx)
            val topLeft = Offset(
                (size.width - arcSize.width) / 2,
                (size.height - arcSize.height) / 2
            )

            if (total <= 0.0 || data.isEmpty()) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Butt)
                )
                return@Canvas
            }

            var cursor = -90f
            data.forEach { slice ->
                val sweep = (slice.value / total * 360.0).toFloat() * settle.value
                val drawableSweep = (sweep - gapDegrees).coerceAtLeast(0.5f)
                drawArc(
                    color = slice.color,
                    startAngle = cursor + gapDegrees / 2,
                    sweepAngle = drawableSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Butt)
                )
                cursor += sweep
            }
        }
        centerContent()
    }
}

/**
 * Smooth area chart that grows from the baseline on first draw.
 *
 * [points] are (timestamp, value) pairs. The fill uses a vertical gradient so
 * the line reads as a volume, not a scribble.
 */
@Composable
fun AuraAreaChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    lineWidth: Dp = 2.5.dp,
    fillTop: Color = lineColor.copy(alpha = 0.26f),
    fillBottom: Color = lineColor.copy(alpha = 0.0f),
    contentDescription: String? = null
) {
    val maxValue = remember(points) { points.maxOfOrNull { it.second } ?: 0.0 }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(points) {
        grow.snapTo(0f)
        grow.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                AuraMotion.DURATION_CHART,
                easing = FastOutSlowInEasing
            )
        )
    }

    Canvas(
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }
    ) {
        val n = points.size
        if (n < 2 || maxValue <= 0.0) return@Canvas

        val stepX = size.width / (n - 1)
        val topPad = 6f
        val usableHeight = size.height - topPad

        val offsets = points.mapIndexed { index, (_, value) ->
            Offset(
                x = index * stepX,
                y = size.height - (value / maxValue).toFloat() * usableHeight
            )
        }

        val linePath = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            for (i in 1 until offsets.size) {
                val p0 = offsets[i - 1]
                val p1 = offsets[i]
                val midX = (p0.x + p1.x) / 2
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(offsets.last().x, size.height)
            lineTo(offsets.first().x, size.height)
            close()
        }

        val fillBrush = Brush.verticalGradient(
            colors = listOf(fillTop, fillBottom),
            startY = 0f,
            endY = size.height
        )

        withTransform({
            scale(scaleX = 1f, scaleY = grow.value, pivot = Offset(0f, size.height))
        }) {
            drawPath(path = areaPath, brush = fillBrush, alpha = grow.value)
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = lineWidth.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
