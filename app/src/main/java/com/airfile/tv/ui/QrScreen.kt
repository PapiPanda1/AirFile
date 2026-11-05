package com.airfile.tv.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.airfile.tv.AirFileState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

private val Navy = Color(0xFF0B1B3A)
private val Muted = Color(0xFFB8C4DC)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AirFileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QrScreen(onChooseFolder: () -> Unit) {
    val url by AirFileState.url.collectAsStateWithLifecycle()
    val received by AirFileState.received.collectAsStateWithLifecycle()
    val location by AirFileState.location.collectAsStateWithLifecycle()
    val active by AirFileState.active.collectAsStateWithLifecycle()
    val error by AirFileState.error.collectAsStateWithLifecycle()

    val qr = remember(url) { url?.let { qrBitmap(it).asImageBitmap() } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(horizontal = 64.dp, vertical = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (qr != null) {
                    Image(bitmap = qr, contentDescription = "QR code", modifier = Modifier.fillMaxSize().padding(10.dp))
                } else {
                    Text(
                        text = "Connect this TV to Wi-Fi or Ethernet",
                        color = Color.Black,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = url?.substringBefore("?t=") ?: "No network",
                color = Muted,
                fontSize = 18.sp,
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("AirFile", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            Text(
                "Scan the QR code with your phone's camera, then pick any file to send it to this TV.",
                color = Color.White,
                fontSize = 22.sp,
            )
            Text("Your phone and TV must be on the same Wi-Fi network.", color = Muted, fontSize = 18.sp)
            Text(location, color = Muted, fontSize = 18.sp)
            error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 18.sp) }

            Button(onClick = onChooseFolder, modifier = Modifier.focusRequester(focus)) {
                Text("Choose save folder")
            }

            if (active > 0) {
                Text("Receiving $active file(s)…", color = Color(0xFF8AB4F8), fontSize = 20.sp)
            }

            if (received.isNotEmpty()) {
                Text("Received", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    items(received) { file ->
                        Text(
                            text = "✓ ${file.name}  (${Format.humanSize(file.bytes)})",
                            color = Color.White,
                            fontSize = 18.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 2.dp).width(700.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
