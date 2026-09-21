package `in`.aarogyam.patient.data

import com.msg91.sendotp.OTPWidget
import `in`.aarogyam.patient.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OtpService(private val api: ApiClient) {
    private var config: OtpProviderConfig? = null

    private suspend fun config(): OtpProviderConfig = config ?: run {
        val configured = if (
            BuildConfig.MSG91_MOBILE_WIDGET_ID.isNotBlank() &&
            BuildConfig.MSG91_MOBILE_WIDGET_TOKEN.isNotBlank()
        ) {
            OtpProviderConfig(
                provider = "msg91",
                widgetId = BuildConfig.MSG91_MOBILE_WIDGET_ID,
                tokenAuth = BuildConfig.MSG91_MOBILE_WIDGET_TOKEN,
            )
        } else {
            api.otpProviderConfig()
        }
        config = configured
        configured
    }

    suspend fun send(phone: String, purpose: String): OtpRequest {
        val provider = config()
        if (provider.provider == "server") return api.requestOtp(phone, purpose)
        if (purpose == "register") api.checkRegistrationAvailability(phone)
        if (provider.provider != "msg91") {
            throw ApiException("Mobile OTP widget is not configured on the backend", 503)
        }
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
        if (provider.provider == "server") return api.verifyOtp(phone, requestId, otp, purpose)
        if (provider.provider != "msg91") {
            throw ApiException("Mobile OTP widget is not configured on the backend", 503)
        }
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
