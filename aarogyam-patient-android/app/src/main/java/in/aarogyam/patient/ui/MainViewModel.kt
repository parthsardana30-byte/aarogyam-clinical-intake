package `in`.aarogyam.patient.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.aarogyam.patient.BuildConfig
import `in`.aarogyam.patient.data.ApiClient
import `in`.aarogyam.patient.data.ApiException
import `in`.aarogyam.patient.data.AuthSession
import `in`.aarogyam.patient.data.Dashboard
import `in`.aarogyam.patient.data.OtpRequest
import `in`.aarogyam.patient.data.OtpService
import `in`.aarogyam.patient.data.Registration
import `in`.aarogyam.patient.data.SessionStore
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AppLanguage(val label: String, val nativeLabel: String, val code: String) {
    English("English", "English", "EN"),
    Hindi("Hindi", "हिन्दी", "HI"),
}

data class MedicationItem(
    val id: String,
    val name: String,
    val dose: String,
    val time: String,
    val instruction: String,
    val taken: Boolean = false,
)

data class AppointmentItem(
    val id: String,
    val doctor: String,
    val specialty: String,
    val day: String,
    val date: String,
    val time: String,
    val location: String,
    val dateIso: String? = null,
    val reminderEnabled: Boolean = true,
    val intakeComplete: Boolean = false,
)

data class AiHealthNote(
    val id: String,
    val title: String,
    val summary: String,
    val createdLabel: String,
)

data class CompanionState(
    val language: AppLanguage = AppLanguage.English,
    val medications: List<MedicationItem> = listOf(
        MedicationItem("morning", "Metformin", "500 mg", "8:00 AM", "After breakfast"),
        MedicationItem("evening", "Vitamin D3", "1 tablet", "8:30 PM", "After dinner"),
    ),
    val appointments: List<AppointmentItem> = listOf(
        AppointmentItem("cardio", "Dr. Meera Shah", "Cardiology", "TUE", "23 SEP", "10:30 AM", "Arogyam Clinic, Ahmedabad", dateIso = "2026-09-23"),
        AppointmentItem("general", "Dr. Arjun Rao", "General Medicine", "MON", "06 OCT", "4:00 PM", "Video consultation", dateIso = "2026-10-06", intakeComplete = true),
    ),
    val aiNotes: List<AiHealthNote> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val emergencyContact: String = "",
    val emergencyPhone: String = "",
)

data class MainUiState(
    val session: AuthSession? = null,
    val dashboard: Dashboard? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val dashboardError: String? = null,
    val companion: CompanionState = CompanionState(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application)
    private val companionPreferences = application.getSharedPreferences("patient_companion", 0)
    private var api = ApiClient(BuildConfig.API_BASE_URL)
    private val otpService = OtpService(api)
    var state by mutableStateOf(MainUiState(session = sessionStore.read()))
        private set

    init {
        val savedLanguage = companionPreferences.getString("language", null)
            ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
        state = state.copy(companion = state.companion.copy(
            language = savedLanguage ?: state.companion.language,
        ))
        if (state.session != null) refresh()
    }

    fun login(phone: String, epin: String) = runRequest {
        val session = api.loginWithPhone(phone, epin)
        sessionStore.save(session)
        state = state.copy(session = session)
        loadDashboard(session)
    }

    fun loginWithOtp(phone: String, requestId: String, otp: String) = runRequest {
        val verification = otpService.verify(phone, requestId, otp, "login")
        val session = api.loginWithVerifiedOtp(phone, verification.requestId, verification.token)
        sessionStore.save(session)
        state = state.copy(session = session)
        loadDashboard(session)
    }

    fun requestOtp(phone: String, purpose: String = "register", result: (OtpRequest) -> Unit) = runRequest {
        result(otpService.send(phone, purpose))
    }

    fun verifyOtp(phone: String, requestId: String, otp: String, purpose: String = "register", result: (String, String) -> Unit) = runRequest {
        val verification = otpService.verify(phone, requestId, otp, purpose)
        result(verification.requestId, verification.token)
    }

    fun register(phone: String, requestId: String, verificationToken: String, draft: RegistrationDraft) = runRequest {
        val session = api.register(draft.toRegistration(phone, requestId, verificationToken))
        sessionStore.save(session)
        state = state.copy(session = session)
        loadDashboard(session)
    }

    fun resetEpin(phone: String, requestId: String, verificationToken: String, epin: String) = runRequest {
        val session = api.resetEpin(phone, requestId, verificationToken, epin)
        sessionStore.save(session)
        state = state.copy(session = session)
        loadDashboard(session)
    }

    fun refresh() {
        val session = state.session ?: return
        runRequest { loadDashboard(session) }
    }

    fun setLanguage(language: AppLanguage) {
        companionPreferences.edit().putString("language", language.name).apply()
        state = state.copy(companion = state.companion.copy(language = language))
    }

    fun markMedication(id: String) {
        state = state.copy(companion = state.companion.copy(
            medications = state.companion.medications.map {
                if (it.id == id) it.copy(taken = !it.taken) else it
            },
        ))
    }

    fun addMedication(name: String, dose: String, time: String, instruction: String) {
        val medication = MedicationItem(
            id = System.currentTimeMillis().toString(),
            name = name,
            dose = dose.ifBlank { "Dose not set" },
            time = time.ifBlank { "Time not set" },
            instruction = instruction.ifBlank { "Follow your prescription" },
        )
        state = state.copy(companion = state.companion.copy(
            medications = state.companion.medications + medication,
        ))
    }

    fun toggleAppointmentReminder(id: String) {
        state = state.copy(companion = state.companion.copy(
            appointments = state.companion.appointments.map {
                if (it.id == id) it.copy(reminderEnabled = !it.reminderEnabled) else it
            },
        ))
    }

    fun addAppointment(hospital: String, date: String, time: String) {
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
        val appointment = AppointmentItem(
            id = System.currentTimeMillis().toString(),
            doctor = "Hospital appointment",
            specialty = "",
            day = parsedDate?.dayOfWeek?.name?.take(3) ?: "NEW",
            date = parsedDate?.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH))?.uppercase() ?: date.ifBlank { "DATE TBD" }.uppercase(),
            time = time.ifBlank { "Time TBD" },
            location = hospital.ifBlank { "Hospital" },
            dateIso = parsedDate?.toString(),
        )
        state = state.copy(companion = state.companion.copy(
            appointments = listOf(appointment) + state.companion.appointments,
        ))
    }

    fun completeIntake(appointmentId: String) {
        state = state.copy(companion = state.companion.copy(
            appointments = state.companion.appointments.map {
                if (it.id == appointmentId) it.copy(intakeComplete = true) else it
            },
        ))
    }

    fun setNotifications(enabled: Boolean) {
        state = state.copy(companion = state.companion.copy(notificationsEnabled = enabled))
    }

    fun setMedicalConditions(conditions: Set<String>, onSuccess: () -> Unit = {}) {
        val session = state.session ?: return
        val normalized = conditions.map { condition ->
            if (condition.equals("None", true) || condition.equals("None of these", true)) "none"
            else condition.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        }.ifEmpty { listOf("none") }
        runRequest(successMessage = "Medical conditions updated") {
            api.updateMedicalConditions(session, normalized)
            loadDashboard(session)
            onSuccess()
        }
    }

    fun completeProfileIdentity(identity: String, onSuccess: () -> Unit = {}) {
        val session = state.session ?: return
        runRequest(successMessage = "ABHA ID saved to your profile") {
            api.linkAbha(session, identity)
            loadDashboard(session)
            onSuccess()
        }
    }

    fun saveEmergencyCard(contact: String, phone: String) {
        state = state.copy(companion = state.companion.copy(
            emergencyContact = contact,
            emergencyPhone = phone,
        ))
    }

    fun saveAiNote(title: String, summary: String) {
        val note = AiHealthNote(
            id = System.currentTimeMillis().toString(),
            title = title.ifBlank { "Health check-in" },
            summary = summary,
            createdLabel = "Today",
        )
        state = state.copy(companion = state.companion.copy(
            aiNotes = listOf(note) + state.companion.aiNotes,
        ))
    }

    fun upload(file: File, mimeType: String, displayName: String) {
        val session = state.session ?: return
        runRequest(successMessage = "Document added to your health record") {
            api.uploadDocument(session, file, mimeType, displayName)
            loadDashboard(session)
        }
    }

    fun logout() {
        sessionStore.clear()
        state = MainUiState()
    }

    fun clearMessage() { state = state.copy(message = null) }

    private suspend fun loadDashboard(session: AuthSession) {
        state = state.copy(dashboard = api.dashboard(session), dashboardError = null)
    }

    private fun runRequest(successMessage: String? = null, block: suspend () -> Unit) {
        if (state.loading) return
        viewModelScope.launch {
            state = state.copy(loading = true, message = null, dashboardError = null)
            try {
                block()
                state = state.copy(loading = false, message = successMessage)
            } catch (error: Exception) {
                val message = error.message ?: "Something went wrong. Check the backend connection and try again."
                if (error is ApiException && error.status == 401) {
                    sessionStore.clear()
                    state = MainUiState(message = message)
                } else {
                    state = state.copy(
                        loading = false,
                        message = if (state.dashboard == null) null else message,
                        dashboardError = if (state.dashboard == null) message else null,
                    )
                }
            }
        }
    }
}

data class RegistrationDraft(
    val identityMethod: String,
    val identityNumber: String,
    val fullName: String,
    val dateOfBirth: String,
    val gender: String,
    val heightCm: Double,
    val weightKg: Double,
    val bloodGroup: String,
    val conditions: List<String>,
    val allergies: String,
    val epin: String,
) {
    fun toRegistration(phone: String, requestId: String, verificationToken: String) = Registration(
        phone, requestId, verificationToken, identityMethod, identityNumber, fullName, dateOfBirth,
        gender, heightCm, weightKg, bloodGroup, conditions, allergies, epin,
    )
}
