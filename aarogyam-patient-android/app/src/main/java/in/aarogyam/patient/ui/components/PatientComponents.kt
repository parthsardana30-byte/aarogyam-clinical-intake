@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package `in`.aarogyam.patient.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import `in`.aarogyam.patient.ui.AppLanguage
import `in`.aarogyam.patient.ui.theme.Amber
import `in`.aarogyam.patient.ui.theme.Border
import `in`.aarogyam.patient.ui.theme.Canvas
import `in`.aarogyam.patient.ui.theme.ClinicalBlue
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.ForestDark
import `in`.aarogyam.patient.ui.theme.Leaf
import `in`.aarogyam.patient.ui.theme.Mint
import `in`.aarogyam.patient.ui.theme.Muted
import `in`.aarogyam.patient.ui.theme.SoftWhite
import `in`.aarogyam.patient.ui.theme.WarmCanvas

internal val SoftBlue = Color(0xFFEAF4F7)
internal val SoftAmber = Color(0xFFFFF4D2)
internal val SoftRose = Color(0xFFFFEFEB)

@Composable
internal fun AarogyamBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.background(
            Brush.linearGradient(
                0f to Canvas,
                .58f to Color(0xFFF2F7F3),
                1f to WarmCanvas,
                start = Offset.Zero,
                end = Offset(900f, 1500f),
            ),
        ),
    ) { content() }
}

@Composable
internal fun FlowScaffold(
    title: String,
    step: Int,
    total: Int,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Canvas,
        topBar = {
            Column(Modifier.background(SoftWhite)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Column {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                            Text("Step $step of $total", color = Muted, fontSize = 12.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                )
                LinearProgressIndicator(
                    progress = { step.toFloat() / total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Leaf,
                    trackColor = Mint,
                )
            }
        },
        content = content,
    )
}

@Composable
internal fun LanguageMenu(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { open = true },
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = .9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Language, null, tint = Forest, modifier = Modifier.size(18.dp))
                Text(selected.code, color = ForestDark, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 7.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(language.nativeLabel, color = DeepTeal, fontWeight = FontWeight.SemiBold)
                            if (language.nativeLabel != language.label) Text(language.label, color = Muted, fontSize = 11.sp)
                        }
                    },
                    leadingIcon = {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(if (language == selected) Forest else Mint), contentAlignment = Alignment.Center) {
                            Text(language.code, color = if (language == selected) Color.White else Forest, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    trailingIcon = { if (language == selected) Icon(Icons.Default.Check, "Selected", tint = Forest) },
                    onClick = { onSelected(language); open = false },
                )
            }
        }
    }
}

@Composable
internal fun ArogyamBrand(modifier: Modifier = Modifier, inverse: Boolean = false, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(if (compact) 38.dp else 44.dp)
                .clip(RoundedCornerShape(if (compact) 11.dp else 13.dp))
                .background(DeepTeal),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(if (compact) 27.dp else 31.dp)) {
                val w = size.width
                val h = size.height
                val a = Path().apply {
                    moveTo(w * .12f, h * .82f)
                    lineTo(w * .42f, h * .17f)
                    quadraticTo(w * .50f, h * .03f, w * .58f, h * .18f)
                    lineTo(w * .90f, h * .82f)
                }
                drawPath(a, Color.White, style = Stroke(width = w * .095f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val pulse = Path().apply {
                    moveTo(w * .22f, h * .61f)
                    lineTo(w * .42f, h * .61f)
                    lineTo(w * .50f, h * .43f)
                    lineTo(w * .61f, h * .73f)
                    lineTo(w * .69f, h * .56f)
                    lineTo(w * .94f, h * .56f)
                }
                drawPath(pulse, Leaf, style = Stroke(width = w * .075f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Text(
            "Aarogyam",
            color = if (inverse) Color.White else DeepTeal,
            fontSize = if (compact) 20.sp else 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
internal fun ScreenIntro(eyebrow: String, title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), color = Forest, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = DeepTeal, modifier = Modifier.padding(top = 4.dp))
            Text(subtitle, color = Muted, modifier = Modifier.padding(top = 5.dp), lineHeight = 20.sp)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun SectionTitle(title: String, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = DeepTeal, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Forest, fontWeight = FontWeight.Bold) }
    }
}

@Composable
internal fun DateTile(day: String, date: String) {
    Column(
        Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(Mint),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(day, color = Forest, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
        Text(date, color = DeepTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun InfoBanner(icon: ImageVector, title: String, body: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SoftBlue),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClinicalBlue.copy(alpha = .12f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, Color.White.copy(alpha = .78f), ClinicalBlue)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = DeepTeal)
                Text(body, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
internal fun RecordCard(icon: ImageVector, title: String, subtitle: String, body: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .92f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = .8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, Mint, Forest)
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = DeepTeal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Forest, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Forest.copy(alpha = .65f), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
internal fun EmptyCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .88f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, null, tint = Leaf, modifier = Modifier.size(40.dp))
            Text(title, fontWeight = FontWeight.Bold, color = DeepTeal, modifier = Modifier.padding(top = 12.dp))
            Text(body, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun SettingRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, Mint, Forest)
            Text(title, Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.SemiBold, color = DeepTeal)
            Text(value, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun ToggleSetting(icon: ImageVector, title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, Mint, Forest)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = DeepTeal)
                Text(body, color = Muted, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
internal fun IconBadge(icon: ImageVector, background: Color, tint: Color, size: Int = 48) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * .32f).dp)).background(background),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * .48f).dp)) }
}

@Composable
internal fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        options.forEach { option ->
            val isSelected = selected == option
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = { Text(option, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Mint,
                    selectedLabelColor = Forest,
                    labelColor = Muted,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Border,
                    selectedBorderColor = Leaf,
                ),
            )
        }
    }
}

@Composable
internal fun QuestionChoice(question: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(question, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DeepTeal)
        ChoiceRow(options, selected, onSelect)
    }
}

@Composable
internal fun PrimaryFlowButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Forest, disabledContainerColor = Border),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

internal fun tr(language: AppLanguage, key: String): String {
    val english = mapOf(
        "today" to "Today", "care" to "Care", "records" to "Records", "you" to "You",
        "home" to "Home", "files" to "Files", "scan" to "Scan", "history" to "History", "profile" to "Profile",
        "hello" to "Hello", "today_subtitle" to "Your care, organized for the day.",
        "ai_checkup" to "Start health check-in", "ai_checkup_sub" to "Share what is bothering you before your visit.",
        "daily_checkin" to "Daily check-in", "next_up" to "Next appointment", "view_care" to "View care",
        "medicine_reminder" to "Medicine reminder",
    )
    val hindi = mapOf(
        "today" to "आज", "care" to "देखभाल", "records" to "रिकॉर्ड", "you" to "आप",
        "home" to "होम", "files" to "फ़ाइलें", "scan" to "स्कैन", "history" to "इतिहास", "profile" to "प्रोफ़ाइल",
        "hello" to "नमस्ते", "today_subtitle" to "आज की आपकी देखभाल एक जगह।",
        "ai_checkup" to "स्वास्थ्य चेक-इन", "ai_checkup_sub" to "अपनी परेशानी साझा करें और विज़िट के लिए तैयार रहें।",
        "daily_checkin" to "दैनिक चेक-इन", "next_up" to "अगली अपॉइंटमेंट", "view_care" to "देखें",
        "medicine_reminder" to "दवा की याद दिलाना",
    )
    val dictionary = when (language) {
        AppLanguage.English -> english[key]
        AppLanguage.Hindi -> hindi[key]
    }
    return dictionary ?: english[key] ?: key
}

internal fun l10n(language: AppLanguage, english: String, hindi: String): String =
    if (language == AppLanguage.Hindi) hindi else english
