package `in`.aarogyam.patient.ui

import android.app.Application
import android.net.Uri
import android.webkit.CookieManager
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
import `in`.aarogyam.patient.data.HealthDocument
import `in`.aarogyam.patient.data.MobileIntakeGrant
import `in`.aarogyam.patient.data.OtpRequest
import `in`.aarogyam.patient.data.OtpService
import `in`.aarogyam.patient.data.Registration
import `in`.aarogyam.patient.data.SessionStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import org.json.JSONArray
import org.json.JSONObject
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
    val medications: List<MedicationItem> = emptyList(),
    val appointments: List<AppointmentItem> = emptyList(),
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

data class DocumentPreviewState(
    val document: HealthDocument? = null,
    val file: File? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class DocumentUploadState(
    val fileName: String = "",
    val uploading: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application)
    private val companionPreferences = application.getSharedPreferences("patient_companion", 0)
    private var api = ApiClient(BuildConfig.API_BASE_URL)
    private val otpService = OtpService(api)
    var state by mutableStateOf(MainUiState(session = sessionStore.read()))
        private set
    var documentPreview by mutableStateOf(DocumentPreviewState())
        private set
    var documentUpload by mutableStateOf(DocumentUploadState())
        private set
    var intakeGrant by mutableStateOf<MobileIntakeGrant?>(null)
        private set
    var intakeQrError by mutableStateOf<String?>(null)
        private set
    var intakeQrBusy by mutableStateOf(false)
        private set
    var enrollmentUrl by mutableStateOf<String?>(null)
        private set
    private var uploadJob: Job? = null
    private var authorizationCheckInProgress = false
    private var dashboardLoadVersion = 0

    init {
        val savedLanguage = companionPreferences.getString("language", null)
            ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
        state = state.copy(companion = state.companion.copy(
            language = savedLanguage ?: state.companion.language,
            appointments = state.session?.let { loadAppointments(it.patient.id) }.orEmpty(),
        ))
        if (state.session != null) refresh()
    }

    fun login(phone: String, epin: String) = runRequest {
        val session = api.loginWithPhone(phone, epin)
        activateSession(session)
        loadDashboard(session)
    }

    fun loginWithOtp(phone: String, requestId: String, otp: String) = runRequest {
        val verification = otpService.verify(phone, requestId, otp, "login")
        val session = api.loginWithVerifiedOtp(phone, verification.requestId, verification.token)
        activateSession(session)
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
        activateSession(session)
        loadDashboard(session)
    }

    fun resetEpin(phone: String, requestId: String, verificationToken: String, epin: String) = runRequest {
        val session = api.resetEpin(phone, requestId, verificationToken, epin)
        activateSession(session)
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
        updateAppointments(state.companion.appointments.map {
                if (it.id == id) it.copy(reminderEnabled = !it.reminderEnabled) else it
            })
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
        updateAppointments(listOf(appointment) + state.companion.appointments)
    }

    fun scanHospitalQr(rawValue: String, onSuccess: () -> Unit) {
        val session = state.session ?: return
        val qr = runCatching { Uri.parse(rawValue.trim()) }.getOrNull()
        val code = if (qr?.scheme == "arogyam" && qr.host == "intake") qr.getQueryParameter("code") else null
        if (code == null || (code != "AROGYAM-AI-CHECKUP-V1" && !Regex("[a-zA-Z0-9_-]{32}").matches(code))) {
            intakeQrError = "This is not an Arogyam hospital intake QR. Ask reception for the current QR."
            return
        }
        if (intakeQrBusy) return
        intakeQrError = null
        intakeQrBusy = true
        viewModelScope.launch {
            try {
                intakeGrant = if (code == "AROGYAM-AI-CHECKUP-V1") {
                    val cookie = CookieManager.getInstance().getCookie(BuildConfig.API_BASE_URL).orEmpty()
                    if (!api.isAuthorizedPatientDevice(cookie)) {
                        throw IllegalStateException("Authorize this phone first: scan the QR from the hospital staff portal, then scan the AI QR.")
                    }
                    MobileIntakeGrant("", "Hospital check-up", "")
                } else api.redeemHospitalQr(session, code)
                onSuccess()
            } catch (error: Exception) {
                intakeQrError = error.message ?: "Could not verify this QR. Scan the latest code on the hospital screen."
            } finally {
                intakeQrBusy = false
            }
        }
    }

    fun prepareDeviceAuthorization(rawValue: String): Boolean {
        val qr = runCatching { Uri.parse(rawValue.trim()) }.getOrNull() ?: return false
        val base = Uri.parse(BuildConfig.API_BASE_URL)
        if (qr.scheme !in setOf("http", "https") || qr.host != base.host || qr.path != "/device-enroll.html") return false
        val enrollment = qr.getQueryParameter("enrollment")?.takeIf { it.isNotBlank() } ?: return false
        val token = qr.getQueryParameter("token")?.takeIf { it.isNotBlank() } ?: return false
        enrollmentUrl = base.buildUpon().path("/device-enroll.html").clearQuery()
            .appendQueryParameter("enrollment", enrollment).appendQueryParameter("token", token).build().toString()
        intakeQrError = null
        return true
    }

    fun finishDeviceAuthorization(onSuccess: () -> Unit) {
        if (authorizationCheckInProgress || enrollmentUrl == null) return
        authorizationCheckInProgress = true
        viewModelScope.launch {
            try {
                val cookie = CookieManager.getInstance().getCookie(BuildConfig.API_BASE_URL).orEmpty()
                if (!api.isAuthorizedPatientDevice(cookie)) throw IllegalStateException("Staff approval is still pending. Keep the authorization page open.")
                CookieManager.getInstance().flush()
                enrollmentUrl = null
                intakeQrError = null
                state = state.copy(message = "Phone authorized. Now scan the AI check-up QR.")
                onSuccess()
            } catch (error: Exception) {
                intakeQrError = error.message ?: "Could not confirm device authorization"
            } finally {
                authorizationCheckInProgress = false
            }
        }
    }

    fun deleteAppointment(id: String) {
        updateAppointments(state.companion.appointments.filterNot { it.id == id })
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
        val session = state.session ?: run { file.delete(); return }
        if (uploadJob?.isActive == true) {
            file.delete()
            documentUpload = DocumentUploadState(displayName, error = "Another file is uploading. Try again when it finishes.")
            return
        }
        documentUpload = DocumentUploadState(displayName, uploading = true)
        uploadJob = viewModelScope.launch {
            try {
                api.uploadDocument(session, file, mimeType, displayName)
                documentUpload = DocumentUploadState(displayName, completed = true)
                try {
                    loadDashboard(session)
                } catch (_: Exception) {
                    documentUpload = DocumentUploadState(displayName, completed = true,
                        error = "Saved on the server, but the list did not refresh. Tap Refresh in Files.")
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message = when {
                    error is ApiException && error.status == 413 -> "This PDF is too large for the server. Choose a file under 8 MB."
                    error is ApiException && error.status in listOf(404, 405) -> "Document upload is not enabled on the server yet."
                    else -> error.message ?: "Upload failed. Check your connection and try again."
                }
                documentUpload = DocumentUploadState(displayName, error = message)
            } finally {
                file.delete()
            }
        }
    }

    fun reportUploadError(message: String) {
        documentUpload = DocumentUploadState(error = message)
    }

    fun openDocument(document: HealthDocument) {
        val session = state.session ?: return
        closeDocument()
        documentPreview = DocumentPreviewState(document = document, loading = true)
        viewModelScope.launch {
            try {
                val bytes = api.fetchDocument(session, document.id)
                val file = withContext(Dispatchers.IO) {
                    File.createTempFile("patient-document-", if (document.type == "application/pdf") ".pdf" else ".img", getApplication<Application>().cacheDir)
                        .also { it.writeBytes(bytes) }
                }
                if (documentPreview.document?.id == document.id) {
                    documentPreview = DocumentPreviewState(document = document, file = file)
                } else {
                    withContext(Dispatchers.IO) { file.delete() }
                }
            } catch (error: Exception) {
                if (documentPreview.document?.id == document.id) {
                    documentPreview = DocumentPreviewState(document = document, error = error.message ?: "Could not open this document")
                }
            }
        }
    }

    fun closeDocument() {
        documentPreview.file?.delete()
        documentPreview = DocumentPreviewState()
    }

    fun downloadDocument(document: HealthDocument, destination: Uri) {
        val session = state.session ?: return
        runRequest(successMessage = "Document saved to your phone") {
            try {
                val bytes = api.fetchDocument(session, document.id)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(destination)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Could not write to the selected location")
                }
            } catch (error: Exception) {
                withContext(Dispatchers.IO) {
                    runCatching { getApplication<Application>().contentResolver.delete(destination, null, null) }
                }
                throw error
            }
        }
    }

    fun deleteDocument(document: HealthDocument) {
        val session = state.session ?: return
        if (!document.deletable) return
        runRequest(successMessage = "Document deleted") {
            api.deleteDocument(session, document.id)
            if (documentPreview.document?.id == document.id) closeDocument()
            loadDashboard(session)
        }
    }

    fun reportError(message: String) { state = state.copy(message = message) }

    fun logout() {
        uploadJob?.cancel()
        documentUpload = DocumentUploadState()
        intakeGrant = null
        intakeQrError = null
        closeDocument()
        state.session?.let { companionPreferences.edit().remove(appointmentKey(it.patient.id)).apply() }
        sessionStore.clear()
        state = MainUiState()
    }

    fun clearMessage() { state = state.copy(message = null) }

    private suspend fun loadDashboard(session: AuthSession) {
        val version = ++dashboardLoadVersion
        val dashboard = api.dashboard(session)
        if (version == dashboardLoadVersion && state.session?.patient?.id == session.patient.id) {
            state = state.copy(dashboard = dashboard, dashboardError = null)
        }
    }

    private fun activateSession(session: AuthSession) {
        sessionStore.save(session)
        state = state.copy(session = session, companion = state.companion.copy(
            appointments = loadAppointments(session.patient.id),
        ))
    }

    private fun appointmentKey(patientId: String) = "appointments_$patientId"

    private fun loadAppointments(patientId: String): List<AppointmentItem> {
        val raw = companionPreferences.getString(appointmentKey(patientId), "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val dateIso = item.getString("date")
                val parsed = LocalDate.parse(dateIso)
                AppointmentItem(
                    id = item.getString("id"), doctor = "Hospital appointment", specialty = "",
                    day = parsed.dayOfWeek.name.take(3),
                    date = parsed.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)).uppercase(),
                    time = item.getString("time"), location = item.getString("hospital"), dateIso = dateIso,
                    reminderEnabled = item.optBoolean("enabled", true),
                )
            }.filter { it.dateIso?.let(LocalDate::parse)?.isBefore(LocalDate.now()) == false }
        }.getOrDefault(emptyList())
    }

    private fun updateAppointments(appointments: List<AppointmentItem>) {
        state = state.copy(companion = state.companion.copy(appointments = appointments))
        val patientId = state.session?.patient?.id ?: return
        val array = JSONArray()
        appointments.forEach { item ->
            if (item.dateIso != null) array.put(JSONObject()
                .put("id", item.id).put("hospital", item.location).put("date", item.dateIso)
                .put("time", item.time).put("enabled", item.reminderEnabled))
        }
        companionPreferences.edit().putString(appointmentKey(patientId), array.toString()).apply()
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
                    closeDocument()
                    sessionStore.clear()
                    state = MainUiState(message = message)
                } else {
                    state = state.copy(
                        loading = false,
                        message = message,
                        dashboardError = if (state.session != null) message else null,
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
