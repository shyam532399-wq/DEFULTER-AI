package com.example.ui.components

import android.os.Build
import android.view.Choreographer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.HudEffectTheme
import com.example.domain.HudHealthLevel
import com.example.domain.SystemHudMonitor
import com.example.domain.SystemHudSnapshot
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LocalPersonaAccent
import com.example.ui.theme.RubyDanger
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Samples real hardware Choreographer display FPS on device/emulator while staying no-op under Robolectric JVM tests.
 */
@Composable
fun RememberLiveFpsReporter(onFpsMeasured: (Int) -> Unit) {
    val latestCallback by rememberUpdatedState(onFpsMeasured)
    DisposableEffect(Unit) {
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            onDispose { }
        } else {
            val choreographer = Choreographer.getInstance()
            var frameCount = 0
            var windowStartNs = 0L
            var active = true

            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!active) return
                    if (windowStartNs == 0L) {
                        windowStartNs = frameTimeNanos
                    }
                    frameCount++
                    val elapsedNs = frameTimeNanos - windowStartNs
                    if (elapsedNs >= 1_000_000_000L) {
                        val fps = ((frameCount * 1_000_000_000L) / elapsedNs).toInt().coerceIn(15, 120)
                        latestCallback(fps)
                        frameCount = 0
                        windowStartNs = frameTimeNanos
                    }
                    choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(callback)
            onDispose {
                active = false
                choreographer.removeFrameCallback(callback)
            }
        }
    }
}

/**
 * Dynamic green -> yellow -> red progression matching `HunterisLive-1/maya-ui`.
 */
@Composable
fun hudStatusColor(percent: Int): Color = when {
    percent >= 82 -> RubyDanger
    percent >= 60 -> AmberWarning
    else -> EmeraldSuccess
}

@Composable
fun hudTempColor(tempCelsius: Double): Color = when {
    tempCelsius >= 41.0 -> RubyDanger
    tempCelsius >= 37.5 -> AmberWarning
    else -> EmeraldSuccess
}

/**
 * Holographic / Quantum animated background container with color-shifting border and L-bracket HUD corners.
 */
@Composable
fun QuantumHudContainer(
    effectTheme: HudEffectTheme,
    modifier: Modifier = Modifier,
    particlesEnabled: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val accent = LocalPersonaAccent.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "hud_bg_transition")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hud_phase"
    )
    val borderShift by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hud_border_shift"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(surfaceColor.copy(alpha = 0.94f))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.primary.copy(alpha = borderShift),
                        accent.secondary.copy(alpha = 1f - borderShift * 0.5f),
                        accent.primary.copy(alpha = borderShift * 0.7f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            when (effectTheme) {
                HudEffectTheme.QUANTUM_GRID -> drawQuantumGrid(
                    primary = accent.primary,
                    secondary = accent.secondary,
                    phase = phase
                )

                HudEffectTheme.HOLOGRAPHIC_SCAN -> drawHolographicScan(
                    primary = accent.primary,
                    secondary = accent.secondary,
                    phase = phase
                )

                HudEffectTheme.MINIMAL -> Unit
            }
            drawHudCornerAccents(color = accent.primary.copy(alpha = 0.85f))
        }

        if (particlesEnabled && effectTheme != HudEffectTheme.MINIMAL) {
            MayaParticleEngineOverlay(
                particleCount = 30,
                maxConnections = 24,
                showScanlines = effectTheme == HudEffectTheme.HOLOGRAPHIC_SCAN,
                testTagName = "cockpit_particle_engine",
                modifier = Modifier.matchParentSize()
            )
        }

        content()
    }
}

private fun DrawScope.drawQuantumGrid(primary: Color, secondary: Color, phase: Float) {
    val stepPx = 28.dp.toPx().coerceAtLeast(16f)
    val drift = phase * stepPx
    var x = (drift % stepPx)
    while (x < size.width) {
        drawLine(
            color = primary.copy(alpha = 0.06f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
        x += stepPx
    }
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = secondary.copy(alpha = 0.05f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += stepPx
    }

    // Drifting quantum particles
    for (i in 0 until 6) {
        val angle = (phase * 2.0 * PI) + i * 1.05
        val px = (size.width * (0.15f + 0.7f * ((i * 0.19f + phase) % 1f)))
        val py = (size.height * (0.5f + 0.35f * sin(angle).toFloat()))
        drawCircle(
            color = if (i % 2 == 0) primary.copy(alpha = 0.18f) else secondary.copy(alpha = 0.18f),
            radius = 3.dp.toPx(),
            center = Offset(px, py)
        )
    }
}

private fun DrawScope.drawHolographicScan(primary: Color, secondary: Color, phase: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                primary.copy(alpha = 0.14f),
                secondary.copy(alpha = 0.06f),
                Color.Transparent
            ),
            center = Offset(size.width * (0.25f + 0.5f * phase), size.height * 0.35f),
            radius = size.maxDimension * 0.65f
        )
    )
    val scanY = size.height * phase
    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                secondary.copy(alpha = 0.38f),
                primary.copy(alpha = 0.45f),
                Color.Transparent
            )
        ),
        start = Offset(0f, scanY),
        end = Offset(size.width, scanY),
        strokeWidth = 2.dp.toPx()
    )
}

private fun DrawScope.drawHudCornerAccents(color: Color) {
    val arm = 16.dp.toPx()
    val pad = 6.dp.toPx()
    val stroke = 2.2.dp.toPx()
    val w = size.width
    val h = size.height

    // Top-Left ┌
    drawLine(color, Offset(pad, pad), Offset(pad + arm, pad), stroke, StrokeCap.Round)
    drawLine(color, Offset(pad, pad), Offset(pad, pad + arm), stroke, StrokeCap.Round)
    // Top-Right ┐
    drawLine(color, Offset(w - pad - arm, pad), Offset(w - pad, pad), stroke, StrokeCap.Round)
    drawLine(color, Offset(w - pad, pad), Offset(w - pad, pad + arm), stroke, StrokeCap.Round)
    // Bottom-Left └
    drawLine(color, Offset(pad, h - pad), Offset(pad + arm, h - pad), stroke, StrokeCap.Round)
    drawLine(color, Offset(pad, h - pad - arm), Offset(pad, h - pad), stroke, StrokeCap.Round)
    // Bottom-Right ┘
    drawLine(color, Offset(w - pad - arm, h - pad), Offset(w - pad, h - pad), stroke, StrokeCap.Round)
    drawLine(color, Offset(w - pad, h - pad - arm), Offset(w - pad, h - pad), stroke, StrokeCap.Round)
}

/**
 * Centerpiece Reactive AI Orb upgraded with `ui/orb_widget.py` from `HunterisLive-1/maya-ui`:
 * - Smooth spring-like radius expansion (`amplitude` lerp)
 * - HSV Hue shift from Blue (`240°`) at idle to Cyan (`180°`) at peak voice output
 * - 3 concentric glowing HSV aura layers behind bold monospace `"MAYA"` core text.
 */
@Composable
fun MayaReactiveOrb(
    audioLevel: Float,
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    guardArmed: Boolean,
    onTapOrb: () -> Unit,
    modifier: Modifier = Modifier,
    centerLabel: String = "MAYA",
    testTagName: String = "maya_reactive_orb"
) {
    val accent = LocalPersonaAccent.current
    val orbCd = stringResource(R.string.cd_hud_orb)
    val smoothedAmp by animateFloatAsState(
        targetValue = (audioLevel.coerceIn(0f, 1f) * 2f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 110, easing = LinearEasing),
        label = "orb_smooth_amp"
    )
    val infinite = rememberInfiniteTransition(label = "maya_orb")
    val spinDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isThinking -> 1800
                    isListening || isSpeaking -> 2800
                    else -> 6500
                },
                easing = LinearEasing
            )
        ),
        label = "orb_spin"
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathe"
    )

    // `orb_widget.py` HSV Blue (240°) -> Cyan (180°) shift driven by amplitude
    val hsvHue = (240f - 60f * smoothedAmp).coerceIn(180f, 240f)
    val hsvShiftColor = Color.hsv(
        hue = hsvHue,
        saturation = (0.78f + 0.22f * smoothedAmp).coerceIn(0f, 1f),
        value = 1f
    )

    val corePrimary = when {
        guardArmed && !isListening && !isSpeaking -> EmeraldSuccess
        isThinking -> AmberWarning
        isSpeaking || smoothedAmp > 0.08f -> hsvShiftColor
        isListening -> accent.primary
        else -> hsvShiftColor
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(104.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = orbCd
                role = Role.Button
            }
            .clickable(onClick = onTapOrb)
            .testTag(testTagName)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f
            val energyBoost = 1f + (smoothedAmp * 0.34f)

            // 3 concentric HSV glow halos (`for glow_size in [8, 5, 3]` in `orb_widget.py`)
            for (layer in 3 downTo 1) {
                val layerScale = (0.55f + layer * 0.14f) * (breathe * energyBoost).coerceAtMost(1.22f)
                val alpha = ((0.12f + 0.14f * smoothedAmp) / layer).coerceIn(0.04f, 0.36f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            corePrimary.copy(alpha = alpha * 2.2f),
                            accent.secondary.copy(alpha = alpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = (maxR * layerScale).coerceAtLeast(4f)
                    ),
                    radius = (maxR * layerScale).coerceAtMost(maxR),
                    center = center
                )
            }

            // Outer dashed telemetry ring
            rotate(degrees = spinDeg, pivot = center) {
                drawCircle(
                    color = corePrimary.copy(alpha = 0.68f),
                    radius = maxR * 0.86f,
                    center = center,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                    )
                )
            }

            // Counter-rotating quantum arc ring
            rotate(degrees = -spinDeg * 1.3f, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            accent.secondary,
                            corePrimary,
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = 15f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(center.x - maxR * 0.70f, center.y - maxR * 0.70f),
                    size = Size(maxR * 1.40f, maxR * 1.40f),
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Inner reactive plasma core (`current_radius += (target - current_radius) * 0.4`)
            val coreRadius = (maxR * 0.44f * breathe * energyBoost).coerceAtMost(maxR * 0.62f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(15, 25, 55, 215),
                        corePrimary.copy(alpha = 0.88f),
                        accent.secondary.copy(alpha = 0.65f)
                    ),
                    center = center,
                    radius = coreRadius * 1.15f
                ),
                radius = coreRadius,
                center = center
            )
        }

        // Centered glowing monospace AI Name (`"MAYA"` in `orb_widget.py`)
        if (centerLabel.isNotBlank()) {
            Text(
                text = centerLabel.take(6),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (11f + 3f * smoothedAmp).sp,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

/**
 * 24-Bar Neon Frequency & Waveform Visualizer mirroring `ui/mic_visualizer.py` in `HunterisLive-1/maya-ui`.
 */
@Composable
fun NeonAudioSpectrumVisualizer(
    spectrumBands: List<Float>,
    isListening: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = LocalPersonaAccent.current
    val statusText = when {
        isListening -> "MIC SPECTRUM • LIVE"
        isSpeaking -> "VOICE SYNTH • ACTIVE"
        else -> "AUDIO ENGINE • STANDBY"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("neon_audio_visualizer")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isListening || isSpeaking) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            val peakPct = ((spectrumBands.maxOrNull() ?: 0.08f) * 100).roundToInt()
            Text(
                text = "LVL $peakPct%",
                style = MaterialTheme.typography.labelSmall,
                color = accent.secondary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            val count = spectrumBands.size.coerceAtLeast(1)
            val gap = 3.dp.toPx()
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)

            spectrumBands.forEachIndexed { idx, rawVal ->
                val level = rawVal.coerceIn(0.08f, 1f)
                val barHeight = (size.height * level).coerceAtLeast(4.dp.toPx())
                val left = idx * (barWidth + gap)
                val top = (size.height - barHeight) / 2f

                // Soft neon outer glow when active
                if (isListening || isSpeaking) {
                    drawRoundRect(
                        color = accent.primary.copy(alpha = 0.22f * level),
                        topLeft = Offset(left - 1.dp.toPx(), (top - 2.dp.toPx()).coerceAtLeast(0f)),
                        size = Size(barWidth + 2.dp.toPx(), (barHeight + 4.dp.toPx()).coerceAtMost(size.height)),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accent.secondary,
                            accent.primary,
                            EmeraldSuccess.copy(alpha = 0.85f)
                        ),
                        startY = top,
                        endY = top + barHeight
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
    }
}

/**
 * Full System Monitoring HUD Panel from `HunterisLive-1/maya-ui` (`CPU`, `RAM`, `GPU`, `DISK`, `TEMP`, `NET ↓/↑`, `THREADS`).
 */
@Composable
fun SystemMonitoringHudPanel(
    snapshot: SystemHudSnapshot,
    effectTheme: HudEffectTheme,
    floatingHudVisible: Boolean,
    onSelectTheme: (HudEffectTheme) -> Unit,
    onToggleFloatingHud: () -> Unit,
    onPinDiagnosticToBoard: () -> Unit,
    modifier: Modifier = Modifier,
    particlesEnabled: Boolean = true,
    waveVisualizerMode: Boolean = true,
    onToggleParticles: () -> Unit = {},
    onToggleVisualizerMode: () -> Unit = {},
    onOpenFullHud: () -> Unit = {}
) {
    val accent = LocalPersonaAccent.current
    val healthColor = when (snapshot.healthLevel) {
        HudHealthLevel.COOL -> EmeraldSuccess
        HudHealthLevel.WARM -> AmberWarning
        HudHealthLevel.CRITICAL -> RubyDanger
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("system_hud_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.hud_title_quantum),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = accent.primary
            )
            Surface(
                color = healthColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${snapshot.healthLevel.label} • ${snapshot.uiFps} FPS • ${snapshot.appThreadCount} THR",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = healthColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Row 1: CPU & RAM color-shifting meters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudMetricGauge(
                label = "CPU (${snapshot.cpuCoreCount}C)",
                valueText = "${snapshot.cpuLoadPercent}% • ${snapshot.cpuPeakMhz}MHz",
                progress = snapshot.cpuLoadPercent / 100f,
                barColor = hudStatusColor(snapshot.cpuLoadPercent),
                modifier = Modifier.weight(1f)
            )
            HudMetricGauge(
                label = "RAM",
                valueText = String.format(Locale.US, "%.1f/%.1fG (%d%%)", snapshot.ramUsedGb, snapshot.ramTotalGb, snapshot.ramUsedPercent),
                progress = snapshot.ramUsedPercent / 100f,
                barColor = hudStatusColor(snapshot.ramUsedPercent),
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: GPU & DISK (`workers/system_worker.py` + `ui/left_panel.py`)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudMetricGauge(
                label = "GPU",
                valueText = "${snapshot.gpuLoadPercent}% • HWUI",
                progress = snapshot.gpuLoadPercent / 100f,
                barColor = hudStatusColor(snapshot.gpuLoadPercent),
                modifier = Modifier
                    .weight(1f)
                    .testTag("hud_gauge_gpu")
            )
            HudMetricGauge(
                label = "DISK",
                valueText = String.format(Locale.US, "%d%% • %.0fG free", snapshot.storageUsedPercent, snapshot.storageFreeGb),
                progress = snapshot.storageUsedPercent / 100f,
                barColor = hudStatusColor(snapshot.storageUsedPercent),
                modifier = Modifier
                    .weight(1f)
                    .testTag("hud_gauge_disk")
            )
        }

        // Row 3: Thermal (°C) & Live Network Throughput (↓ / ↑)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val tempNorm = ((snapshot.temperatureCelsius - 20.0) / 30.0).toFloat().coerceIn(0.08f, 1f)
            HudMetricGauge(
                label = "TEMP",
                valueText = String.format(Locale.US, "%.1f°C • %s", snapshot.temperatureCelsius, snapshot.thermalStatusLabel),
                progress = tempNorm,
                barColor = hudTempColor(snapshot.temperatureCelsius),
                modifier = Modifier.weight(1f)
            )
            val netCombinedKbps = snapshot.netDownloadKbps + snapshot.netUploadKbps
            val netNorm = (netCombinedKbps / 2048.0).toFloat().coerceIn(0.08f, 1f)
            HudMetricGauge(
                label = "NET LIVE",
                valueText = "↓${SystemHudMonitor.formatNetRate(snapshot.netDownloadKbps)} ↑${SystemHudMonitor.formatNetRate(snapshot.netUploadKbps)}",
                progress = netNorm,
                barColor = if (netCombinedKbps > 1500.0) AmberWarning else EmeraldSuccess,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 4: Full Maya HUD + Wave/Bars Visualizer + AI Particles + Theme Switcher + Float HUD + Pin Report
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = onOpenFullHud,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Fullscreen,
                        contentDescription = stringResource(R.string.cd_open_full_maya_hud),
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text(stringResource(R.string.hud_full_mode_chip), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("open_full_maya_hud_button")
            )

            FilterChip(
                selected = waveVisualizerMode,
                onClick = onToggleVisualizerMode,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = stringResource(R.string.cd_toggle_visualizer_mode),
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        if (waveVisualizerMode) stringResource(R.string.hud_wave_mode_label)
                        else stringResource(R.string.hud_bars_mode_label),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("toggle_visualizer_mode_button")
            )

            FilterChip(
                selected = particlesEnabled,
                onClick = onToggleParticles,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = stringResource(R.string.cd_toggle_particles),
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text(stringResource(R.string.hud_particles_label), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("toggle_particles_button")
            )

            HudEffectTheme.entries.forEach { mode ->
                FilterChip(
                    selected = effectTheme == mode,
                    onClick = { onSelectTheme(mode) },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("hud_theme_${mode.id}")
                )
            }

            FilterChip(
                selected = floatingHudVisible,
                onClick = onToggleFloatingHud,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.DashboardCustomize,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text(if (floatingHudVisible) "Float HUD ON" else "Float HUD", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("toggle_floating_hud_button")
            )

            AssistChip(
                onClick = onPinDiagnosticToBoard,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Pin HUD Report", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("pin_hud_report_button")
            )
        }
    }
}

@Composable
private fun HudMetricGauge(
    label: String,
    valueText: String,
    progress: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            ) {
                val r = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                drawRoundRect(
                    color = barColor.copy(alpha = 0.18f),
                    size = size,
                    cornerRadius = r
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(barColor.copy(alpha = 0.75f), barColor)
                    ),
                    size = Size((size.width * progress.coerceIn(0.05f, 1f)), size.height),
                    cornerRadius = r
                )
            }
        }
    }
}

/**
 * Draggable Floating Mini-HUD Overlay (`HunterisLive-1/maya-ui` draggable HUD window + clean `×` close).
 */
@Composable
fun DraggableFloatingMiniHud(
    snapshot: SystemHudSnapshot,
    audioLevel: Float,
    isListening: Boolean,
    isSpeaking: Boolean,
    initialOffsetX: Float,
    initialOffsetY: Float,
    onOffsetCommitted: (Float, Float) -> Unit,
    onTriggerMic: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalPersonaAccent.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { (maxWidth - 290.dp).coerceAtLeast(0.dp).toPx() }
        val maxHeightPx = with(density) { (maxHeight - 88.dp).coerceAtLeast(0.dp).toPx() }

        var offsetX by remember { mutableFloatStateOf(initialOffsetX.coerceIn(8f, maxWidthPx.coerceAtLeast(8f))) }
        var offsetY by remember { mutableFloatStateOf(initialOffsetY.coerceIn(8f, maxHeightPx.coerceAtLeast(8f))) }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(listOf(accent.primary, accent.secondary)),
                    shape = RoundedCornerShape(22.dp)
                )
                .pointerInput(maxWidthPx, maxHeightPx) {
                    detectDragGestures(
                        onDragEnd = { onOffsetCommitted(offsetX, offsetY) },
                        onDragCancel = { onOffsetCommitted(offsetX, offsetY) }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxWidthPx.coerceAtLeast(0f))
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxHeightPx.coerceAtLeast(0f))
                    }
                }
                .testTag("floating_mini_hud")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenWith,
                    contentDescription = "Drag Maya Mini HUD",
                    tint = accent.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Mini reactive core dot
                Canvas(modifier = Modifier.size(20.dp)) {
                    val scale = 0.65f + 0.35f * audioLevel.coerceIn(0f, 1f)
                    drawCircle(
                        color = if (isListening) RubyDanger else accent.primary.copy(alpha = 0.3f),
                        radius = size.minDimension / 2f
                    )
                    drawCircle(
                        color = if (isSpeaking) accent.secondary else accent.primary,
                        radius = (size.minDimension / 2f) * scale
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "CPU ${snapshot.cpuLoadPercent}% • ${String.format(Locale.US, "%.1f°C", snapshot.temperatureCelsius)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "↓${SystemHudMonitor.formatNetRate(snapshot.netDownloadKbps)} ↑${SystemHudMonitor.formatNetRate(snapshot.netUploadKbps)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSuccess
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onTriggerMic,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("floating_hud_mic_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.cd_mic_button),
                        tint = if (isListening) RubyDanger else accent.primary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("floating_hud_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_close_floating_hud),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
