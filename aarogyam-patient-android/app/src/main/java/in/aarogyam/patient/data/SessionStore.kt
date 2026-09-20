package `in`.aarogyam.patient.data

import android.content.Context

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("patient_session", Context.MODE_PRIVATE)

    fun read(): AuthSession? {
        val id = preferences.getString("patient_id", null) ?: return null
        val name = preferences.getString("patient_name", null) ?: return null
        val token = preferences.getString("token", null) ?: return null
        return AuthSession(PatientSummary(id, name), token)
    }

    fun save(session: AuthSession) {
        preferences.edit()
            .putString("patient_id", session.patient.id)
            .putString("patient_name", session.patient.fullName)
            .putString("token", session.token)
            .apply()
    }

    fun clear() = preferences.edit().clear().apply()
}
