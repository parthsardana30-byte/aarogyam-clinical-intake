package `in`.aarogyam.patient.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect
import `in`.aarogyam.patient.ui.auth.AuthScreen
import `in`.aarogyam.patient.ui.navigation.PatientExperience

@Composable
fun AarogyamApp(viewModel: MainViewModel, onPickDocument: () -> Unit) {
    if (viewModel.state.session == null) {
        AuthScreen(viewModel)
    } else {
        LifecycleResumeEffect(Unit) {
            viewModel.refresh()
            onPauseOrDispose { }
        }
        PatientExperience(viewModel, onPickDocument)
    }
}
