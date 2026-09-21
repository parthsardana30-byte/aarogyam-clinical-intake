package `in`.aarogyam.patient.ui.screens.records

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.aarogyam.patient.data.HealthDocument
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.ui.components.AarogyamBackdrop
import `in`.aarogyam.patient.ui.components.l10n
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun DocumentViewerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onDownload: (HealthDocument) -> Unit,
) {
    DisposableEffect(Unit) { onDispose { viewModel.closeDocument() } }
    val preview = viewModel.documentPreview
    val document = preview.document
    val language = viewModel.state.companion.language
    var pageIndex by remember(preview.file) { mutableIntStateOf(0) }
    var pageCount by remember(preview.file) { mutableIntStateOf(0) }
    var pageImage by remember(preview.file) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var renderError by remember(preview.file) { mutableStateOf<String?>(null) }

    LaunchedEffect(preview.file, pageIndex) {
        val file = preview.file ?: return@LaunchedEffect
        pageImage = null
        renderError = null
        try {
            val rendered = withContext(Dispatchers.IO) {
                if (document?.type?.contains("pdf", true) == true) renderPdfPage(file, pageIndex)
                else Pair(1, renderImage(file))
            }
            pageCount = rendered.first
            pageImage = rendered.second.asImageBitmap()
        } catch (error: Exception) {
            renderError = error.message ?: "This document could not be displayed"
        }
    }

    AarogyamBackdrop(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(l10n(language, "Back", "वापस")) }
                Text(document?.name.orEmpty(), modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    color = DeepTeal, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (document != null) {
                    TextButton(onClick = { onDownload(document) }) { Text(l10n(language, "Save", "सहेजें")) }
                }
            }
            when {
                preview.loading || (preview.file != null && pageImage == null && renderError == null) -> {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(l10n(language, "Opening secure document…", "सुरक्षित दस्तावेज़ खुल रहा है…"),
                            color = Muted, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                preview.error != null || renderError != null -> {
                    Text(preview.error ?: renderError.orEmpty(), color = DeepTeal, modifier = Modifier.padding(top = 24.dp))
                }
                pageImage != null -> {
                    if (pageCount > 1) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) {
                                Text(l10n(language, "Previous", "पिछला"))
                            }
                            Text("${pageIndex + 1} / $pageCount", color = DeepTeal)
                            Button(onClick = { pageIndex++ }, enabled = pageIndex + 1 < pageCount) {
                                Text(l10n(language, "Next", "अगला"))
                            }
                        }
                    }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        val bitmap = pageImage!!
                        Image(bitmap, contentDescription = document?.name,
                            modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    }
                }
            }
        }
    }
}

private fun renderPdfPage(file: File, index: Int): Pair<Int, Bitmap> {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(descriptor)
    try {
        require(renderer.pageCount > 0) { "This PDF has no pages" }
        val page = renderer.openPage(index.coerceIn(0, renderer.pageCount - 1))
        try {
            val scale = minOf(2f, 1600f / page.width, 2200f / page.height)
            val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return renderer.pageCount to bitmap
        } finally {
            page.close()
        }
    } finally {
        renderer.close()
        descriptor.close()
    }
}

private fun renderImage(file: File): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 2048 || bounds.outHeight / sampleSize > 2048) sampleSize *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ?: error("This image could not be displayed")
}
