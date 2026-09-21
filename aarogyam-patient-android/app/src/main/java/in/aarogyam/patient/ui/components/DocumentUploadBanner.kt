package `in`.aarogyam.patient.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.ui.DocumentUploadState
import `in`.aarogyam.patient.ui.theme.Border
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.Muted

@Composable
internal fun DocumentUploadBanner(status: DocumentUploadState, onChooseAgain: () -> Unit) {
    if (!status.uploading && status.error == null && !status.completed) return
    val failed = status.error != null && !status.completed
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (failed) Color(0xFFFFF4F2) else Color(0xFFF0F8F5)),
        border = BorderStroke(1.dp, if (failed) Color(0xFFF0CBC4) else Border)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (status.uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Forest)
            Column(Modifier.weight(1f).padding(start = if (status.uploading) 11.dp else 0.dp)) {
                Text(when {
                    status.uploading -> "Uploading ${status.fileName.ifBlank { "document" }}"
                    failed -> "Upload did not finish"
                    else -> "Saved to your medical files"
                }, color = if (failed) Color(0xFF9B2C20) else DeepTeal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                status.error?.let { Text(it, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
            }
            if (failed) TextButton(onClick = onChooseAgain) { Text("Try again", color = Forest) }
        }
    }
}
