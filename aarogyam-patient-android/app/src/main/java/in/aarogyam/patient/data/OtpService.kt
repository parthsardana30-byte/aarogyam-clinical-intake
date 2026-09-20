package `in`.aarogyam.patient.data

import com.msg91.sendotp.OTPWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OtpService(private val api: ApiClient) {
    private var config: OtpProviderConfig? = null

    private suspend fun config(): OtpProviderConfig = config ?: api.otpProviderConfig().also { config = it }

    suspend fun send(phone: String, purpose: String): OtpRequest {
        val provider = config()
        if (provider.provider != "msg91") return api.requestOtp(phone, purpose)
        if (provider.widgetId.isBlank() || provider.tokenAuth.isBlank()) {
            throw ApiException("MSG91 mobile OTP is not configured", 503)
        }
        val response = withContext(Dispatchers.IO) {
            OTPWidget.sendOTP(provider.widgetId, provider.tokenAuth, "91${phone.filter(Char::isDigit)}")
        }
        val json = JSONObject(response)
        if (json.optString("type").equals("error", true)) {
            throw ApiException(json.optString("message", "Could not send OTP"), 502)
        }
        val requestId = json.optString("message")
        if (requestId.isBlank()) throw ApiException("MSG91 returned an invalid OTP request", 502)
        return OtpRequest(requestId, null)
    }

    suspend fun verify(phone: String, requestId: String, otp: String, purpose: String): OtpVerification {
        val provider = config()
        if (provider.provider != "msg91") return api.verifyOtp(phone, requestId, otp, purpose)
        val response = withContext(Dispatchers.IO) {
            OTPWidget.verifyOTP(provider.widgetId, provider.tokenAuth, requestId, otp.filter(Char::isDigit))
        }
        val json = JSONObject(response)
        if (json.optString("type").equals("error", true)) {
            throw ApiException(json.optString("message", "Incorrect OTP"), 401)
        }
        val accessToken = json.optString("message")
        if (accessToken.isBlank()) throw ApiException("MSG91 did not return a verification token", 502)
        return api.verifyMsg91AccessToken(phone, accessToken, purpose)
    }
}
