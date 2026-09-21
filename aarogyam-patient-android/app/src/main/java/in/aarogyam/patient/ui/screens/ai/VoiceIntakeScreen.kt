@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.aarogyam.patient.ui.screens.ai

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import `in`.aarogyam.patient.BuildConfig
import `in`.aarogyam.patient.data.AuthSession
import `in`.aarogyam.patient.data.MobileIntakeGrant
import `in`.aarogyam.patient.ui.AppLanguage
import org.json.JSONObject

@Composable
internal fun VoiceIntakeScreen(session: AuthSession, grant: MobileIntakeGrant, language: AppLanguage, onClose: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val webView = remember(session.patient.id, grant.token, language) {
        val config = JSONObject()
            .put("patientId", session.patient.id)
            .put("patientToken", session.token)
            .put("grant", grant.token)
            .put("hospitalName", grant.hospitalName)
            .put("language", language.label)
            .put("voiceLanguage", if (language == AppLanguage.Hindi) "hi" else "en")
            .toString().replace("<", "\\u003c")
        val html = context.assets.open("voice_intake.html").bufferedReader().use { it.readText() }
            .replace("__AROGYAM_CONFIG__", config)
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Audio replies arrive after an asynchronous model request, well after the tap that starts the call.
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean = true
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    val allowedHost = Uri.parse(BuildConfig.API_BASE_URL).host
                    val microphoneAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (request.origin.host == allowedHost && microphoneAllowed &&
                        request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    } else request.deny()
                }
            }
            loadDataWithBaseURL(BuildConfig.API_BASE_URL.trimEnd('/') + "/", html, "text/html", "UTF-8", null)
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.stopLoading(); webView.destroy() }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Aarogyam AI") }, navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize().padding(padding))
    }
}
