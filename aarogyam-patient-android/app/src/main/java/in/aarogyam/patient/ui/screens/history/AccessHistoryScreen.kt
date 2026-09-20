package `in`.aarogyam.patient.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.aarogyam.patient.data.Dashboard
import `in`.aarogyam.patient.ui.AppLanguage
import `in`.aarogyam.patient.ui.components.AarogyamBackdrop
import `in`.aarogyam.patient.ui.components.ScreenIntro
import `in`.aarogyam.patient.ui.components.l10n
import `in`.aarogyam.patient.ui.theme.Border
import `in`.aarogyam.patient.ui.theme.DeepTeal
import `in`.aarogyam.patient.ui.theme.Forest
import `in`.aarogyam.patient.ui.theme.Leaf
import `in`.aarogyam.patient.ui.theme.Mint
import `in`.aarogyam.patient.ui.theme.Muted

private data class AccessEvent(
    val doctor: String,
    val role: String,
    val action: String,
    val date: String,
    val time: String,
    val facility: String,
    val icon: ImageVector,
)

@Composable
internal fun AccessHistoryScreen(dashboard: Dashboard?, language: AppLanguage, modifier: Modifier = Modifier) {
    val events = dashboard?.accessHistory.orEmpty().map { entry ->
        AccessEvent(
            doctor = entry.doctorName,
            role = entry.specialty,
            action = l10n(language, "Viewed visit record and clinical summary", "विज़िट रिकॉर्ड और क्लिनिकल सारांश देखा"),
            date = entry.accessedAt.take(10).ifBlank { l10n(language, "Recent", "हाल का") },
            time = entry.accessedAt.drop(11).take(5).ifBlank { "—" },
            facility = listOf(entry.hospitalName, entry.hospitalLocation).filter { it.isNotBlank() }.joinToString(" · "),
            icon = Icons.Default.Visibility,
        )
    }

    AarogyamBackdrop(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                ScreenIntro(
                    eyebrow = l10n(language, "Transparent by design", "पूरी पारदर्शिता"),
                    title = l10n(language, "Access history", "एक्सेस इतिहास"),
                    subtitle = l10n(language, "See who accessed your records and what they did.", "देखें कि आपके रिकॉर्ड किसने और कब देखे।"),
                )
            }
            item { AccessHistoryHeader(events.size, language) }
            if (events.isEmpty()) {
                item {
                    Text(
                        l10n(language, "No one has accessed your records yet.", "अभी तक किसी ने आपके रिकॉर्ड नहीं देखे हैं।"),
                        color = Muted,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
            }
            items(events) { event -> AccessEventCard(event, language) }
        }
    }
}

@Composable
private fun AccessHistoryHeader(eventCount: Int, language: AppLanguage) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Shield, null, tint = Forest, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(l10n(language, "Recent access", "हाल का एक्सेस"), color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(l10n(language, "$eventCount recorded activities", "$eventCount दर्ज गतिविधियां"), color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AccessEventCard(event: AccessEvent, language: AppLanguage) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
                Icon(event.icon, null, tint = Forest, modifier = Modifier.size(21.dp))
            }
            Box(Modifier.padding(top = 5.dp).size(width = 2.dp, height = 92.dp).background(Border))
        }
        Card(
            modifier = Modifier.weight(1f).padding(start = 11.dp),
            shape = RoundedCornerShape(21.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .95f)),
            border = BorderStroke(1.dp, Border),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(event.doctor, color = DeepTeal, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(event.role, color = Forest, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(l10n(language, "VERIFIED", "सत्यापित"), color = Forest, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .7.sp)
                }
                Text(event.action, color = DeepTeal, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 11.dp))
                Text("${event.date} · ${event.time}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                Text(event.facility, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
