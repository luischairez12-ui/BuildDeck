package com.builddeck.mobile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Build animations are driven by real Termux stages and callbacks, never guessed percentages. */
@Composable
internal fun BuildPulseCard(
    record: BuildRecord,
    stage: String?,
    onViewLog: (() -> Unit)?,
    onImportApk: (() -> Unit)?,
    onOpenVault: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val active = record.status in setOf("SUBMITTED", "RUNNING")
    val noFirstStage = record.status == "SUBMITTED" && stage.isNullOrBlank()
    var now by remember(record.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(record.id, active) {
        while (active) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(2000)
        }
    }
    val stale = active && now - record.submittedAt > (if (noFirstStage) 60_000L else 35L * 60 * 1000)
    val animating = active && !stale
    val failed = record.status == "FAILED"
    val imported = record.status == "READY"
    val compiled = record.status in setOf("SUCCESS_NEEDS_IMPORT", "SUCCESS")
    val finished = imported || compiled
    // Only animate a running build. Finished history must not burn the battery.
    val pulse = if (animating) {
        val transition = rememberInfiniteTransition(label = "build-running")
        val fraction by transition.animateFloat(
            initialValue = .48f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
            label = "activity-pulse"
        )
        fraction
    } else 1f
    val flash = remember(record.id) { Animatable(0f) }
    var precedingStatus by remember(record.id) { mutableStateOf(record.status) }
    LaunchedEffect(record.id, record.status) {
        val wasBuilding = precedingStatus in setOf("SUBMITTED", "RUNNING")
        precedingStatus = record.status
        flash.snapTo(0f)
        // Flash only on a real transition, not every time old history is opened.
        if (finished && wasBuilding) {
            repeat(3) {
                flash.animateTo(1f, tween(165))
                flash.animateTo(0f, tween(200))
            }
        }
    }
    val base = when {
        failed -> NovaRed
        finished -> NovaGreen
        stale -> Color(0xFFC28C30)
        else -> NovaBlue
    }
    val accent = when {
        animating -> lerp(base.copy(alpha = .40f), base, pulse)
        finished -> lerp(base, Color(0xFFB6F7D1), flash.value)
        else -> base
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (animating || finished || failed) 2.dp else 1.dp,
            accent.copy(alpha = if (animating) pulse else .85f)),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(23.dp)) {
                    drawCircle(accent.copy(alpha = if (animating) pulse * .26f else .2f))
                    drawCircle(accent, radius = size.minDimension * .30f)
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            record.status == "DISMISSED" -> "Request dismissed"
                            failed -> "Build encountered an error"
                            imported -> "Build complete • APK saved"
                            compiled -> "Compiled • needs APK import"
                            stale -> "Awaiting build result"
                            record.status == "SUBMITTED" -> "Connecting to Termux…"
                            else -> "Building your Android app…"
                        },
                        fontWeight = FontWeight.Bold,
                        color = if (failed) NovaRed else MaterialTheme.colorScheme.onSurface
                    )
                    NovaHint("Build " + record.id.take(8) + " • " + novaDate(record.submittedAt))
                }
                NovaStatus(
                    when {
                        record.status == "DISMISSED" -> "CLOSED"
                        failed -> "FAILED"
                        imported -> "READY"
                        compiled -> "IMPORT"
                        stale -> "CHECK"
                        noFirstStage -> "WAIT"
                        else -> "LIVE"
                    },
                    imported || (animating && !failed && !noFirstStage)
                )
            }
            val displayedStage = when {
                record.status == "DISMISSED" -> "Hidden from live monitor; saved output can still be recovered."
                failed -> record.note.ifBlank { "See the build log for details." }
                imported -> "Verified APK is available in Vault."
                compiled -> "APK compiled, but automatic collection did not complete."
                stale -> "No confirmation received. Build may still be running; check Termux or reconnect Out in Settings."
                !stage.isNullOrBlank() -> stage
                record.note.isNotBlank() && record.note != "Submitted to Termux" -> record.note
                else -> "Waiting for Termux to report its first stage"
            }
            Text(displayedStage.take(220),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) NovaRed else MaterialTheme.colorScheme.onSurfaceVariant)

            if (animating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = NovaBlue,
                    trackColor = NovaBlue.copy(alpha = .12f)
                )
            } else {
                HorizontalDivider(
                    color = accent.copy(alpha = .35f),
                    thickness = if (failed || finished) 2.dp else 1.dp
                )
            }
            if (!compact) {
                // A stage turning green means it finished, not merely started.
                val actualStage = stage ?: record.note.takeIf { record.status == "RUNNING" }
                val currentStage = when {
                    imported || compiled -> 4
                    actualStage?.contains("Package complete", true) == true -> 4
                    actualStage?.contains("APK ready", true) == true -> 4
                    actualStage?.contains("Complete", true) == true -> 4
                    actualStage?.contains("Packaging", true) == true -> 3
                    actualStage?.contains("Compiling", true) == true -> 2
                    actualStage?.contains("Extracting", true) == true -> 1
                    actualStage?.contains("Preparing", true) == true -> 0
                    else -> -1
                }
                val completedThrough = if (imported) 4 else currentStage - 1
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Prepare", "Extract", "Compile", "Package", "Vault").forEachIndexed { index, label ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                color = when {
                                    index <= completedThrough -> NovaGreen
                                    index == currentStage && failed -> NovaRed
                                    index == currentStage && active && !stale -> NovaBlue.copy(alpha = pulse)
                                    index == currentStage && (stale || compiled) -> Color(0xFFC28C30)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth().height(7.dp)
                            ) {}
                            Spacer(Modifier.height(5.dp))
                            Text(if (index <= completedThrough) "✓" else if (index == currentStage && active) "•" else "–",
                                color = if (index <= completedThrough) NovaGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = if (index <= completedThrough) NovaGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1)
                        }
                    }
                }
            }
            if (onViewLog != null || (compiled && onImportApk != null) ||
                (imported && onOpenVault != null)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onViewLog != null) TextButton(onClick = onViewLog) {
                        Text("Read build log", color = NovaBlue)
                    }
                    if (compiled && onImportApk != null) TextButton(onClick = onImportApk) {
                        Text("Import APK", color = NovaBlue)
                    }
                    if (imported && onOpenVault != null) TextButton(onClick = onOpenVault) {
                        Text("Open APK Vault", color = NovaBlue)
                    }
                }
            }
        }
    }
}
