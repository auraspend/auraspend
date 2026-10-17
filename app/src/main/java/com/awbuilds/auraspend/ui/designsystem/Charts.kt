package com.awbuilds.auraspend.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** A single donut slice. Kept tiny and allocation-free at draw time. */
data class AuraSlice(
    val label: String,
    val value: Double,
    val color: Color
)

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
