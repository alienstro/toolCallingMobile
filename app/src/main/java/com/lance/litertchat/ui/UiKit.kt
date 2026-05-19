package com.lance.litertchat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val AppBackground = Color(0xFF292420)
val AppSurface = Color(0xFF332D28)
val AppPanel = Color(0xFF3D3631)
val AppPanelAlt = Color(0xFF4A423B)
val AppText = Color(0xFFF3EFE9)
val AppMuted = Color(0xFFC1B6A8)
val AppFaint = Color(0xFF94877A)
val AppBorder = Color(0xFF5A5048)
val AppAccent = Color(0xFFA8513D)
val AppAccentSoft = Color(0x3DA8513D)
val AppSuccess = Color(0xFF72C592)
val AppWarning = Color(0xFFD8B45C)
val AppDanger = Color(0xFFD76E5D)
val AppInfo = Color(0xFF80B8D8)

val AppBackgroundBrush = Brush.verticalGradient(
    listOf(Color(0xFF302923), AppBackground, Color(0xFF231F1C))
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
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
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AppText,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = 2.dp)
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppPanel.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun PanelTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle(title, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun StatusPill(
    text: String,
    tone: PillTone = PillTone.Neutral,
    modifier: Modifier = Modifier,
    withDot: Boolean = false
) {
    val colors = pillColors(tone)
    Row(
        modifier = modifier
            .border(1.dp, AppBorder, RoundedCornerShape(999.dp))
            .background(colors.background, RoundedCornerShape(999.dp))
            .heightIn(min = 28.dp)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (withDot) StatusDot(tone = tone)
        Text(
            text = text,
            color = colors.foreground,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

enum class PillTone {
    Neutral,
    Accent,
    Good,
    Warn,
    Danger
}

data class ChromeRuntimeStatus(
    val label: String,
    val tone: PillTone
)

enum class BannerTone {
    Info,
    Warning
}

fun chromeRuntimeStatus(state: AppState): ChromeRuntimeStatus = when {
    state.isGenerating -> ChromeRuntimeStatus("Running", PillTone.Accent)
    state.isDownloading -> ChromeRuntimeStatus("Downloading", PillTone.Accent)
    state.runtimeStatus.fallbackReason != null ||
        ((state.gpuBackendEnabled || state.npuBackendEnabled) && state.runtimeStatus.active.label == "CPU") ->
        ChromeRuntimeStatus("CPU fallback", PillTone.Warn)
    state.activeModel != null -> ChromeRuntimeStatus("Ready", PillTone.Good)
    else -> ChromeRuntimeStatus("Missing", PillTone.Warn)
}

fun bannerToneForMessage(message: String): BannerTone {
    val warningWords = listOf("unavailable", "blocked", "error", "fail", "remove", "delete", "running", "rejected")
    val lower = message.lowercase()
    return if (warningWords.any { it in lower }) BannerTone.Warning else BannerTone.Info
}

@Composable
fun WarningBanner(
    message: String,
    tone: BannerTone,
    modifier: Modifier = Modifier
) {
    val warning = tone == BannerTone.Warning
    val color = if (warning) AppWarning else AppAccent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.62f), RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        color.copy(alpha = if (warning) 0.30f else 0.26f),
                        AppPanel.copy(alpha = 0.96f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 13.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (warning) "!" else "i",
                color = AppBackground,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black
            )
        }
        Text(
            text = message,
            color = if (warning) Color(0xFFFFF4C8) else AppText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .border(1.dp, Color.Transparent)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = AppText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                color = AppFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun DataTable(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AppBorder, RoundedCornerShape(14.dp))
            .background(Color.Transparent, RoundedCornerShape(14.dp))
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (index > 0) Modifier.border(
                            width = 0.dp,
                            color = Color.Transparent
                        ) else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = row.first,
                    color = AppFaint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.55f)
                )
                Text(
                    text = row.second,
                    color = AppMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
            if (index != rows.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppBorder.copy(alpha = 0.72f))
                )
            }
        }
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
            .border(1.dp, AppBorder, RoundedCornerShape(12.dp))
            .background(AppPanelAlt, RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 9.dp)
    ) {
        Text(
            text = value,
            color = AppText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label.uppercase(),
            color = AppFaint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun CompactActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    danger: Boolean = false
) {
    val container = when {
        primary -> AppAccent
        danger -> AppDanger.copy(alpha = 0.22f)
        else -> AppPanelAlt
    }
    val content = when {
        primary -> Color(0xFFF8F2ED)
        danger -> Color(0xFFF0A092)
        else -> AppText
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = AppPanelAlt,
            disabledContentColor = AppFaint
        ),
        border = BorderStroke(1.dp, if (primary) AppAccent else AppBorder)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.46f),
        colors = SwitchDefaults.colors(
            checkedThumbColor = AppAccent,
            checkedTrackColor = AppAccentSoft,
            checkedBorderColor = AppAccent.copy(alpha = 0.56f),
            uncheckedThumbColor = AppFaint,
            uncheckedTrackColor = AppBackground,
            uncheckedBorderColor = AppBorder
        )
    )
}

@Composable
fun StatusDot(tone: PillTone, modifier: Modifier = Modifier) {
    val color = pillColors(tone).foreground
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color, RoundedCornerShape(999.dp))
    )
}

private data class PillColors(
    val foreground: Color,
    val background: Color
)

private fun pillColors(tone: PillTone): PillColors = when (tone) {
    PillTone.Neutral -> PillColors(AppMuted, AppPanelAlt)
    PillTone.Accent -> PillColors(Color(0xFFE9B7A6), AppAccentSoft)
    PillTone.Good -> PillColors(AppSuccess, AppSuccess.copy(alpha = 0.14f))
    PillTone.Warn -> PillColors(AppWarning, AppWarning.copy(alpha = 0.14f))
    PillTone.Danger -> PillColors(AppDanger, AppDanger.copy(alpha = 0.14f))
}
