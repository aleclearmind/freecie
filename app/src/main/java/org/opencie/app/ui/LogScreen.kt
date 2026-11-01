package org.opencie.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opencie.app.R
import org.opencie.app.diagnostics.Kind
import org.opencie.app.diagnostics.LogEntry

/** The persistent diagnostic log, newest at the bottom, with share/copy/clear. */
@Composable
internal fun LogScreen(
    entries: List<LogEntry>,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Symbol(R.drawable.ms_arrow_back, 24.dp, colors.onSurface, contentDescription = stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.log_title),
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            stringResource(R.string.log_privacy),
            style = Type.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceContainer),
        ) {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.log_empty),
                    style = Type.body,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                ) {
                    items(entries) { entry -> LogRow(entry) }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PrimaryButton(stringResource(R.string.log_share), onShare, enabled = entries.isNotEmpty(), icon = R.drawable.ms_share)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextPillButton(stringResource(R.string.log_copy), onCopy, Modifier.weight(1f), enabled = entries.isNotEmpty())
                TextPillButton(stringResource(R.string.log_clear), { confirmClear = true }, Modifier.weight(1f), enabled = entries.isNotEmpty())
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.log_clear_title)) },
            text = { Text(stringResource(R.string.log_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text(stringResource(R.string.log_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalExtraColors.current
    val (background, foreground) = when (entry.kind) {
        Kind.PIN -> extra.warningContainer to extra.onWarningContainer
        Kind.PIN_OK, Kind.OK -> colors.primaryContainer to colors.onPrimaryContainer
        Kind.PIN_KO, Kind.ERROR -> colors.errorContainer to colors.onErrorContainer
        else -> Color.Transparent to colors.onSurface
    }
    val dim = entry.kind == Kind.APDU
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            "${entry.time.substringAfter(' ')}  ${entry.kind?.tag.orEmpty()}",
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp),
            color = if (background == Color.Transparent) colors.onSurfaceVariant else foreground,
        )
        Text(
            entry.message,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = if (entry.kind in setOf(Kind.PIN, Kind.PIN_OK, Kind.PIN_KO)) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (dim) colors.onSurfaceVariant else foreground,
        )
    }
}
