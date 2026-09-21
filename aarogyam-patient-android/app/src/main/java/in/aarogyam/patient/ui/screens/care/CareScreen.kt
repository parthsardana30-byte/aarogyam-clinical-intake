package `in`.aarogyam.patient.ui.screens.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.ui.AppLanguage
import `in`.aarogyam.patient.ui.AppointmentItem
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.ui.components.AarogyamBackdrop
import `in`.aarogyam.patient.ui.components.DateTile
import `in`.aarogyam.patient.ui.components.EmptyCard
import `in`.aarogyam.patient.ui.components.ScreenIntro
import `in`.aarogyam.patient.ui.components.l10n
import `in`.aarogyam.patient.ui.theme.Border
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.Muted
import java.time.LocalDate

@Composable
internal fun CareScreen(
    viewModel: MainViewModel,
    onAddAppointment: () -> Unit,
    onUploadRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = viewModel.state.companion.language
    var pendingDelete by remember { mutableStateOf<AppointmentItem?>(null) }
    val appointments = viewModel.state.companion.appointments.filter { appointment ->
        val date = appointment.dateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        date == null || !date.isBefore(LocalDate.now())
    }

    pendingDelete?.let { appointment ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(l10n(language, "Delete this reminder?", "यह रिमाइंडर हटाएं?")) },
            text = { Text(appointment.location) },
            confirmButton = { TextButton(onClick = {
                viewModel.deleteAppointment(appointment.id)
                pendingDelete = null
            }) { Text(l10n(language, "Delete", "हटाएं"), color = Color(0xFFB42318)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(l10n(language, "Cancel", "रद्द करें")) } },
        )
    }

    AarogyamBackdrop(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                ScreenIntro(
                    l10n(language, "Upcoming visits", "आगामी मुलाकातें"),
                    l10n(language, "Appointment reminders", "अपॉइंटमेंट रिमाइंडर"),
                    l10n(language, "Past appointments disappear automatically.", "पुरानी अपॉइंटमेंट अपने आप हट जाती हैं।"),
                )
            }
            item {
                Button(onClick = onAddAppointment, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Add, null)
                    Text(l10n(language, "Add appointment", "अपॉइंटमेंट जोड़ें"), modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                }
            }
            item {
                OutlinedButton(onClick = onUploadRecord, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.UploadFile, null)
                    Text(l10n(language, "Upload medical record", "मेडिकल रिकॉर्ड अपलोड करें"), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (appointments.isEmpty()) {
                item {
                    EmptyCard(
                        l10n(language, "No upcoming appointments", "कोई आगामी अपॉइंटमेंट नहीं"),
                        l10n(language, "Add a visit to create your first reminder.", "अपना पहला रिमाइंडर बनाने के लिए अपॉइंटमेंट जोड़ें।"),
                    )
                }
            } else {
                items(appointments, key = { it.id }) { appointment ->
                    AppointmentReminderCard(appointment, language,
                        onReminder = { viewModel.toggleAppointmentReminder(appointment.id) },
                        onDelete = { pendingDelete = appointment })
                }
            }
        }
    }
}

@Composable
private fun AppointmentReminderCard(item: AppointmentItem, language: AppLanguage, onReminder: () -> Unit, onDelete: () -> Unit) {
    val simpleHospitalReminder = item.doctor == "Hospital appointment"
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .96f)),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DateTile(item.day, item.date)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(if (simpleHospitalReminder) item.location else item.doctor, color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (!simpleHospitalReminder) Text(item.specialty, color = Forest, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (simpleHospitalReminder) item.time else "${item.time} · ${item.location}",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Border)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, tint = Forest, modifier = Modifier.size(19.dp))
                Text(
                    l10n(language, "Show on Home", "होम पर दिखाएं"),
                    color = DeepTeal,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    fontSize = 13.sp,
                )
                Switch(checked = item.reminderEnabled, onCheckedChange = { onReminder() })
            }
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFB42318), modifier = Modifier.size(18.dp))
                Text(l10n(language, "Delete reminder", "रिमाइंडर हटाएं"), color = Color(0xFFB42318), modifier = Modifier.padding(start = 5.dp))
            }
        }
    }
}
