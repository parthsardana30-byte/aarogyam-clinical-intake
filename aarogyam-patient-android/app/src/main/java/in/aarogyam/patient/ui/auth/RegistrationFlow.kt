@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package `in`.aarogyam.patient.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.ui.RegistrationDraft
import `in`.aarogyam.patient.ui.theme.Border
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.Mint
import `in`.aarogyam.patient.ui.theme.Muted
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val steps = listOf("Phone", "Details", "Aadhaar", "Health", "E-PIN")
private val conditionChoices = listOf(
    "Diabetes", "Hypertension", "Asthma", "Thyroid disorder", "Heart disease",
    "High cholesterol", "Arthritis", "Kidney condition", "Chronic lung disease",
    "Epilepsy", "Cancer", "None of these",
)
private val bloodGroups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown")

@Composable
internal fun RegistrationFlow(viewModel: MainViewModel) {
    var step by remember { mutableIntStateOf(0) }
    var phone by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var verificationToken by remember { mutableStateOf("") }
    var demoOtp by remember { mutableStateOf<String?>(null) }
    var returnAfterReverification by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var gender by remember { mutableStateOf("Female") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var aadhaar by remember { mutableStateOf("") }
    var aadhaarChecked by remember { mutableStateOf(false) }
    var conditions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bloodGroup by remember { mutableStateOf("Unknown") }
    var epin by remember { mutableStateOf("") }
    var confirmEpin by remember { mutableStateOf("") }
    val otpLength = demoOtp?.length?.takeIf { it in 4..6 } ?: 4

    if (showDatePicker) {
        val today = LocalDate.now()
        val firstDay = today.minusYears(120).toEpochDay() * 86_400_000
        val lastDay = today.toEpochDay() * 86_400_000
        val pickerState = rememberDatePickerState(
            initialDisplayedMonthMillis = LocalDate.of(2000, 1, 1).toEpochDay() * 86_400_000,
            yearRange = (today.year - 120)..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in firstDay..lastDay
                override fun isSelectableYear(year: Int) = year in (today.year - 120)..today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dob = LocalDate.ofEpochDay(it / 86_400_000).toString() }
                    showDatePicker = false
                }, enabled = pickerState.selectedDateMillis != null) { Text("Use date") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }

    Text("Create your account", color = DeepTeal, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    Text("One clear step at a time.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp, bottom = 16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        steps.forEachIndexed { index, _ ->
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(6.dp))
                .background(if (index <= step) Forest else Border))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 17.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(steps[step], color = Forest, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("${step + 1} of ${steps.size}", color = Muted, fontSize = 12.sp)
    }
    if (step > 0) {
        Row(Modifier.fillMaxWidth().padding(bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = Forest, modifier = Modifier.size(16.dp))
            Text("+91 $phone verified", color = Forest, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }

    when (step) {
        0 -> {
            StepHeading("Verify your phone", "We’ll text a code to your mobile number.")
            RegistrationField(phone, { phone = it.onlyDigits(10) }, "Mobile number", KeyboardType.Phone)
            if (requestId.isNotBlank()) {
                RegistrationField(otp, { otp = it.onlyDigits(otpLength) }, "$otpLength-digit code", KeyboardType.Number)
                demoOtp?.let { Text("Local test code: $it", color = Forest, fontSize = 12.sp, modifier = Modifier.padding(bottom = 9.dp)) }
                TextButton(onClick = {
                    otp = ""; viewModel.requestOtp(phone) { requestId = it.id; demoOtp = it.demoOtp }
                }) { Text("Send a new code") }
            }
            ContinueButton(if (requestId.isBlank()) "Send code" else "Verify phone",
                phone.isValidIndianPhone() && (requestId.isBlank() || otp.length == otpLength)) {
                if (requestId.isBlank()) viewModel.requestOtp(phone) { requestId = it.id; demoOtp = it.demoOtp }
                else viewModel.verifyOtp(phone, requestId, otp) { verifiedId, token ->
                    requestId = verifiedId; verificationToken = token; step = returnAfterReverification
                }
            }
        }
        1 -> {
            StepHeading("About you", "Use the same details you give your hospital.")
            RegistrationField(name, {
                name = it.filter { char -> char.isLetter() || char.isWhitespace() || char in "-' ." }.take(80)
            }, "Full name")
            Text("Date of birth", color = DeepTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 13.dp)) {
                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                Text(
                    if (dob.isBlank()) "Select your date of birth" else LocalDate.parse(dob).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)),
                    modifier = Modifier.weight(1f).padding(start = 9.dp),
                )
            }
            Text("Gender", color = DeepTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 5.dp, bottom = 9.dp)) {
                listOf("Female", "Male", "Prefer not to say").forEach { option ->
                    FilterChip(selected = gender == option, onClick = { gender = option }, label = { Text(option, fontSize = 12.sp) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RegistrationField(height, { height = it.onlyDigits(3) }, "Height · cm", KeyboardType.Number, Modifier.weight(1f))
                RegistrationField(weight, { weight = it.onlyDigits(3) }, "Weight · kg", KeyboardType.Number, Modifier.weight(1f))
            }
            Text("Height 50–250 cm · Weight 2–350 kg", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))
            ContinueButton("Continue", name.trim().length >= 2 && dob.isNotBlank() &&
                (height.toIntOrNull() ?: 0) in 50..250 && (weight.toIntOrNull() ?: 0) in 2..350) { step = 2 }
        }
        2 -> {
            StepHeading("Confirm Aadhaar", "Aadhaar is required. Verification is a demo for now.")
            RegistrationField(aadhaar.chunked(4).joinToString(" "), {
                val digits = it.onlyDigits(12)
                if (digits != aadhaar) aadhaarChecked = false
                aadhaar = digits
            }, "12-digit Aadhaar number", KeyboardType.Number)
            Card(colors = CardDefaults.cardColors(containerColor = Mint), border = BorderStroke(1.dp, Border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 13.dp)) {
                Text("This demo check confirms the format only. It does not contact Aadhaar.",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
            OutlinedButton(onClick = { aadhaarChecked = true }, enabled = aadhaar.length == 12 && !aadhaarChecked,
                modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp)) {
                Text(if (aadhaarChecked) "Aadhaar checked (demo)" else "Check Aadhaar (demo)")
            }
            ContinueButton("Continue", aadhaarChecked) { step = 3 }
        }
        3 -> {
            StepHeading("Medical history", "Select any conditions you’ve been diagnosed with.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 13.dp)) {
                conditionChoices.forEach { condition ->
                    FilterChip(selected = condition in conditions,
                        onClick = { conditions = updateConditions(conditions, condition) },
                        label = { Text(condition, fontSize = 12.sp) })
                }
            }
            Text("Blood group", color = DeepTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 5.dp, bottom = 10.dp)) {
                bloodGroups.forEach { group ->
                    FilterChip(selected = bloodGroup == group, onClick = { bloodGroup = group }, label = { Text(group) })
                }
            }
            ContinueButton("Continue", conditions.isNotEmpty()) { step = 4 }
        }
        else -> {
            StepHeading("Set your E-PIN", "You’ll use these six digits to sign in.")
            RegistrationField(epin, { epin = it.onlyDigits(6) }, "6-digit E-PIN", KeyboardType.NumberPassword, concealed = true)
            RegistrationField(confirmEpin, { confirmEpin = it.onlyDigits(6) }, "Confirm E-PIN", KeyboardType.NumberPassword, concealed = true)
            if (confirmEpin.isNotBlank() && confirmEpin != epin) {
                Text("E-PINs do not match", color = Color(0xFFB42318), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            ContinueButton("Create account", epin.length == 6 && confirmEpin == epin) {
                viewModel.register(phone, requestId, verificationToken, RegistrationDraft(
                    identityMethod = "aadhaar", identityNumber = aadhaar,
                    fullName = name.trim().replace(Regex("\\s+"), " "), dateOfBirth = dob, gender = gender,
                    heightCm = height.toDouble(), weightKg = weight.toDouble(), bloodGroup = bloodGroup,
                    conditions = conditions.map { if (it == "None of these") "none" else it.lowercase().replace(' ', '_') },
                    allergies = "", epin = epin,
                ))
            }
            TextButton(onClick = {
                returnAfterReverification = 4; requestId = ""; verificationToken = ""; otp = ""; demoOtp = null; step = 0
            }, modifier = Modifier.fillMaxWidth()) { Text("Need a new phone code?") }
        }
    }
    StatusMessage(viewModel.state.message)
    if (step > 0) TextButton(onClick = { step-- }, modifier = Modifier.fillMaxWidth()) { Text("Back", color = Forest) }
}

@Composable
private fun StepHeading(title: String, description: String) {
    Text(title, color = DeepTeal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Text(description, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 15.dp))
}

@Composable
private fun RegistrationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    concealed: Boolean = false,
) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (concealed) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(14.dp))
}

@Composable
private fun ContinueButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(15.dp)) { Text(label, fontWeight = FontWeight.Bold) }
}

private fun String.onlyDigits(limit: Int) = filter(Char::isDigit).take(limit)

private fun updateConditions(selected: Set<String>, condition: String): Set<String> = when {
    condition == "None of these" && condition !in selected -> setOf(condition)
    condition == "None of these" -> emptySet()
    condition in selected -> selected - condition
    else -> (selected - "None of these") + condition
}
