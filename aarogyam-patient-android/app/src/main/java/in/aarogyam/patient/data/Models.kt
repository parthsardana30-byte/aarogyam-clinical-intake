package `in`.aarogyam.patient.data

data class PatientSummary(val id: String, val fullName: String)

data class AuthSession(
    val patient: PatientSummary,
    val token: String,
)

data class PatientProfile(
    val id: String,
    val fullName: String,
    val phone: String,
    val dateOfBirth: String,
    val gender: String,
    val heightCm: Double,
    val weightKg: Double,
    val bloodGroup: String,
    val conditions: List<String>,
    val allergies: String?,
    val identityMethod: String,
    val identityLast4: String,
    val abhaLinkStatus: String,
    val abhaLast4: String,
    val abhaNumber: String = "",
)

data class DoctorSummary(
    val id: String,
    val name: String,
    val specialty: String,
)

data class VisitVitals(
    val heartRate: Int,
    val oxygenSaturation: Int,
    val systolic: Int,
    val diastolic: Int,
    val recordedAt: String,
)

data class PatientVisit(
    val id: String,
    val encounterNumber: String,
    val hospitalName: String,
    val hospitalLocation: String,
    val uhid: String,
    val summary: String?,
    val evaluationStatus: String,
    val createdAt: String,
    val doctor: DoctorSummary?,
    val vitals: VisitVitals?,
    val documents: List<HealthDocument>,
)

data class HealthDocument(
    val id: String,
    val name: String,
    val type: String,
    val size: Long,
    val createdAt: String,
    val previewUrl: String,
    val category: String = "other",
    val hospitalId: String? = null,
    val intakeId: String? = null,
    val analysisStatus: String = "not-started",
    val aiSummary: String? = null,
    val deletable: Boolean = false,
)

data class AccessHistoryEntry(
    val id: String,
    val doctorName: String,
    val specialty: String,
    val hospitalName: String,
    val hospitalLocation: String,
    val accessType: String,
    val accessedAt: String,
)

data class Dashboard(
    val patient: PatientProfile,
    val consultationCount: Int,
    val documentCount: Int,
    val visits: List<PatientVisit>,
    val accessHistory: List<AccessHistoryEntry>,
    val documents: List<HealthDocument>,
)

data class OtpRequest(val id: String, val demoOtp: String?)
data class OtpVerification(val requestId: String, val token: String)
data class OtpProviderConfig(
    val provider: String,
    val widgetId: String = "",
    val tokenAuth: String = "",
)

data class Registration(
    val phone: String,
    val otpRequestId: String,
    val otpVerificationToken: String,
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
)
