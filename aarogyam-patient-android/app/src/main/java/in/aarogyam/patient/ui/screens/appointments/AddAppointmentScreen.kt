@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.aarogyam.patient.ui.screens.appointments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.ui.components.AarogyamBackdrop
import `in`.aarogyam.patient.ui.components.PrimaryFlowButton
import `in`.aarogyam.patient.ui.components.l10n
import `in`.aarogyam.patient.ui.theme.Canvas
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Muted
import `in`.aarogyam.patient.ui.theme.SoftWhite
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun AddAppointmentScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val language = viewModel.state.companion.language
    val context = LocalContext.current
    var hospital by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    val futureAppointment = runCatching {
        hospital.isNotBlank() && LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).isAfter(LocalDateTime.now())
    }.getOrDefault(false)

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftWhite),
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, l10n(language, "Back", "वापस")) } },
                title = { Text(l10n(language, "Add appointment", "अपॉइंटमेंट जोड़ें"), color = DeepTeal, fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        AarogyamBackdrop(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(l10n(language, "Set a reminder", "रिमाइंडर सेट करें"), color = DeepTeal, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(
                        l10n(language, "Just add the hospital, date and time.", "केवल अस्पताल, तारीख और समय जोड़ें।"),
                        color = Muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        hospital,
                        { hospital = it.replace('\n', ' ').take(80) },
                        label = { Text(l10n(language, "Hospital name", "अस्पताल का नाम")) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(l10n(language, "Appointment date", "अपॉइंटमेंट की तारीख"), color = DeepTeal, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = {
                        val today = LocalDate.now()
                        DatePickerDialog(context, { _, year, month, day ->
                            date = LocalDate.of(year, month + 1, day).toString()
                        }, today.year, today.monthValue - 1, today.dayOfMonth).apply {
                            datePicker.minDate = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }.show()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (date.isBlank()) l10n(language, "Choose appointment date", "अपॉइंटमेंट की तारीख चुनें")
                            else LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)))
                    }
                }
                item {
                    Text(l10n(language, "Appointment time", "अपॉइंटमेंट का समय"), color = DeepTeal, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = {
                        val now = LocalTime.now()
                        TimePickerDialog(context, { _, hour, minute ->
                            time = String.format(Locale.ENGLISH, "%02d:%02d", hour, minute)
                        }, now.hour, now.minute, false).show()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (time.isBlank()) l10n(language, "Choose appointment time", "अपॉइंटमेंट का समय चुनें")
                            else LocalTime.parse(time).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)))
                    }
                }
                item {
                    PrimaryFlowButton(
                        l10n(language, "Save appointment", "अपॉइंटमेंट सहेजें"),
                        futureAppointment,
                    ) {
                        viewModel.addAppointment(hospital, date, time)
                        onClose()
                    }
                }
            }
        }
    }
}
