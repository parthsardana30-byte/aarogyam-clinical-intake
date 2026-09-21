package `in`.aarogyam.patient.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String, val status: Int) : Exception(message)

data class MobileIntakeGrant(val token: String, val hospitalName: String, val expiresAt: String)

class ApiClient(private val baseUrl: String) {
    private suspend fun request(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
        cookie: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (cookie != null) connection.setRequestProperty("Cookie", cookie)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (status !in 200..299) {
                val message = json.optString("error").ifBlank {
                    when (status) {
                        413 -> "The server rejected this file as too large. Check the VPS upload limit."
                        404, 405 -> "This feature is not available on the VPS yet."
                        else -> "Request failed (HTTP $status)"
                    }
                }
                throw ApiException(message, status)
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    suspend fun loginWithPhone(phone: String, epin: String): AuthSession {
        val json = request("POST", "/api/patient-login", body = JSONObject()
            .put("phone", phone.filter(Char::isDigit))
            .put("epin", epin.filter(Char::isDigit)))
        return json.toAuthSession()
    }

    suspend fun redeemHospitalQr(session: AuthSession, code: String): MobileIntakeGrant {
        val json = try {
            request("POST", "/api/mobile-intake/redeem", session.token,
                JSONObject().put("patientId", session.patient.id).put("code", code))
        } catch (error: ApiException) {
            if (error.status == 404 || error.status == 405) {
                throw ApiException("AI check-up has not been enabled on the hospital server yet.", error.status)
            }
            throw error
        }
        return MobileIntakeGrant(json.getString("grant"), json.optString("hospitalName", "Hospital"), json.getString("expiresAt"))
    }

    suspend fun isAuthorizedPatientDevice(cookie: String): Boolean {
        if (!cookie.split(';').any { it.trim().startsWith("arog_device=") }) return false
        val json = request("GET", "/api/device-session", cookie = cookie)
        return json.optBoolean("authorized") && json.optString("allowedRole") == "patient"
    }

    suspend fun loginWithVerifiedOtp(phone: String, requestId: String, verificationToken: String): AuthSession {
        val json = request("POST", "/api/patient-login-otp", body = JSONObject()
            .put("phone", phone.filter(Char::isDigit))
            .put("otpRequestId", requestId)
            .put("otpVerificationToken", verificationToken))
        return json.toAuthSession()
    }

    suspend fun requestOtp(phone: String, purpose: String = "register"): OtpRequest {
        val json = request("POST", "/api/signup-otp/request", body = JSONObject()
            .put("phone", phone.filter(Char::isDigit))
            .put("purpose", purpose))
        return OtpRequest(json.getString("id"), json.optString("demoOtp").ifBlank { null })
    }

    suspend fun checkRegistrationAvailability(phone: String) {
        request("POST", "/api/patient-registration-availability", body = JSONObject()
            .put("phone", phone.filter(Char::isDigit)))
    }

    suspend fun otpProviderConfig(): OtpProviderConfig {
        val json = request("GET", "/api/signup-otp/config?client=android")
        if (json.optString("client") != "android") {
            throw ApiException("The VPS backend needs the Android OTP update before mobile signup can work", 503)
        }
        return OtpProviderConfig(
            provider = json.optString("provider", "server"),
            widgetId = json.optString("widgetId"),
            tokenAuth = json.optString("tokenAuth"),
        )
    }

    suspend fun verifyOtp(phone: String, requestId: String, otp: String, purpose: String = "register"): OtpVerification {
        val json = request("POST", "/api/signup-otp/verify", body = JSONObject()
            .put("phone", phone.filter(Char::isDigit))
            .put("id", requestId)
            .put("otp", otp.filter(Char::isDigit))
            .put("purpose", purpose))
        return OtpVerification(json.optString("id", requestId), json.getString("verificationToken"))
    }

    suspend fun verifyMsg91AccessToken(phone: String, accessToken: String, purpose: String): OtpVerification {
        val json = request("POST", "/api/signup-otp/verify", body = JSONObject()
            .put("phone", phone.filter(Char::isDigit))
            .put("accessToken", accessToken)
            .put("purpose", purpose))
        return OtpVerification(json.getString("id"), json.getString("verificationToken"))
    }

    suspend fun resetEpin(phone: String, requestId: String, verificationToken: String, epin: String): AuthSession {
        val json = request("POST", "/api/patient-password-reset", body = JSONObject()
            .put("phone", phone.filter(Char::isDigit))
            .put("otpRequestId", requestId)
            .put("otpVerificationToken", verificationToken)
            .put("epin", epin.filter(Char::isDigit)))
        return json.toAuthSession()
    }

    suspend fun register(registration: Registration): AuthSession {
        val body = JSONObject()
            .put("phone", registration.phone.filter(Char::isDigit))
            .put("otpRequestId", registration.otpRequestId)
            .put("otpVerificationToken", registration.otpVerificationToken)
            .put("identityMethod", registration.identityMethod)
            .put("identityNumber", registration.identityNumber.filter(Char::isDigit))
            .put("fullName", registration.fullName)
            .put("dateOfBirth", registration.dateOfBirth)
            .put("gender", registration.gender)
            .put("heightCm", registration.heightCm)
            .put("weightKg", registration.weightKg)
            .put("bloodGroup", registration.bloodGroup)
            .put("conditions", JSONArray(registration.conditions))
            .put("allergies", registration.allergies)
            .put("epin", registration.epin)
        return request("POST", "/api/patient-registrations", body = body).toAuthSession()
    }

    suspend fun dashboard(session: AuthSession): Dashboard {
        val json = request("GET", "/api/patients/${session.patient.id}/dashboard", session.token)
        val accessHistory = accessHistory(session)
        val patient = json.getJSONObject("patient")
        val profile = patient.getJSONObject("profile")
        val health = patient.getJSONObject("health")
        val identity = patient.getJSONObject("identity")
        val summary = json.getJSONObject("summary")
        return Dashboard(
            patient = PatientProfile(
                id = patient.getString("id"),
                fullName = profile.getString("fullName"),
                phone = patient.getString("phone"),
                dateOfBirth = profile.getString("dateOfBirth"),
                gender = profile.getString("gender"),
                heightCm = profile.getDouble("heightCm"),
                weightKg = profile.getDouble("weightKg"),
                bloodGroup = profile.getString("bloodGroup"),
                conditions = health.getJSONArray("conditions").toStringList(),
                allergies = health.nullableString("allergies"),
                identityMethod = identity.getString("method"),
                identityLast4 = identity.getString("last4"),
                abhaLinkStatus = patient.getString("abhaLinkStatus"),
                abhaLast4 = patient.optString("abhaLast4"),
                abhaNumber = patient.optString("abhaNumber"),
            ),
            consultationCount = summary.getInt("consultationCount"),
            documentCount = summary.getInt("documentCount"),
            visits = json.optJSONArray("visits").orEmptyObjects().map { item ->
                val doctor = item.optJSONObject("doctor")?.let {
                    DoctorSummary(it.optString("id"), it.optString("name"), it.optString("specialty"))
                }
                val vitals = item.optJSONObject("vitals")?.let {
                    VisitVitals(
                        heartRate = it.optInt("heartRate"),
                        oxygenSaturation = it.optInt("oxygenSaturation"),
                        systolic = it.optInt("systolic"),
                        diastolic = it.optInt("diastolic"),
                        recordedAt = it.optString("recordedAt"),
                    )
                }
                PatientVisit(
                    id = item.getString("id"),
                    encounterNumber = item.optString("encounterNumber"),
                    hospitalName = item.optString("hospitalName", "Hospital"),
                    hospitalLocation = item.optString("hospitalLocation"),
                    uhid = item.optString("uhid"),
                    summary = item.nullableString("summary"),
                    evaluationStatus = item.optString("evaluationStatus", "awaiting_staff"),
                    createdAt = item.optString("createdAt"),
                    doctor = doctor,
                    vitals = vitals,
                    documents = item.optJSONArray("documents").orEmptyObjects().map(::parseDocument),
                )
            },
            accessHistory = accessHistory,
            documents = json.optJSONArray("documents").orEmptyObjects().map(::parseDocument),
        )
    }

    suspend fun linkAbha(session: AuthSession, abhaNumber: String) {
        request(
            "POST",
            "/api/patients/${session.patient.id}/link-abha",
            session.token,
            JSONObject().put("abhaNumber", abhaNumber.filter(Char::isDigit)),
        )
    }

    suspend fun updateMedicalConditions(session: AuthSession, conditions: List<String>) {
        request(
            "PUT",
            "/api/patients/${session.patient.id}/medical-conditions",
            session.token,
            JSONObject().put("conditions", JSONArray(conditions)),
        )
    }

    private suspend fun accessHistory(session: AuthSession): List<AccessHistoryEntry> {
        val json = request("GET", "/api/patients/${session.patient.id}/access-history", session.token)
        return json.optJSONArray("accessHistory").orEmptyObjects().map { item ->
            AccessHistoryEntry(
                id = item.optString("id"),
                doctorName = item.optString("doctorName", "Doctor"),
                specialty = item.optString("specialty"),
                hospitalName = item.optString("hospitalName", "Hospital"),
                hospitalLocation = item.optString("hospitalLocation"),
                accessType = item.optString("accessType", "record-view"),
                accessedAt = item.optString("accessedAt"),
            )
        }
    }

    suspend fun uploadDocument(session: AuthSession, file: File, mimeType: String, displayName: String): HealthDocument {
        val encoded = withContext(Dispatchers.IO) {
            if (file.length() == 0L || file.length() > 8L * 1024 * 1024) {
                throw ApiException("Choose a file smaller than 8 MB", 400)
            }
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }
        val json = request("POST", "/api/patients/${session.patient.id}/documents", session.token,
            JSONObject().put("name", displayName).put("type", mimeType).put("data", encoded))
        return parseDocument(json.getJSONObject("document"))
    }

    suspend fun fetchDocument(session: AuthSession, documentId: String): ByteArray = withContext(Dispatchers.IO) {
        val path = "/api/patients/${session.patient.id}/documents/$documentId"
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer ${session.token}")
            connection.setRequestProperty("Accept", "application/pdf,image/jpeg,image/png,image/webp")
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = runCatching { JSONObject(error).optString("error") }.getOrNull()
                throw ApiException(message?.ifBlank { null } ?: "Could not open this document", status)
            }
            val limit = 8 * 1024 * 1024
            if (connection.contentLengthLong > limit) throw ApiException("Document is too large to open", 413)
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > limit) throw ApiException("Document is too large to open", 413)
                    output.write(buffer, 0, count)
                }
            }
            output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun deleteDocument(session: AuthSession, documentId: String) {
        request("DELETE", "/api/patients/${session.patient.id}/documents/$documentId", session.token)
    }

    fun absoluteUrl(path: String) = baseUrl.trimEnd('/') + path

    private fun JSONObject.toAuthSession(): AuthSession {
        val patient = getJSONObject("patient")
        return AuthSession(PatientSummary(patient.getString("id"), patient.getString("fullName")), getString("sessionToken"))
    }

    private fun parseDocument(item: JSONObject) = HealthDocument(
        id = item.getString("id"),
        name = item.optString("name", "Medical document"),
        type = item.optString("type", "application/octet-stream"),
        size = item.optLong("size"),
        createdAt = item.optString("createdAt"),
        previewUrl = item.optString("previewUrl"),
        category = item.optString("category", "other"),
        hospitalId = item.nullableString("hospitalId"),
        intakeId = item.nullableString("intakeId"),
        analysisStatus = item.optString("analysisStatus", "not-started"),
        aiSummary = item.nullableString("aiSummary"),
        deletable = item.optBoolean("deletable"),
    )
}

private fun JSONArray.toStringList() = (0 until length()).map { getString(it) }
private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONArray?.orEmptyObjects() = this?.objects().orEmpty()
private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).trim().ifBlank { null }
