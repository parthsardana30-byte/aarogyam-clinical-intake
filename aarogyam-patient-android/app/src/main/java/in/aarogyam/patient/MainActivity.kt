package `in`.aarogyam.patient

import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.aarogyam.patient.ui.AarogyamApp
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.ui.theme.AarogyamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AarogyamTheme {
                val patientViewModel: MainViewModel = viewModel()
                val scope = rememberCoroutineScope()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri ?: return@rememberLauncherForActivityResult
                    scope.launch {
                        val type = contentResolver.getType(uri) ?: "application/octet-stream"
                        val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: "Health document"
                        val file = withContext(Dispatchers.IO) {
                            val suffix = when (type) {
                                "application/pdf" -> ".pdf"
                                "image/png" -> ".png"
                                "image/webp" -> ".webp"
                                else -> ".jpg"
                            }
                            File.createTempFile("health-document-", suffix, cacheDir).also { target ->
                                contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) }
                            }
                        }
                        patientViewModel.upload(file, type, displayName)
                    }
                }
                AarogyamApp(patientViewModel) {
                    picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
                }
            }
        }
    }
}
