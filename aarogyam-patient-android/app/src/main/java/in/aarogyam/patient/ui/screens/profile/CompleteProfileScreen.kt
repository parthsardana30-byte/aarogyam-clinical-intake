@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.aarogyam.patient.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import `in`.aarogyam.patient.data.PatientProfile
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.ui.AppLanguage
import `in`.aarogyam.patient.ui.components.AarogyamBackdrop
import `in`.aarogyam.patient.ui.components.PrimaryFlowButton
import `in`.aarogyam.patient.ui.components.l10n
import `in`.aarogyam.patient.ui.theme.Border
import `in`.aarogyam.patient.ui.theme.Canvas
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.Mint
import `in`.aarogyam.patient.ui.theme.Muted
import `in`.aarogyam.patient.ui.theme.SoftWhite

@Composable
internal fun CompleteProfileScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val profile = viewModel.state.dashboard?.patient
    val language = viewModel.state.companion.language
    var abhaId by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftWhite),
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, l10n(language, "Back", "वापस")) }
                },
                title = { Text(l10n(language, "Complete your profile", "अपनी प्रोफाइल पूरी करें"), color = DeepTeal, fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        AarogyamBackdrop(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(l10n(language, "Details you provided", "आपके द्वारा दिया गया विवरण"), color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text(l10n(language, "Review the information entered during registration.", "पंजीकरण के समय दर्ज जानकारी की समीक्षा करें।"), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                }
                item { RegistrationDetailsCard(profile, viewModel, language) }
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .96f)),
                        border = BorderStroke(1.dp, Border),
                    ) {
                        Column(Modifier.padding(17.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Mint), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Badge, null, tint = Forest, modifier = Modifier.size(21.dp))
                                }
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(l10n(language, "Add your ABHA ID", "अपनी ABHA ID जोड़ें"), color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(l10n(language, "Optional — you can add it later", "वैकल्पिक — आप इसे बाद में जोड़ सकते हैं"), color = Muted, fontSize = 12.sp)
                                }
                            }
                            OutlinedTextField(
                                value = abhaId,
                                onValueChange = { abhaId = it.filter(Char::isDigit).take(14) },
                                label = { Text("ABHA ID") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text(l10n(language, "Your verified Aadhaar details will not be changed.", "आपका सत्यापित आधार विवरण नहीं बदलेगा।")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                shape = RoundedCornerShape(15.dp),
                            )
                        }
                    }
                }
                item {
                    PrimaryFlowButton(l10n(language, "Save ABHA ID", "ABHA ID सहेजें"), abhaId.length == 14) {
                        viewModel.completeProfileIdentity(abhaId, onClose)
                    }
                }
                item { TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(l10n(language, "Not now", "अभी नहीं"), color = Forest) } }
            }
        }
    }
}

@Composable
private fun RegistrationDetailsCard(profile: PatientProfile?, viewModel: MainViewModel, language: AppLanguage) {
    val patientName = profile?.fullName ?: viewModel.state.session?.patient?.fullName ?: l10n(language, "Patient", "मरीज")
    val conditions = profile?.conditions.orEmpty().map(::friendlyCondition).joinToString().ifBlank { l10n(language, "None added", "कोई नहीं") }
    val notAvailable = l10n(language, "Not available", "उपलब्ध नहीं")
    val notAdded = l10n(language, "Not added", "जोड़ा नहीं गया")
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SoftWhite),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 8.dp)) {
            ProfileDetail(l10n(language, "Full name", "पूरा नाम"), patientName)
            ProfileDetail(l10n(language, "Mobile number", "मोबाइल नंबर"), profile?.phone ?: notAvailable)
            ProfileDetail(l10n(language, "Date of birth", "जन्म तिथि"), profile?.dateOfBirth ?: notAdded)
            ProfileDetail(l10n(language, "Gender", "लिंग"), profile?.gender ?: notAdded)
            ProfileDetail(l10n(language, "Height", "लंबाई"), profile?.heightCm?.takeIf { it > 0 }?.let { "${it.toInt()} cm" } ?: notAdded)
            ProfileDetail(l10n(language, "Weight", "वजन"), profile?.weightKg?.takeIf { it > 0 }?.let { "${it.toInt()} kg" } ?: notAdded)
            ProfileDetail(l10n(language, "Blood group", "ब्लड ग्रुप"), profile?.bloodGroup ?: notAdded)
            ProfileDetail(l10n(language, "Medical conditions", "स्वास्थ्य स्थितियां"), conditions)
            ProfileDetail(l10n(language, "Allergies", "एलर्जी"), profile?.allergies?.takeIf { it.isNotBlank() } ?: l10n(language, "None added", "कोई नहीं"))
            if (profile?.identityMethod.equals("aadhaar", true) && !profile?.identityLast4.isNullOrBlank()) {
                ProfileDetail("Aadhaar", "${l10n(language, "Verified", "सत्यापित")} ···· ${profile?.identityLast4}", highlight = true)
            }
        }
    }
}

@Composable
private fun ProfileDetail(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(.9f))
        Text(
            value,
            color = if (highlight) Forest else DeepTeal,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
