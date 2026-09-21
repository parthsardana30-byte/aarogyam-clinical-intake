package `in`.aarogyam.patient

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.aarogyam.patient.ui.AarogyamApp
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.data.HealthDocument
import `in`.aarogyam.patient.ui.theme.AarogyamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val incomingIntakeLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingIntakeLink.value = intent?.dataString
        enableEdgeToEdge()
        setContent {
            AarogyamTheme {
                val patientViewModel: MainViewModel = viewModel()
                val scope = rememberCoroutineScope()
                val pendingDownload = remember { mutableStateOf<HealthDocument?>(null) }
                val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    val document = pendingDownload.value
                    pendingDownload.value = null
                    val destination = result.data?.data
                    if (result.resultCode == RESULT_OK && document != null && destination != null) {
                        patientViewModel.downloadDocument(document, destination)
                    }
                }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri ?: return@rememberLauncherForActivityResult
                    scope.launch {
                        var stagedFile: File? = null
                        try {
                            val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            } ?: "Health document"
                            val reportedType = contentResolver.getType(uri)
                            val type = if (reportedType in setOf("application/pdf", "image/jpeg", "image/png", "image/webp")) reportedType!!
                                else when (displayName.substringAfterLast('.', "").lowercase()) {
                                    "pdf" -> "application/pdf"
                                    "png" -> "image/png"
                                    "webp" -> "image/webp"
                                    "jpg", "jpeg" -> "image/jpeg"
                                    else -> throw IllegalArgumentException("Choose a PDF, JPG, PNG or WebP file")
                                }
                            val file = withContext(Dispatchers.IO) {
                                val suffix = when (type) {
                                    "application/pdf" -> ".pdf"
                                    "image/png" -> ".png"
                                    "image/webp" -> ".webp"
                                    else -> ".jpg"
                                }
                                File.createTempFile("health-document-", suffix, cacheDir).also { target ->
                                    stagedFile = target
                                    val input = contentResolver.openInputStream(uri)
                                        ?: throw IllegalStateException("Could not open the selected file")
                                    input.use { source ->
                                        target.outputStream().use { output ->
                                            val buffer = ByteArray(8192)
                                            var total = 0L
                                            while (true) {
                                                val count = source.read(buffer)
                                                if (count < 0) break
                                                total += count
                                                if (total > 8L * 1024 * 1024) throw IllegalArgumentException("Choose a file smaller than 8 MB")
                                                output.write(buffer, 0, count)
                                            }
                                        }
                                    }
                                }
                            }
                            stagedFile = null
                            patientViewModel.upload(file, type, displayName)
                        } catch (error: Exception) {
                            stagedFile?.delete()
                            patientViewModel.reportUploadError(error.message ?: "Could not read the selected file")
                        }
                    }
                }
                AarogyamApp(
                    patientViewModel,
                    intakeLink = incomingIntakeLink.value,
                    onIntakeLinkConsumed = { incomingIntakeLink.value = null },
                    onPickDocument = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")) },
                    onDownloadDocument = { document ->
                        pendingDownload.value = document
                        saveDocument.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = document.type
                            putExtra(Intent.EXTRA_TITLE, document.name)
                        })
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntakeLink.value = intent.dataString
    }
}
