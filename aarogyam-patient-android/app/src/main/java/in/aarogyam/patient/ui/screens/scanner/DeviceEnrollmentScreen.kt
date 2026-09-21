@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.aarogyam.patient.ui.screens.scanner

import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import `in`.aarogyam.patient.BuildConfig

@Composable
internal fun DeviceEnrollmentScreen(url: String, onAuthorized: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val webView = remember(url) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                    if (request.url.toString().startsWith(BuildConfig.API_BASE_URL.trimEnd('/') + "/#/login/device-patient")) {
                        onAuthorized()
                        return true
                    }
                    return !request.url.toString().startsWith(BuildConfig.API_BASE_URL.trimEnd('/') + "/device-enroll.html")
                }

                override fun onPageFinished(view: WebView, pageUrl: String) {
                    if (pageUrl.startsWith(BuildConfig.API_BASE_URL.trimEnd('/') + "/#/login/device-patient")) onAuthorized()
                }
            }
            loadUrl(url)
        }
    }
    DisposableEffect(webView) { onDispose { webView.stopLoading(); webView.destroy() } }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Authorize this phone") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize().padding(padding))
    }
}
