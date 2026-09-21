package `in`.aarogyam.patient.ui.screens.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.data.Dashboard
import `in`.aarogyam.patient.ui.AppointmentItem
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.ui.AppLanguage
import `in`.aarogyam.patient.ui.components.AarogyamBackdrop
import `in`.aarogyam.patient.ui.components.DateTile
import `in`.aarogyam.patient.ui.components.DocumentUploadBanner
import `in`.aarogyam.patient.ui.components.EmptyCard
import `in`.aarogyam.patient.ui.components.SectionTitle
import `in`.aarogyam.patient.ui.components.tr
import `in`.aarogyam.patient.ui.components.l10n
import `in`.aarogyam.patient.ui.theme.Border
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.Leaf
import `in`.aarogyam.patient.ui.theme.Mint
import `in`.aarogyam.patient.ui.theme.Muted
import java.time.LocalDate

@Composable
internal fun TodayScreen(
    viewModel: MainViewModel,
    dashboard: Dashboard?,
    onUploadRecord: () -> Unit,
    onOpenAppointmentReminders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val companion = viewModel.state.companion
    val language = companion.language
    val firstName = (dashboard?.patient?.fullName ?: viewModel.state.session?.patient?.fullName ?: "there").substringBefore(" ")
    val reminders = companion.appointments.filter { it.reminderEnabled && it.isActiveReminder() }
    val latestReminder = reminders.firstOrNull()

    AarogyamBackdrop(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                Text("${tr(language, "hello")}, $firstName", style = MaterialTheme.typography.headlineLarge, color = DeepTeal)
                Text(
                    l10n(language, "Your care for today", "आज आपकी देखभाल"),
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            item { UploadRecordCard(language, onUploadRecord) }
            if (viewModel.documentUpload.uploading || viewModel.documentUpload.error != null || viewModel.documentUpload.completed) {
                item { DocumentUploadBanner(viewModel.documentUpload, onUploadRecord) }
            }
            item { LatestReminderCard(latestReminder, language, onOpenAppointmentReminders) }
            item { SectionTitle(l10n(language, "Your appointment reminders", "आपके अपॉइंटमेंट रिमाइंडर"), l10n(language, "Manage", "देखें"), onOpenAppointmentReminders) }
            if (reminders.isEmpty()) {
                item { EmptyCard(l10n(language, "No upcoming reminders", "कोई आगामी रिमाइंडर नहीं"), l10n(language, "Add an appointment to see it here.", "यहां देखने के लिए अपॉइंटमेंट जोड़ें।")) }
            } else {
                items(reminders, key = { it.id }) { appointment ->
                    ReminderRow(appointment, onOpenAppointmentReminders)
                }
            }
        }
    }
}

private fun AppointmentItem.isActiveReminder(): Boolean {
    val scheduled = dateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return true
    return !scheduled.isBefore(LocalDate.now())
}

@Composable
private fun UploadRecordCard(language: AppLanguage, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(DeepTeal, Forest))).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Leaf),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.UploadFile, null, tint = DeepTeal, modifier = Modifier.size(23.dp)) }
            Text(
                l10n(language, "Upload a medical record", "मेडिकल रिकॉर्ड अपलोड करें"),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(horizontal = 13.dp),
            )
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Leaf)
        }
    }
}

@Composable
private fun LatestReminderCard(item: AppointmentItem?, language: AppLanguage, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .96f)),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Mint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CalendarMonth, null, tint = Forest, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(l10n(language, "LATEST REMINDER", "नवीनतम रिमाइंडर"), color = Forest, fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text(l10n(language, "Appointment reminder", "अपॉइंटमेंट रिमाइंडर"), color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 25.sp)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Forest)
            }
            if (item == null) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = Forest)
                    Text(l10n(language, "Set an appointment reminder", "अपॉइंटमेंट रिमाइंडर सेट करें"), color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 15.dp).clip(RoundedCornerShape(17.dp)).background(Mint).padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DateTile(item.day, item.date)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        val simpleHospitalReminder = item.doctor == "Hospital appointment"
                        Text(if (simpleHospitalReminder) item.location else item.doctor, color = DeepTeal, fontWeight = FontWeight.Bold)
                        Text(if (simpleHospitalReminder) item.time else "${item.specialty} · ${item.time}", color = Muted, fontSize = 12.sp)
                        if (!simpleHospitalReminder) Text(item.location, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(Icons.Default.NotificationsActive, null, tint = Forest, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(item: AppointmentItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .94f)),
        border = BorderStroke(1.dp, Border),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            DateTile(item.day, item.date)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                val simpleHospitalReminder = item.doctor == "Hospital appointment"
                Text(if (simpleHospitalReminder) item.location else item.doctor, color = DeepTeal, fontWeight = FontWeight.Bold)
                Text(if (simpleHospitalReminder) item.time else "${item.time} · ${item.location}", color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.NotificationsActive, null, tint = Forest, modifier = Modifier.size(17.dp))
            }
        }
    }
}
