package `in`.aarogyam.patient.ui.screens.profile

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.data.Dashboard
import `in`.aarogyam.patient.data.PatientProfile
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.ui.AppLanguage
import `in`.aarogyam.patient.ui.components.AarogyamBackdrop
import `in`.aarogyam.patient.ui.components.ScreenIntro
import `in`.aarogyam.patient.ui.components.l10n
import `in`.aarogyam.patient.ui.theme.Amber
import `in`.aarogyam.patient.ui.theme.Border
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.Leaf
import `in`.aarogyam.patient.ui.theme.Mint
import `in`.aarogyam.patient.ui.theme.Muted
import `in`.aarogyam.patient.ui.theme.SoftWhite

@Composable
internal fun ProfileScreen(
    viewModel: MainViewModel,
    dashboard: Dashboard?,
    onCompleteProfile: () -> Unit,
    onMedicalConditions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = dashboard?.patient
    val companion = viewModel.state.companion
    val hasAbha = profile?.abhaLinkStatus.equals("linked", ignoreCase = true)
    val conditions = profile?.conditions.orEmpty().map(::friendlyCondition).toSet()

    AarogyamBackdrop(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 34.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item { ScreenIntro(l10n(companion.language, "Your account", "आपका खाता"), l10n(companion.language, "Profile", "प्रोफाइल"), l10n(companion.language, "Personal details and health information.", "व्यक्तिगत विवरण और स्वास्थ्य जानकारी।")) }
            if (!hasAbha) item { CompleteProfileCard(companion.language, onCompleteProfile) }
            item { IdentityCard(profile, viewModel, companion.language) }
            item { MedicalConditionsCard(conditions, companion.language, onMedicalConditions) }
            item {
                Button(
                    onClick = viewModel::logout,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318), contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(19.dp))
                    Text(l10n(companion.language, "Sign out", "साइन आउट"), Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CompleteProfileCard(language: AppLanguage, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6DB)),
        border = BorderStroke(1.dp, Amber.copy(alpha = .35f)),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Badge, null, tint = Amber, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(l10n(language, "Complete your profile", "अपनी प्रोफाइल पूरी करें"), color = DeepTeal, fontWeight = FontWeight.Bold)
                Text(l10n(language, "Review your details and add your ABHA ID", "अपना विवरण देखें और ABHA ID जोड़ें"), color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Amber, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun IdentityCard(profile: PatientProfile?, viewModel: MainViewModel, language: AppLanguage) {
    var expanded by remember { mutableStateOf(false) }
    val fallbackName = viewModel.state.session?.patient?.fullName ?: l10n(language, "Patient", "मरीज")
    val patientId = profile?.id ?: viewModel.state.session?.patient?.id.orEmpty()

    Card(
        modifier = Modifier.animateContentSize(),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .96f)),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(DeepTeal), contentAlignment = Alignment.Center) {
                    Text((profile?.fullName ?: fallbackName).take(1).uppercase(), color = Leaf, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text(profile?.fullName ?: fallbackName, color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(profile?.phone ?: "${l10n(language, "Patient ID", "मरीज ID")} · $patientId", color = Muted, fontSize = 13.sp)
                }
                Icon(Icons.Default.CheckCircle, "Verified patient", tint = Forest, modifier = Modifier.size(22.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 15.dp).clip(RoundedCornerShape(15.dp)).background(Mint).padding(11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicValue(l10n(language, "Blood group", "ब्लड ग्रुप"), profile?.bloodGroup ?: "—")
                BasicValue(l10n(language, "Gender", "लिंग"), profile?.gender ?: "—")
                BasicValue(l10n(language, "Born", "जन्म"), profile?.dateOfBirth ?: "—")
            }
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (expanded) l10n(language, "Hide entered details", "दर्ज विवरण छिपाएं") else l10n(language, "Show entered details", "दर्ज विवरण देखें"), color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Forest)
            }
            if (expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    val notAvailable = l10n(language, "Not available", "उपलब्ध नहीं")
                    val notAdded = l10n(language, "Not added", "जोड़ा नहीं गया")
                    DetailRow(l10n(language, "Full name", "पूरा नाम"), profile?.fullName ?: fallbackName)
                    DetailRow(l10n(language, "Patient ID", "मरीज ID"), patientId.ifBlank { notAvailable })
                    DetailRow(l10n(language, "Phone", "फोन"), profile?.phone ?: notAvailable)
                    DetailRow(l10n(language, "Date of birth", "जन्म तिथि"), profile?.dateOfBirth ?: notAdded)
                    DetailRow(l10n(language, "Gender", "लिंग"), profile?.gender ?: notAdded)
                    DetailRow(l10n(language, "Height", "लंबाई"), profile?.heightCm?.takeIf { it > 0 }?.let { "${it.toInt()} cm" } ?: notAdded)
                    DetailRow(l10n(language, "Weight", "वजन"), profile?.weightKg?.takeIf { it > 0 }?.let { "${it.toInt()} kg" } ?: notAdded)
                    DetailRow(l10n(language, "Blood group", "ब्लड ग्रुप"), profile?.bloodGroup ?: notAdded)
                    DetailRow(l10n(language, "Allergies", "एलर्जी"), profile?.allergies?.takeIf { it.isNotBlank() } ?: l10n(language, "None added", "कोई नहीं"))
                    if (!profile?.identityLast4.isNullOrBlank()) {
                        DetailRow(
                            if (profile?.identityMethod.equals("aadhaar", true)) "Aadhaar" else l10n(language, "Registered identity", "पंजीकृत पहचान"),
                            "${l10n(language, "Verified", "सत्यापित")} ···· ${profile?.identityLast4}",
                        )
                    }
                    if (profile?.abhaLinkStatus.equals("linked", true)) {
                        DetailRow("ABHA ID", "${l10n(language, "Added", "जोड़ा गया")} ···· ${profile?.abhaLast4.orEmpty()}")
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        Text(label, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(.9f))
        Text(value, color = DeepTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MedicalConditionsCard(selected: Set<String>, language: AppLanguage, onClick: () -> Unit) {
    val activeConditions = selected.filterNot { it.equals("None", true) }
    val summary = when {
        activeConditions.isEmpty() -> l10n(language, "No conditions selected", "कोई स्थिति नहीं चुनी")
        activeConditions.size <= 2 -> activeConditions.joinToString(" · ")
        else -> activeConditions.take(2).joinToString(" · ") + "  +${activeConditions.size - 2}"
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = SoftWhite),
        border = BorderStroke(1.dp, Border),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Mint), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MedicalInformation, null, tint = Forest, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(l10n(language, "Medical conditions", "स्वास्थ्य स्थितियां"), color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(summary, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, l10n(language, "Edit medical conditions", "स्वास्थ्य स्थितियां बदलें"), tint = Forest, modifier = Modifier.size(20.dp))
        }
    }
}

internal fun friendlyCondition(value: String): String = value
    .replace('_', ' ')
    .lowercase()
    .replaceFirstChar { it.titlecase() }
