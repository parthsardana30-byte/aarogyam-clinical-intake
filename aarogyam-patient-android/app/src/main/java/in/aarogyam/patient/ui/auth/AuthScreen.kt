@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.aarogyam.patient.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.ui.RegistrationDraft
import `in`.aarogyam.patient.ui.components.AarogyamBackdrop
import `in`.aarogyam.patient.ui.components.ArogyamBrand
import `in`.aarogyam.patient.ui.theme.*

private enum class AuthPage { Login, Register, LoginWithOtp, ForgotEpin }

private val registrationMedicalConditions = listOf(
    "Diabetes", "Hypertension", "Asthma", "Thyroid disorder", "Heart disease",
    "High cholesterol", "Arthritis", "Kidney condition", "Chronic lung disease",
    "Epilepsy", "Cancer", "None of these",
)

@Composable
internal fun AuthScreen(viewModel: MainViewModel) {
    var page by remember { mutableStateOf(AuthPage.Login) }
    fun open(target: AuthPage) { viewModel.clearMessage(); page = target }

    AarogyamBackdrop(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(30.dp))
                ArogyamBrand(Modifier.fillMaxWidth())
                Spacer(Modifier.height(28.dp))
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(23.dp)).background(Mint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Forest, modifier = Modifier.size(37.dp))
                    Box(Modifier.align(Alignment.BottomEnd).size(27.dp).clip(CircleShape).background(Forest), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.HealthAndSafety, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Text("Your health, always with you.", style = MaterialTheme.typography.headlineLarge, color = DeepTeal, modifier = Modifier.padding(top = 17.dp))
                Text("Sign in securely to access appointments, visits and medical records.", color = Muted, lineHeight = 21.sp, modifier = Modifier.padding(top = 7.dp, bottom = 22.dp))
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .96f)),
                    border = BorderStroke(1.dp, Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        if (page == AuthPage.Login || page == AuthPage.Register) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AuthTab("Sign in", page == AuthPage.Login, Modifier.weight(1f)) { open(AuthPage.Login) }
                                AuthTab("Create account", page == AuthPage.Register, Modifier.weight(1f)) { open(AuthPage.Register) }
                            }
                            Spacer(Modifier.height(22.dp))
                        }
                        when (page) {
                            AuthPage.Login -> LoginForm(
                                viewModel,
                                onLoginWithOtp = { open(AuthPage.LoginWithOtp) },
                                onForgotEpin = { open(AuthPage.ForgotEpin) },
                            )
                            AuthPage.Register -> RegistrationForm(viewModel)
                            AuthPage.LoginWithOtp -> LoginWithOtpForm(viewModel) { open(AuthPage.Login) }
                            AuthPage.ForgotEpin -> ForgotEpinForm(viewModel) { open(AuthPage.Login) }
                        }
                    }
                }
                Row(Modifier.padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Forest, modifier = Modifier.size(15.dp))
                    Text("Your health information stays private", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 7.dp))
                }
            }
            if (viewModel.state.loading) LoadingOverlay()
        }
    }
}

@Composable
private fun LoginForm(viewModel: MainViewModel, onLoginWithOtp: () -> Unit, onForgotEpin: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var epin by remember { mutableStateOf("") }
    Text("Welcome back", style = MaterialTheme.typography.headlineSmall, color = DeepTeal)
    Text("Use your registered mobile number and six-digit E-PIN.", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
    FormField(phone, { phone = it.digits(10) }, "10-digit mobile number", keyboardType = KeyboardType.Phone)
    FormField(epin, { epin = it.digits(6) }, "6-digit E-PIN", password = true, keyboardType = KeyboardType.NumberPassword)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onLoginWithOtp) { Text("Login with OTP", color = Forest, fontWeight = FontWeight.Bold) }
        TextButton(onClick = onForgotEpin) { Text("Forgot E-PIN?", color = Forest, fontWeight = FontWeight.Bold) }
    }
    StatusMessage(viewModel.state.message)
    Button(
        onClick = { viewModel.login(phone, epin) },
        enabled = phone.length == 10 && epin.length == 6,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text("Open my dashboard", fontWeight = FontWeight.Bold) }
}

@Composable
private fun LoginWithOtpForm(viewModel: MainViewModel, onBack: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var demoOtp by remember { mutableStateOf<String?>(null) }

    Text("Login with OTP", style = MaterialTheme.typography.headlineSmall, color = DeepTeal)
    Text(
        if (requestId.isBlank()) "Enter your registered mobile number." else "Enter the OTP sent to +91 $phone.",
        color = Muted,
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
    )
    FormField(phone, { if (requestId.isBlank()) phone = it.digits(10) }, "Registered mobile number", keyboardType = KeyboardType.Phone)
    if (requestId.isNotBlank()) {
        FormField(otp, { otp = it.digits(6) }, "6-digit OTP", keyboardType = KeyboardType.Number)
        demoOtp?.let { Text("Local demo OTP: $it", color = Forest, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)) }
    }
    FlowButton(
        if (requestId.isBlank()) "Send OTP" else "Verify OTP and sign in",
        phone.length == 10 && (requestId.isBlank() || otp.length == 6),
    ) {
        if (requestId.isBlank()) {
            viewModel.requestOtp(phone, "login") { requestId = it.id; demoOtp = it.demoOtp }
        } else {
            viewModel.loginWithOtp(phone, requestId, otp)
        }
    }
    StatusMessage(viewModel.state.message)
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to E-PIN login", color = Forest) }
}

@Composable
private fun ForgotEpinForm(viewModel: MainViewModel, onBack: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    var phone by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var verificationToken by remember { mutableStateOf("") }
    var demoOtp by remember { mutableStateOf<String?>(null) }
    var epin by remember { mutableStateOf("") }
    var confirmEpin by remember { mutableStateOf("") }

    Text("Reset your E-PIN", style = MaterialTheme.typography.headlineSmall, color = DeepTeal)
    StepLabel(step)
    when (step) {
        1 -> {
            Text("We will send an OTP to your registered mobile number.", color = Muted, modifier = Modifier.padding(bottom = 14.dp))
            FormField(phone, { phone = it.digits(10) }, "Registered mobile number", keyboardType = KeyboardType.Phone)
            FlowButton("Send OTP", phone.length == 10) {
                viewModel.requestOtp(phone, "reset") { requestId = it.id; demoOtp = it.demoOtp; step = 2 }
            }
        }
        2 -> {
            Text("Enter the OTP sent to +91 $phone.", color = Muted, modifier = Modifier.padding(bottom = 14.dp))
            FormField(otp, { otp = it.digits(6) }, "6-digit OTP", keyboardType = KeyboardType.Number)
            demoOtp?.let { Text("Local demo OTP: $it", color = Forest, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)) }
            FlowButton("Verify OTP", otp.length == 6) {
                viewModel.verifyOtp(phone, requestId, otp, "reset") { verifiedRequestId, token ->
                    requestId = verifiedRequestId; verificationToken = token; step = 3
                }
            }
        }
        else -> {
            Text("Choose a new six-digit E-PIN.", color = Muted, modifier = Modifier.padding(bottom = 14.dp))
            FormField(epin, { epin = it.digits(6) }, "New E-PIN", password = true, keyboardType = KeyboardType.NumberPassword)
            FormField(confirmEpin, { confirmEpin = it.digits(6) }, "Confirm E-PIN", password = true, keyboardType = KeyboardType.NumberPassword)
            PinMismatch(epin, confirmEpin)
            FlowButton("Save E-PIN and sign in", epin.length == 6 && epin == confirmEpin) {
                viewModel.resetEpin(phone, requestId, verificationToken, epin)
            }
        }
    }
    StatusMessage(viewModel.state.message)
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in", color = Forest) }
}

@Composable
private fun RegistrationForm(viewModel: MainViewModel) {
    var step by remember { mutableIntStateOf(1) }
    var phone by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var verificationToken by remember { mutableStateOf("") }
    var demoOtp by remember { mutableStateOf<String?>(null) }
    var aadhaar by remember { mutableStateOf("") }
    var aadhaarVerified by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Female") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("Unknown") }
    var conditions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allergies by remember { mutableStateOf("") }
    var epin by remember { mutableStateOf("") }
    var confirmEpin by remember { mutableStateOf("") }

    Text("Create your patient account", style = MaterialTheme.typography.headlineSmall, color = DeepTeal)
    StepLabel(step)
    when (step) {
        1 -> {
            SectionPrompt("Verify your mobile number")
            FormField(phone, { phone = it.digits(10) }, "10-digit mobile number", keyboardType = KeyboardType.Phone)
            if (requestId.isNotBlank()) {
                FormField(otp, { otp = it.digits(6) }, "6-digit OTP", keyboardType = KeyboardType.Number)
                demoOtp?.let { Text("Local demo OTP: $it", color = Forest, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp)) }
            }
            FlowButton(if (requestId.isBlank()) "Send OTP" else "Verify number", phone.length == 10 && (requestId.isBlank() || otp.length == 6)) {
                if (requestId.isBlank()) viewModel.requestOtp(phone) { requestId = it.id; demoOtp = it.demoOtp }
                else viewModel.verifyOtp(phone, requestId, otp) { verifiedRequestId, token ->
                    requestId = verifiedRequestId; verificationToken = token; step = 2
                }
            }
        }
        2 -> {
            SectionPrompt("Basic information")
            FormField(name, { name = it }, "Full name")
            FormField(dob, { dob = it }, "Date of birth (YYYY-MM-DD)")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                listOf("Female", "Male", "Prefer not to say").forEach { option ->
                    FilterChip(selected = gender == option, onClick = { gender = option }, label = { Text(option, fontSize = 11.sp) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(height, { height = it.digits(3) }, "Height cm", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                FormField(weight, { weight = it.digits(3) }, "Weight kg", Modifier.weight(1f), keyboardType = KeyboardType.Number)
            }
            Text("Aadhaar verification is required", color = DeepTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 7.dp))
            FormField(aadhaar, { aadhaar = it.digits(12); aadhaarVerified = false }, "12-digit Aadhaar number", keyboardType = KeyboardType.Number)
            FlowButton(if (aadhaarVerified) "Aadhaar verified (demo)" else "Verify Aadhaar (demo)", aadhaar.length == 12 && !aadhaarVerified) { aadhaarVerified = true }
            FlowButton(
                "Continue",
                name.trim().length > 1 && dob.isNotBlank() && (height.toIntOrNull() ?: 0) in 50..250 && (weight.toIntOrNull() ?: 0) in 2..350 && aadhaarVerified,
            ) { step = 3 }
        }
        else -> {
            SectionPrompt("Previous medical conditions")
            Text("Select everything that applies. This stays in your patient profile and can be edited later.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 9.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Border),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    registrationMedicalConditions.forEach { condition ->
                        Row(
                            Modifier.fillMaxWidth().clickable { conditions = updateRegistrationConditions(conditions, condition) }.padding(horizontal = 8.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = condition in conditions,
                                onCheckedChange = { conditions = updateRegistrationConditions(conditions, condition) },
                                colors = CheckboxDefaults.colors(checkedColor = Forest),
                            )
                            Text(condition, color = DeepTeal, fontSize = 14.sp)
                        }
                    }
                }
            }
            SectionPrompt("Set your E-PIN")
            FormField(bloodGroup, { bloodGroup = it }, "Blood group (or Unknown)")
            FormField(allergies, { allergies = it }, "Allergies (optional)")
            FormField(epin, { epin = it.digits(6) }, "Create 6-digit E-PIN", password = true, keyboardType = KeyboardType.NumberPassword)
            FormField(confirmEpin, { confirmEpin = it.digits(6) }, "Confirm E-PIN", password = true, keyboardType = KeyboardType.NumberPassword)
            PinMismatch(epin, confirmEpin)
            FlowButton("Create account and continue", conditions.isNotEmpty() && epin.length == 6 && epin == confirmEpin) {
                val knownConditions = conditions.map {
                    if (it == "None of these") "none" else it.lowercase().replace(' ', '_')
                }
                viewModel.register(
                    phone, requestId, verificationToken,
                    RegistrationDraft(
                        "aadhaar", aadhaar, name, dob, gender,
                        height.toDoubleOrNull() ?: 0.0, weight.toDoubleOrNull() ?: 0.0,
                        bloodGroup.ifBlank { "Unknown" }, knownConditions.ifEmpty { listOf("none") }, allergies, epin,
                    ),
                )
            }
        }
    }
    if (step > 1) TextButton({ step-- }) { Text("Back", color = Forest) }
    StatusMessage(viewModel.state.message)
}

@Composable private fun StepLabel(step: Int) = Text("STEP $step OF 3", color = Forest, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 7.dp, bottom = 14.dp))
@Composable private fun SectionPrompt(text: String) = Text(text, color = DeepTeal, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))

@Composable
private fun PinMismatch(epin: String, confirmation: String) {
    if (confirmation.isNotEmpty() && epin != confirmation) Text("E-PINs do not match", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
}

@Composable
private fun FlowButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AuthTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Bold) }, modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Mint, selectedLabelColor = Forest),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = Border, selectedBorderColor = Forest.copy(alpha = .28f)),
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth().padding(bottom = 11.dp), shape = RoundedCornerShape(15.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Forest, unfocusedBorderColor = Border,
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
        ),
    )
}

private fun String.digits(limit: Int) = filter(Char::isDigit).take(limit)

private fun updateRegistrationConditions(selected: Set<String>, condition: String): Set<String> = when {
    condition == "None of these" && condition !in selected -> setOf("None of these")
    condition == "None of these" -> emptySet()
    condition in selected -> selected - condition
    else -> (selected - "None of these") + condition
}

@Composable
internal fun StatusMessage(message: String?) {
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }
}

@Composable
internal fun LoadingOverlay() {
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .68f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Forest)
    }
}
