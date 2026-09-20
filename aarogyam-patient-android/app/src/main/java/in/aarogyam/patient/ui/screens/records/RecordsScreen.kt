package `in`.aarogyam.patient.ui.screens.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.data.Dashboard
import `in`.aarogyam.patient.data.HealthDocument
import `in`.aarogyam.patient.data.PatientVisit
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.ui.components.AarogyamBackdrop
import `in`.aarogyam.patient.ui.components.EmptyCard
import `in`.aarogyam.patient.ui.components.ScreenIntro
import `in`.aarogyam.patient.ui.components.SectionTitle
import `in`.aarogyam.patient.ui.components.l10n
import `in`.aarogyam.patient.ui.theme.Border
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.Leaf
import `in`.aarogyam.patient.ui.theme.Mint
import `in`.aarogyam.patient.ui.theme.Muted

@Composable
internal fun RecordsScreen(
    viewModel: MainViewModel,
    dashboard: Dashboard?,
    onPickDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val documents = dashboard?.documents.orEmpty()
    val visits = dashboard?.visits.orEmpty()
    val language = viewModel.state.companion.language

    AarogyamBackdrop(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenIntro(
                    eyebrow = l10n(language, "Private & organized", "निजी और व्यवस्थित"),
                    title = l10n(language, "Medical files", "मेडिकल फाइलें"),
                    subtitle = l10n(language, "Your reports, prescriptions and scans in one place.", "आपकी रिपोर्ट, पर्चे और स्कैन एक ही जगह पर।"),
                )
            }
            item { UploadFileCard(language, onPickDocument) }
            if (visits.isNotEmpty()) {
                item { SectionTitle(l10n(language, "Hospital visits", "अस्पताल विज़िट"), null) {} }
                items(visits, key = { "visit-${it.id}" }) { visit -> VisitCard(visit, language) }
            }
            item { SectionTitle(l10n(language, "Your records", "आपके रिकॉर्ड"), null) {} }
            if (documents.isEmpty()) {
                item { EmptyCard(l10n(language, "No medical files yet", "अभी कोई मेडिकल फाइल नहीं"), l10n(language, "Upload a report or prescription to keep it here.", "रिपोर्ट या पर्चा यहां रखने के लिए अपलोड करें।")) }
            } else {
                items(documents, key = { it.id }) { document -> MedicalFileCard(document, language) }
            }
        }
    }
}

@Composable
private fun VisitCard(visit: PatientVisit, language: `in`.aarogyam.patient.ui.AppLanguage) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .95f)),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Mint),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Description, null, tint = Forest) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(visit.hospitalName, color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(visit.hospitalLocation.ifBlank { visit.createdAt.take(10) }, color = Muted, fontSize = 12.sp)
                }
                Text(visit.createdAt.take(10), color = Muted, fontSize = 11.sp)
            }
            val reference = listOfNotNull(
                visit.encounterNumber.takeIf { it.isNotBlank() }?.let { "Encounter $it" },
                visit.uhid.takeIf { it.isNotBlank() }?.let { "UHID $it" },
            ).joinToString(" · ")
            if (reference.isNotBlank()) Text(reference, color = Forest, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 11.dp))
            visit.doctor?.let {
                Text("${it.name} · ${it.specialty}", color = DeepTeal, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
            }
            visit.vitals?.let {
                Text(
                    "BP ${it.systolic}/${it.diastolic} · SpO₂ ${it.oxygenSaturation}% · ${it.heartRate} bpm",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            val status = if (visit.evaluationStatus == "completed") {
                l10n(language, "Consultation completed", "परामर्श पूरा हुआ")
            } else {
                l10n(language, "Awaiting hospital update", "अस्पताल अपडेट की प्रतीक्षा")
            }
            Text(status, color = Forest, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            visit.summary?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = DeepTeal, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun UploadFileCard(language: `in`.aarogyam.patient.ui.AppLanguage, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Mint),
        border = BorderStroke(1.dp, Leaf.copy(alpha = .38f)),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Forest),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.UploadFile, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(l10n(language, "Upload medical record", "मेडिकल रिकॉर्ड अपलोड करें"), color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(l10n(language, "PDF, prescription, report or scan", "PDF, पर्चा, रिपोर्ट या स्कैन"), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Forest)
        }
    }
}

@Composable
private fun MedicalFileCard(document: HealthDocument, language: `in`.aarogyam.patient.ui.AppLanguage) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .95f)),
        border = BorderStroke(1.dp, Border),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(Mint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (document.type.contains("pdf", true)) Icons.Default.PictureAsPdf else Icons.Default.Description, null, tint = Forest)
            }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(document.name, color = DeepTeal, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(document.type.substringAfterLast('/').uppercase(), color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${l10n(language, "Added", "जोड़ा गया")} ${document.createdAt.take(10)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Forest.copy(alpha = .65f))
        }
    }
}
