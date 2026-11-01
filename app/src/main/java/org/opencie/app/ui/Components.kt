package org.opencie.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opencie.app.R

internal object Type {
    val headline = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 17.sp)
    val button = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val textButton = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
}

/** A Material Symbols Rounded vector from `res/drawable/ms_*.xml`. */
@Composable
internal fun Symbol(
    @DrawableRes icon: Int,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(painterResource(icon), contentDescription, modifier.size(size), tint = tint)
}

/**
 * A screen with an optional top bar, scrollable content that fills the
 * remaining height (so spacers can push it around), and pinned bottom actions.
 */
@Composable
internal fun ScreenFrame(
    contentPadding: PaddingValues,
    contentArrangement: Arrangement.Vertical,
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        topBar?.invoke()
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(contentPadding),
                verticalArrangement = contentArrangement,
                content = content,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = bottom,
        )
    }
}

@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        if (icon != null) {
            Symbol(icon, 22.dp, androidx.compose.material3.LocalContentColor.current)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = Type.button)
    }
}

@Composable
internal fun OutlinedPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) { Text(text, style = Type.button) }
}

@Composable
internal fun TextPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) { Text(text, style = Type.textButton) }
}

/** Amber "think before you continue" note. */
@Composable
internal fun WarningCallout(text: String, modifier: Modifier = Modifier) {
    val extra = LocalExtraColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extra.warningContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(R.drawable.ms_warning_fill, 22.dp, extra.warningIcon)
        Text(text, style = Type.bodySmall, color = extra.onWarningContainer)
    }
}

/** Red alert with a bold title and an optional explanation. */
@Composable
internal fun SevereAlert(title: String, body: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.errorContainer)
            .padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Symbol(R.drawable.ms_gpp_maybe_fill, 24.dp, colors.onErrorContainer)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
                color = colors.onErrorContainer,
            )
            if (body != null) {
                Text(body, style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp), color = colors.onErrorContainer)
            }
        }
    }
}

/** Round tile with a centred symbol (code and error screens). */
@Composable
internal fun IconTile(@DrawableRes icon: Int, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) { Symbol(icon, 30.dp, content) }
}

internal enum class StepState { DONE, CURRENT, PENDING }

@Composable
internal fun StepItem(label: String, state: StepState) {
    val colors = MaterialTheme.colorScheme
    val inProgress = stringResource(R.string.step_current)
    val rowModifier = when (state) {
        StepState.CURRENT -> Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
        else -> Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    }
    Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            StepState.DONE -> Symbol(
                R.drawable.ms_check_circle_fill, 22.dp, colors.primary,
                contentDescription = stringResource(R.string.step_done),
            )
            StepState.CURRENT -> Box(
                Modifier
                    .size(22.dp)
                    .semantics { contentDescription = inProgress },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = colors.onPrimaryContainer,
                    trackColor = LocalExtraColors.current.progressTrack,
                    strokeWidth = 2.5.dp,
                )
            }
            StepState.PENDING -> Symbol(
                R.drawable.ms_radio_button_unchecked, 22.dp, colors.outline,
                contentDescription = stringResource(R.string.step_pending),
            )
        }
        Text(
            label,
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = if (state == StepState.CURRENT) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (state == StepState.CURRENT) colors.onPrimaryContainer else colors.onSurfaceVariant,
        )
    }
}

/** The one-time code: one 70×100 cell per digit, Roboto Mono 64 sp. */
@Composable
internal fun CodeCells(code: String, modifier: Modifier = Modifier) {
    val spoken = stringResource(R.string.code_a11y, code.toList().joinToString(", "))
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        // Fixed physical size: the digits are already large and must stay in their cells.
        val digitSize = with(LocalDensity.current) { 64.dp.toSp() }
        code.forEach { digit ->
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 70.dp)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    digit.toString(),
                    fontFamily = RobotoMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = digitSize,
                    color = LocalExtraColors.current.codeInk,
                )
            }
        }
    }
}
