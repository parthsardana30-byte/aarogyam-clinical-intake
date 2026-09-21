@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.aarogyam.patient.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import `in`.aarogyam.patient.ui.MainViewModel
import `in`.aarogyam.patient.data.HealthDocument
import `in`.aarogyam.patient.ui.components.ArogyamBrand
import `in`.aarogyam.patient.ui.components.LanguageMenu
import `in`.aarogyam.patient.ui.components.tr
import `in`.aarogyam.patient.ui.screens.appointments.AddAppointmentScreen
import `in`.aarogyam.patient.ui.screens.care.CareScreen
import `in`.aarogyam.patient.ui.screens.history.AccessHistoryScreen
import `in`.aarogyam.patient.ui.screens.home.TodayScreen
import `in`.aarogyam.patient.ui.screens.profile.CompleteProfileScreen
import `in`.aarogyam.patient.ui.screens.profile.MedicalConditionsScreen
import `in`.aarogyam.patient.ui.screens.profile.ProfileScreen
import `in`.aarogyam.patient.ui.screens.records.RecordsScreen
import `in`.aarogyam.patient.ui.screens.records.DocumentViewerScreen
import `in`.aarogyam.patient.ui.screens.scanner.ScannerScreen
import `in`.aarogyam.patient.ui.screens.scanner.DeviceEnrollmentScreen
import `in`.aarogyam.patient.ui.screens.ai.VoiceIntakeScreen
import `in`.aarogyam.patient.ui.theme.Canvas
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.Leaf
import `in`.aarogyam.patient.ui.theme.Mint
import `in`.aarogyam.patient.ui.theme.Muted
import `in`.aarogyam.patient.ui.theme.SoftWhite

internal enum class PatientDestination(val route: String) {
    Home("home"),
    Files("files"),
    Scanner("scanner"),
    AccessHistory("access-history"),
    Profile("profile"),
    Care("care"),
    AddAppointment("add-appointment"),
    CompleteProfile("complete-profile"),
    MedicalConditions("medical-conditions"),
    DocumentViewer("document-viewer"),
    VoiceIntake("voice-intake"),
    DeviceAuthorization("device-authorization"),
}

private data class MainNavItem(
    val destination: PatientDestination,
    val icon: ImageVector,
    val label: String,
    val primary: Boolean = false,
)

@Composable
fun PatientExperience(viewModel: MainViewModel, intakeLink: String?, onIntakeLinkConsumed: () -> Unit,
                      onPickDocument: () -> Unit, onDownloadDocument: (HealthDocument) -> Unit) {
    val navController = rememberNavController()
    val state = viewModel.state
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            viewModel.clearMessage()
            snackbarHostState.showSnackbar(message)
        }
    }
    val language = state.companion.language
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: PatientDestination.Home.route
    LaunchedEffect(currentRoute) {
        if (currentRoute == PatientDestination.Files.route) viewModel.refresh()
    }
    val mainItems = listOf(
        MainNavItem(PatientDestination.Home, Icons.Default.Home, tr(language, "home")),
        MainNavItem(PatientDestination.Files, Icons.Default.Folder, tr(language, "files")),
        MainNavItem(PatientDestination.Scanner, Icons.Default.Home, tr(language, "scan"), primary = true),
        MainNavItem(PatientDestination.AccessHistory, Icons.Default.History, tr(language, "history")),
        MainNavItem(PatientDestination.Profile, Icons.Default.Person, tr(language, "profile")),
    )
    val chromeRoutes = mainItems.map { it.destination.route } + PatientDestination.Care.route
    val showAppChrome = currentRoute in chromeRoutes

    fun navigateTo(destination: PatientDestination) {
        navController.navigate(destination.route) {
            if (destination.route in mainItems.map { it.destination.route }) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(intakeLink, state.session?.patient?.id) {
        if (intakeLink != null && state.session != null) {
            onIntakeLinkConsumed()
            navigateTo(PatientDestination.Scanner)
            viewModel.scanHospitalQr(intakeLink) { navigateTo(PatientDestination.VoiceIntake) }
        }
    }

    Scaffold(
        containerColor = Canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showAppChrome) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas.copy(alpha = .97f)),
                    title = { ArogyamBrand(compact = true) },
                    actions = {
                        LanguageMenu(language, viewModel::setLanguage)
                        Spacer(Modifier.width(8.dp))
                    },
                )
            }
        },
        bottomBar = {
            if (showAppChrome) {
                PatientBottomBar(mainItems, currentRoute) { navigateTo(it) }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = PatientDestination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(PatientDestination.Home.route) {
                TodayScreen(
                    viewModel = viewModel,
                    dashboard = state.dashboard,
                    onUploadRecord = onPickDocument,
                    onOpenAppointmentReminders = { navigateTo(PatientDestination.Care) },
                )
            }
            composable(PatientDestination.Files.route) {
                RecordsScreen(viewModel, state.dashboard,
                    onPickDocument = onPickDocument,
                    onOpenDocument = { document ->
                        viewModel.openDocument(document)
                        navigateTo(PatientDestination.DocumentViewer)
                    },
                    onDownloadDocument = onDownloadDocument,
                    onDeleteDocument = viewModel::deleteDocument,
                )
            }
            composable(PatientDestination.DocumentViewer.route) {
                DocumentViewerScreen(viewModel,
                    onBack = { viewModel.closeDocument(); navController.popBackStack() },
                    onDownload = onDownloadDocument,
                )
            }
            composable(PatientDestination.Scanner.route) {
                ScannerScreen(language = language, busy = viewModel.intakeQrBusy, error = viewModel.intakeQrError,
                    onQrScanned = { raw ->
                        if (viewModel.prepareDeviceAuthorization(raw)) navigateTo(PatientDestination.DeviceAuthorization)
                        else viewModel.scanHospitalQr(raw) { navigateTo(PatientDestination.VoiceIntake) }
                    })
            }
            composable(PatientDestination.DeviceAuthorization.route) {
                viewModel.enrollmentUrl?.let { url ->
                    DeviceEnrollmentScreen(url,
                        onAuthorized = { viewModel.finishDeviceAuthorization { navController.popBackStack() } },
                        onBack = { navController.popBackStack() })
                }
            }
            composable(PatientDestination.VoiceIntake.route) {
                val grant = viewModel.intakeGrant
                val session = state.session
                if (grant != null && session != null) VoiceIntakeScreen(session, grant, language,
                    onClose = { viewModel.refresh(); navController.popBackStack() })
            }
            composable(PatientDestination.AccessHistory.route) { AccessHistoryScreen(state.dashboard, language) }
            composable(PatientDestination.Profile.route) {
                ProfileScreen(
                    viewModel = viewModel,
                    dashboard = state.dashboard,
                    onCompleteProfile = { navigateTo(PatientDestination.CompleteProfile) },
                    onMedicalConditions = { navigateTo(PatientDestination.MedicalConditions) },
                )
            }
            composable(PatientDestination.Care.route) {
                CareScreen(
                    viewModel = viewModel,
                    onAddAppointment = { navigateTo(PatientDestination.AddAppointment) },
                    onUploadRecord = onPickDocument,
                )
            }
            composable(PatientDestination.AddAppointment.route) { AddAppointmentScreen(viewModel, navController::popBackStack) }
            composable(PatientDestination.CompleteProfile.route) { CompleteProfileScreen(viewModel, navController::popBackStack) }
            composable(PatientDestination.MedicalConditions.route) { MedicalConditionsScreen(viewModel, navController::popBackStack) }
        }
    }
}

@Composable
private fun PatientBottomBar(items: List<MainNavItem>, currentRoute: String, onSelect: (PatientDestination) -> Unit) {
    Box(Modifier.fillMaxWidth().height(66.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(66.dp),
            color = SoftWhite,
            tonalElevation = 5.dp,
            shadowElevation = 12.dp,
        ) {}
        Box(
            Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter).background(`in`.aarogyam.patient.ui.theme.Border),
        )
        Row(
            Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                BottomNavItem(
                    item = item,
                    selected = currentRoute == item.destination.route,
                    modifier = Modifier.weight(if (item.primary) 1.18f else 1f),
                    onClick = { onSelect(item.destination) },
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(item: MainNavItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val itemModifier = if (item.primary) {
        modifier.clickable(onClick = onClick).padding(vertical = 3.dp)
    } else {
        modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 3.dp)
    }
    Column(
        modifier = itemModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (item.primary) {
            Box(
                Modifier.offset(y = (-32).dp).size(60.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(if (selected) DeepTeal else Forest),
                contentAlignment = Alignment.Center,
            ) { AarogyamScanMark(selected) }
        } else {
            Box(
                Modifier.size(width = 43.dp, height = 34.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) Mint else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) { Icon(item.icon, item.label, tint = if (selected) Forest else Muted, modifier = Modifier.size(22.dp)) }
        }
        if (!item.primary) {
            Text(
                item.label,
                color = if (selected) Forest else Muted,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AarogyamScanMark(selected: Boolean) {
    Canvas(Modifier.size(34.dp)) {
        val stroke = 2.5.dp.toPx()
        val arm = size.width * .22f
        val inset = size.width * .06f
        val frame = if (selected) Leaf else Color.White
        val corners = Path().apply {
            moveTo(inset + arm, inset); lineTo(inset, inset); lineTo(inset, inset + arm)
            moveTo(size.width - inset - arm, inset); lineTo(size.width - inset, inset); lineTo(size.width - inset, inset + arm)
            moveTo(inset, size.height - inset - arm); lineTo(inset, size.height - inset); lineTo(inset + arm, size.height - inset)
            moveTo(size.width - inset - arm, size.height - inset); lineTo(size.width - inset, size.height - inset); lineTo(size.width - inset, size.height - inset - arm)
        }
        drawPath(corners, frame, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val pulse = Path().apply {
            moveTo(size.width * .18f, size.height * .53f)
            lineTo(size.width * .36f, size.height * .53f)
            lineTo(size.width * .44f, size.height * .36f)
            lineTo(size.width * .57f, size.height * .68f)
            lineTo(size.width * .66f, size.height * .48f)
            lineTo(size.width * .83f, size.height * .48f)
        }
        drawPath(pulse, if (selected) Color.White else Leaf, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
