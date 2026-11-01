package org.opencie.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opencie.app.R

/** A CIE held flat against the top of a phone's back, with contactless waves. */
@Composable
internal fun HoldCardIllustration(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.hold_illustration_a11y)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(188.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainer)
            .semantics { contentDescription = description },
    ) {
        val cx = size.width / 2
        // Phone, 132×240 dp, cut off by the frame.
        roundRect(colors.surfaceContainerHigh, cx - 66.dp.toPx(), 48.dp.toPx(), 132f, 240f, 24f)
        val stroke = 3.dp.toPx()
        drawRoundRect(
            colors.outlineVariant,
            topLeft = Offset(cx - 66.dp.toPx() + stroke / 2, 48.dp.toPx() + stroke / 2),
            size = Size(132.dp.toPx() - stroke, 240.dp.toPx() - stroke),
            cornerRadius = CornerRadius(24.dp.toPx() - stroke / 2),
            style = Stroke(stroke),
        )
        roundRect(colors.outlineVariant, cx - 66.dp.toPx() + stroke + 14.dp.toPx(), 48.dp.toPx() + stroke + 14.dp.toPx(), 26f, 44f, 10f)

        // Card, 164×104 dp, tilted by -7°.
        val cardLeft = cx - 82.dp.toPx()
        val cardTop = 62.dp.toPx()
        val cardCenter = Offset(cx, cardTop + 52.dp.toPx())
        rotate(-7f, pivot = cardCenter) {
            drawShadowedCard(colors.primary, cardLeft, cardTop)
            translate(cardLeft, cardTop) {
                roundRect(colors.primaryContainer, 16.dp.toPx(), 34.dp.toPx(), 28f, 22f, 5f)
                roundRect(colors.onPrimary.copy(alpha = .55f), 56.dp.toPx(), 36.dp.toPx(), 78f, 6f, 3f)
                roundRect(colors.onPrimary.copy(alpha = .55f), 56.dp.toPx(), 50.dp.toPx(), 52f, 6f, 3f)
                roundRect(colors.onPrimary.copy(alpha = .3f), 16.dp.toPx(), 76.dp.toPx(), 110f, 6f, 3f)
            }
        }

        // Contactless waves above the card.
        listOf(Triple(30f, 40f, 1f), Triple(48f, 26f, .7f), Triple(66f, 12f, .4f)).forEach { (r, top, alpha) ->
            val radius = r.dp.toPx() - stroke / 2
            drawArc(
                color = colors.primary.copy(alpha = alpha),
                startAngle = -135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - radius, top.dp.toPx() + stroke / 2),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** Rounded rectangle; position in px, size and radius in dp. */
private fun DrawScope.roundRect(color: Color, left: Float, top: Float, widthDp: Float, heightDp: Float, radiusDp: Float) {
    drawRoundRect(
        color,
        topLeft = Offset(left, top),
        size = Size(widthDp.dp.toPx(), heightDp.dp.toPx()),
        cornerRadius = CornerRadius(radiusDp.dp.toPx()),
    )
}

private fun DrawScope.drawShadowedCard(color: Color, left: Float, top: Float) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        setShadowLayer(9.dp.toPx(), 0f, 8.dp.toPx(), Color.Black.copy(alpha = .22f).toArgb())
    }
    drawContext.canvas.nativeCanvas.drawRoundRect(
        left, top, left + 164.dp.toPx(), top + 104.dp.toPx(), 12.dp.toPx(), 12.dp.toPx(), paint,
    )
}
