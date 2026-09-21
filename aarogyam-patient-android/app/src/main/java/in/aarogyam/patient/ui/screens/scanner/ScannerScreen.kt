package `in`.aarogyam.patient.ui.screens.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.ui.AppLanguage
import `in`.aarogyam.patient.ui.components.AarogyamBackdrop
import `in`.aarogyam.patient.ui.components.ScreenIntro
import `in`.aarogyam.patient.ui.components.l10n
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.Leaf
import `in`.aarogyam.patient.ui.theme.Mint
import `in`.aarogyam.patient.ui.theme.Muted
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private enum class ScannerStatus { Tap, Opening, Scanned, Success, Cancelled, Unavailable }

@Composable
internal fun ScannerScreen(language: AppLanguage, busy: Boolean, error: String?, onQrScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var scannerStatus by remember { mutableStateOf(ScannerStatus.Tap) }
    val options = remember {
        GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
    }
    val scanner = remember(context, options) { GmsBarcodeScanning.getClient(context, options) }

    fun startScanner() {
        scannerStatus = ScannerStatus.Opening
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue
                scannerStatus = if (value.isNullOrBlank()) ScannerStatus.Scanned else ScannerStatus.Success
                if (!value.isNullOrBlank()) onQrScanned(value)
            }
            .addOnCanceledListener { scannerStatus = ScannerStatus.Cancelled }
            .addOnFailureListener { scannerStatus = ScannerStatus.Unavailable }
    }

    LaunchedEffect(Unit) { if (!busy) startScanner() }

    AarogyamBackdrop(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenIntro(
                    l10n(language, "At the hospital", "अस्पताल में"),
                    l10n(language, "Scan hospital QR", "अस्पताल का QR स्कैन करें"),
                    l10n(language, "First scan the staff authorization QR. After approval, scan the AI check-up QR.", "पहले स्टाफ का अनुमति QR स्कैन करें। मंज़ूरी के बाद AI चेक-अप QR स्कैन करें।"),
                )
            }
            item { ScannerViewport(scannerStatus, language, ::startScanner) }
            if (busy) item { Text(l10n(language, "Checking hospital QR…", "अस्पताल का QR जांच रहे हैं…"), color = Forest, fontWeight = FontWeight.SemiBold) }
            if (error != null) item { Text(error, color = Color(0xFFB42318), fontWeight = FontWeight.Medium) }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Mint)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Shield, null, tint = Forest)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(l10n(language, "", ""), color = DeepTeal, fontWeight = FontWeight.Bold)
                            Text(
                                l10n(language, "", ""),
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerViewport(status: ScannerStatus, language: AppLanguage, onStartScan: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onStartScan),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = DeepTeal),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(390.dp).background(DeepTeal)) {
            Box(Modifier.align(Alignment.Center).size(244.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val corner = size.width * .22f
                    val stroke = 6.dp.toPx()
                    val frame = Path().apply {
                        moveTo(corner, 0f); lineTo(0f, 0f); lineTo(0f, corner)
                        moveTo(size.width - corner, 0f); lineTo(size.width, 0f); lineTo(size.width, corner)
                        moveTo(0f, size.height - corner); lineTo(0f, size.height); lineTo(corner, size.height)
                        moveTo(size.width - corner, size.height); lineTo(size.width, size.height); lineTo(size.width, size.height - corner)
                    }
                    drawPath(frame, Leaf, style = Stroke(stroke, cap = StrokeCap.Round))
                    val pulse = Path().apply {
                        moveTo(size.width * .14f, size.height * .52f)
                        lineTo(size.width * .35f, size.height * .52f)
                        lineTo(size.width * .43f, size.height * .36f)
                        lineTo(size.width * .57f, size.height * .69f)
                        lineTo(size.width * .67f, size.height * .47f)
                        lineTo(size.width * .86f, size.height * .47f)
                    }
                    drawPath(pulse, Leaf, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(l10n(language, "Align the QR inside the frame", "QR को फ्रेम के अंदर रखें"), color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(scannerStatusText(status, language), color = Leaf, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
            }
            Row(Modifier.align(Alignment.TopCenter).padding(top = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Leaf))
                Text(l10n(language, "SCANNER READY", "स्कैनर तैयार"), color = Leaf, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 8.dp))
            }
            IconButton(
                onClick = {},
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)),
            ) { Icon(Icons.Default.FlashlightOn, l10n(language, "Flashlight", "फ्लैशलाइट"), tint = Color.White) }
        }
    }
}

private fun scannerStatusText(status: ScannerStatus, language: AppLanguage): String = when (status) {
    ScannerStatus.Tap -> l10n(language, "Tap the frame to scan", "स्कैन करने के लिए फ्रेम पर टैप करें")
    ScannerStatus.Opening -> l10n(language, "Opening secure camera…", "सुरक्षित कैमरा खुल रहा है…")
    ScannerStatus.Scanned -> l10n(language, "QR scanned", "QR स्कैन हो गया")
    ScannerStatus.Success -> l10n(language, "QR scanned · checking access", "QR स्कैन हुआ · एक्सेस जांच रहे हैं")
    ScannerStatus.Cancelled -> l10n(language, "Scan cancelled · tap to try again", "स्कैन रद्द हुआ · फिर से टैप करें")
    ScannerStatus.Unavailable -> l10n(language, "Scanner unavailable · tap to try again", "स्कैनर उपलब्ध नहीं · फिर से टैप करें")
}
