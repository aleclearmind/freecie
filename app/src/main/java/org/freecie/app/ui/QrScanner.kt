package org.freecie.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import org.freecie.app.R

/** Full-screen camera preview with the mockup's overlay; reports the first QR code. */
@Composable
internal fun QrScanner(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnResult by rememberUpdatedState(onResult)

    val barcodeView = remember {
        BarcodeView(context).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            decodeSingle(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    result.text?.let { currentOnResult(it) }
                }

                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) = Unit
            })
        }
    }
    DisposableEffect(lifecycle, barcodeView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> barcodeView.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            barcodeView.pause()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF25282D)),
    ) {
        AndroidView(factory = { barcodeView }, modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = onClose,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = .55f)),
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .size(48.dp),
        ) {
            Symbol(R.drawable.ms_close, 24.dp, Color.White, contentDescription = stringResource(R.string.close))
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(
                stringResource(R.string.scan_prompt),
                style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Viewfinder(Modifier.size(248.dp))
        }
    }
}

/** Four rounded corner brackets, 44 dp arms, 4 dp white stroke. */
@Composable
private fun Viewfinder(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val arm = 44.dp.toPx() - stroke / 2
        val radius = 20.dp.toPx() - stroke / 2
        val inset = stroke / 2
        val corner = Path().apply {
            moveTo(inset, inset + arm)
            lineTo(inset, inset + radius)
            arcTo(Rect(Offset(inset, inset), Size(radius * 2, radius * 2)), 180f, 90f, false)
            lineTo(inset + arm, inset)
        }
        for (angle in listOf(0f, 90f, 180f, 270f)) {
            rotate(angle) { drawPath(corner, Color.White, style = Stroke(stroke, cap = StrokeCap.Round)) }
        }
    }
}
