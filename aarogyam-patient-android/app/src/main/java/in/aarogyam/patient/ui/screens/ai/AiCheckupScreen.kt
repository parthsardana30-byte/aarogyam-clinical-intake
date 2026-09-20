@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.aarogyam.patient.ui.screens.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.data.Dashboard
import `in`.aarogyam.patient.ui.theme.Amber
import `in`.aarogyam.patient.ui.theme.Canvas
import `in`.aarogyam.patient.ui.theme.ClinicalBlue
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.ForestDark
import `in`.aarogyam.patient.ui.theme.Leaf
import `in`.aarogyam.patient.ui.theme.Mint
import `in`.aarogyam.patient.ui.theme.Muted

import `in`.aarogyam.patient.ui.*
import `in`.aarogyam.patient.ui.components.*

@Composable
internal fun AiCheckupScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var symptom by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("Mild") }
    var fever by remember { mutableStateOf("No") }
    var appetite by remember { mutableStateOf("Normal") }
    val summary = "Main concern: ${symptom.ifBlank { "Not specified" }}. Duration: ${duration.ifBlank { "Not specified" }}. Severity: $severity. Fever: $fever. Appetite: $appetite."
    FlowScaffold("Guided health check-in", step + 1, 3, onClose) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            when (step) {
                0 -> {
                    IconBadge(Icons.Default.EditNote, Mint, Forest)
                    Text("Tell me what feels different", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
                    Text("Use your own words. This stays in the app and creates a note for your doctor.", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(symptom, { symptom = it }, label = { Text("What are you feeling?") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(duration, { duration = it }, label = { Text("How long has this been happening?") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    Text("How strong is it?", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
                    ChoiceRow(listOf("Mild", "Moderate", "Severe"), severity) { severity = it }
                    Spacer(Modifier.weight(1f))
                    PrimaryFlowButton("Continue", symptom.isNotBlank()) { step = 1 }
                }
                1 -> {
                    Text("A few follow-up questions", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("These help make your health note more useful.", color = Muted, modifier = Modifier.padding(bottom = 22.dp))
                    QuestionChoice("Do you have a fever?", listOf("No", "Yes", "Not sure"), fever) { fever = it }
                    QuestionChoice("How is your appetite?", listOf("Normal", "Less", "More"), appetite) { appetite = it }
                    Card(colors = CardDefaults.cardColors(containerColor = SoftAmber), shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(top = 20.dp)) {
                        Text("If symptoms are severe, sudden, or you feel unsafe, contact emergency services or a clinician now.", modifier = Modifier.padding(15.dp), color = Color(0xFF714214))
                    }
                    Spacer(Modifier.weight(1f))
                    PrimaryFlowButton("Create my health note", true) { step = 2 }
                }
                else -> {
                    IconBadge(Icons.Default.CheckCircle, Mint, Forest)
                    Text("Your note is ready", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
                    Text("Review it before saving to your private record.", color = Muted)
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.padding(top = 18.dp)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Patient-reported health summary", fontWeight = FontWeight.Bold, color = Forest)
                            Text(summary, modifier = Modifier.padding(top = 10.dp), lineHeight = 22.sp)
                            HorizontalDivider(Modifier.padding(vertical = 14.dp))
                            Text("Suggested next step", fontWeight = FontWeight.Bold)
                            Text("Keep this note ready for your clinician. Track any changes and seek urgent help if symptoms worsen.", color = Muted)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("This is documentation support, not a medical diagnosis.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                    PrimaryFlowButton("Save to my records", true) { viewModel.saveAiNote(symptom, summary); onClose() }
                }
            }
        }
    }
}
