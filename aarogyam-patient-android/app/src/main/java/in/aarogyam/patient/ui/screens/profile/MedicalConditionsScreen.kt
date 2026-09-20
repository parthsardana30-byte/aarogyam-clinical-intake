@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.aarogyam.patient.ui.screens.profile

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val commonMedicalConditions = listOf(
    "Diabetes",
    "Hypertension",
    "Asthma",
    "Thyroid disorder",
    "Heart disease",
    "High cholesterol",
    "Arthritis",
    "Kidney condition",
    "Chronic lung disease",
    "Epilepsy",
    "Cancer",
    "None of these",
)

@Composable
internal fun MedicalConditionsScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val companion = viewModel.state.companion
    val language = companion.language
    val profileConditions = viewModel.state.dashboard?.patient?.conditions.orEmpty().map(::friendlyCondition).toSet()
    var selected by remember { mutableStateOf(profileConditions.map { if (it == "None") "None of these" else it }.toSet()) }

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftWhite),
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, l10n(language, "Back", "वापस")) }
                },
                title = { Text(l10n(language, "Medical conditions", "स्वास्थ्य स्थितियां"), color = DeepTeal, fontWeight = FontWeight.Bold) },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Mint), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MedicalInformation, null, tint = Forest)
                        }
                        Column(Modifier.padding(start = 13.dp)) {
                            Text(l10n(language, "Tell us what applies to you", "जो आप पर लागू हो उसे चुनें"), color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text(l10n(language, "Select all that apply. You can change this later.", "लागू सभी विकल्प चुनें। आप इन्हें बाद में बदल सकते हैं।"), color = Muted, fontSize = 13.sp)
                        }
                    }
                }
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .96f)),
                        border = BorderStroke(1.dp, Border),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            commonMedicalConditions.forEach { condition ->
                                val checked = condition in selected
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selected = updateConditions(selected, condition)
                                    }.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { selected = updateConditions(selected, condition) },
                                        colors = CheckboxDefaults.colors(checkedColor = Forest, checkmarkColor = Color.White),
                                    )
                                    Text(
                                        conditionLabel(condition, language),
                                        color = DeepTeal,
                                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.padding(start = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    PrimaryFlowButton(l10n(language, "Save medical conditions", "स्वास्थ्य स्थितियां सहेजें"), true) {
                        viewModel.setMedicalConditions(selected.map { if (it == "None of these") "None" else it }.toSet(), onClose)
                    }
                }
            }
        }
    }
}

private fun conditionLabel(condition: String, language: AppLanguage): String = when (condition) {
    "Diabetes" -> l10n(language, condition, "मधुमेह")
    "Hypertension" -> l10n(language, condition, "उच्च रक्तचाप")
    "Asthma" -> l10n(language, condition, "अस्थमा")
    "Thyroid disorder" -> l10n(language, condition, "थायरॉइड विकार")
    "Heart disease" -> l10n(language, condition, "हृदय रोग")
    "High cholesterol" -> l10n(language, condition, "उच्च कोलेस्ट्रॉल")
    "Arthritis" -> l10n(language, condition, "गठिया")
    "Kidney condition" -> l10n(language, condition, "किडनी की समस्या")
    "Chronic lung disease" -> l10n(language, condition, "दीर्घकालिक फेफड़ों की बीमारी")
    "Epilepsy" -> l10n(language, condition, "मिर्गी")
    "Cancer" -> l10n(language, condition, "कैंसर")
    else -> l10n(language, condition, "इनमें से कोई नहीं")
}

private fun updateConditions(selected: Set<String>, condition: String): Set<String> = when {
    condition == "None of these" && condition !in selected -> setOf("None of these")
    condition == "None of these" -> emptySet()
    condition in selected -> selected - condition
    else -> (selected - "None of these") + condition
}
