package com.lance.litertchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val AppBackground = Color(0xFFF8FAFC)
val AppSurface = Color.White
val AppText = Color(0xFF202634)
val AppMuted = Color(0xFF767F8E)
val AppBorder = Color(0xFFE7EAF0)
val AppAccent = Color(0xFF486DFF)
val AppAccentSoft = Color(0xFFEFF3FF)
val AppSuccess = Color(0xFF20A466)
val AppWarning = Color(0xFFB7791F)
val AppDanger = Color(0xFFD14343)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = AppAccent,
            background = AppBackground,
            surface = AppSurface,
            onSurface = AppText,
            onSurfaceVariant = AppMuted,
            outline = AppBorder,
            error = AppDanger
        ),
        content = content
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        color = AppMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp)
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    tone: PillTone = PillTone.Neutral,
    modifier: Modifier = Modifier
) {
    val colors = when (tone) {
        PillTone.Neutral -> AppMuted to AppSurface
        PillTone.Accent -> AppAccent to AppAccentSoft
        PillTone.Good -> AppSuccess to Color(0xFFEAF8F1)
        PillTone.Warn -> AppWarning to Color(0xFFFFF7E8)
        PillTone.Danger -> AppDanger to Color(0xFFFDECEC)
    }
    Text(
        text = text,
        modifier = modifier
            .border(1.dp, AppBorder, RoundedCornerShape(999.dp))
            .background(colors.second, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = colors.first,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

enum class PillTone {
    Neutral,
    Accent,
    Good,
    Warn,
    Danger
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.55f),
            color = AppMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = AppText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun MetricBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .border(1.dp, AppBorder, RoundedCornerShape(11.dp))
            .background(AppBackground, RoundedCornerShape(11.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp)
    ) {
        Text(
            text = value,
            color = AppText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label.uppercase(),
            color = AppMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CompactActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = if (primary) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = AppAccent,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE3E5EB),
                disabledContentColor = AppMuted
            )
        } else {
            ButtonDefaults.outlinedButtonColors(
                containerColor = AppSurface,
                contentColor = AppText,
                disabledContentColor = AppMuted
            )
        },
        border = androidx.compose.foundation.BorderStroke(1.dp, if (primary) AppAccent else AppBorder)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusDot(tone: PillTone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        PillTone.Good -> AppSuccess
        PillTone.Warn -> AppWarning
        PillTone.Danger -> AppDanger
        PillTone.Accent -> AppAccent
        PillTone.Neutral -> AppMuted
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(color, RoundedCornerShape(999.dp))
                .padding(3.5.dp)
        )
    }
}
