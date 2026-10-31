package com.example.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.MayaPersona
import com.example.domain.ApiKeyConnectionStatus
import com.example.domain.SystemHudSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private data class AiHudParticleSeed(
    val xNorm: Float,
    val yNorm: Float,
    val vx: Float,
    val vy: Float,
    val radiusDp: Float,
    val lifeOffset: Float,
    val color: Color
)

private val MAYA_UI_PARTICLE_PALETTE = listOf(
    Color(255, 100, 200), // Pink
    Color(100, 200, 255), // Cyan
    Color(200, 100, 255), // Purple
    Color(255, 255, 255), // White (AI data)
    Color(100, 255, 200), // Mint
    Color(0, 255, 255)    // Neon Aqua
)

private val MAYA_STATUS_ROTATION = listOf(
    "● MAYA ONLINE",
    "● SYSTEM READY",
    "● MONITORING ACTIVE",
    "● AI OPERATIONAL"
)

/**
 * Port of `effects/particle_engine.py` from `HunterisLive-1/maya-ui`:
 * - Drifting glowing AI data particles in 6 neon hues with radial halos
 * - AI Network constellation connection lines (< 150dp distance, up to maxConnections)
 * - Futuristic 4dp horizontal cyan scanlines
 */
@Composable
fun MayaParticleEngineOverlay(
    particleCount: Int = 90,
    maxConnections: Int = 85,
    showScanlines: Boolean = true,
    testTagName: String = "maya_particle_engine",
    modifier: Modifier = Modifier
) {
    val seeds = remember(particleCount) {
        val rng = Random(42019)
        List(particleCount.coerceIn(12, 150)) { idx ->
            val angle = rng.nextFloat() * (2f * PI.toFloat())
            val speed = 0.25f + rng.nextFloat() * 0.75f
            AiHudParticleSeed(
                xNorm = rng.nextFloat(),
                yNorm = rng.nextFloat(),
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                radiusDp = 1.2f + rng.nextFloat() * 1.8f,
                lifeOffset = rng.nextFloat(),
                color = MAYA_UI_PARTICLE_PALETTE[idx % MAYA_UI_PARTICLE_PALETTE.size]
            )
        }
    }

    val infinite = rememberInfiniteTransition(label = "maya_particle_engine")
    val driftTick by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 48_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_drift_tick"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag(testTagName)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 2f || h <= 2f) return@Canvas

        val connectionLimitPx = 135.dp.toPx()
        val scanStepPx = 4.dp.toPx().coerceAtLeast(3f)

        // Compute wrapped positions for current frame
        val xs = FloatArray(seeds.size)
        val ys = FloatArray(seeds.size)
        val alphas = FloatArray(seeds.size)

        for (i in seeds.indices) {
            val s = seeds[i]
            val rawX = (s.xNorm * w + s.vx * driftTick * 1.6f) % w
            val rawY = (s.yNorm * h + s.vy * driftTick * 1.6f) % h
            xs[i] = if (rawX < 0f) rawX + w else rawX
            ys[i] = if (rawY < 0f) rawY + h else rawY
            val lifeWave = 0.45f + 0.55f * ((sin((driftTick * 0.06f + s.lifeOffset * 6.28f).toDouble()).toFloat() + 1f) * 0.5f)
            alphas[i] = (lifeWave * 0.78f).coerceIn(0.18f, 0.82f)
        }

        // 1. AI Network constellation lines
        var drawnConnections = 0
        for (i in seeds.indices) {
            if (drawnConnections >= maxConnections) break
            val x1 = xs[i]
            val y1 = ys[i]
            for (j in i + 1 until seeds.size) {
                if (drawnConnections >= maxConnections) break
                val dx = x1 - xs[j]
                val dy = y1 - ys[j]
                val distSq = dx * dx + dy * dy
                if (distSq < connectionLimitPx * connectionLimitPx) {
                    val dist = sqrt(distSq)
                    val lineAlpha = ((1f - dist / connectionLimitPx) * 0.18f).coerceIn(0.02f, 0.22f)
                    drawLine(
                        color = Color(0, 150, 255).copy(alpha = lineAlpha),
                        start = Offset(x1, y1),
                        end = Offset(xs[j], ys[j]),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawnConnections++
                }
            }
        }

        // 2. Futuristic horizontal scanlines (`QColor(0, 200, 255, 10)`)
        if (showScanlines) {
            var yLine = 0f
            while (yLine < h) {
                drawLine(
                    color = Color(0, 200, 255).copy(alpha = 0.042f),
                    start = Offset(0f, yLine),
                    end = Offset(w, yLine),
                    strokeWidth = 1f
                )
                yLine += scanStepPx
            }
        }

        // 3. Zero-allocation concentric radial glowing AI particles
        for (i in seeds.indices) {
            val s = seeds[i]
            val center = Offset(xs[i], ys[i])
            val coreRadius = s.radiusDp.dp.toPx().coerceAtLeast(1.5f)
            val glowRadius = (s.radiusDp * 2.3f).dp.toPx().coerceAtLeast(3f)
            drawCircle(
                color = s.color.copy(alpha = (alphas[i] * 0.26f).coerceIn(0.04f, 0.35f)),
                radius = glowRadius,
                center = center
            )
            drawCircle(
                color = s.color.copy(alpha = alphas[i]),
                radius = coreRadius,
                center = center
            )
        }
    }
}

/**
 * Port of `effects/signature_widget.py` from `HunterisLive-1/maya-ui`:
 * Subtle dark translucent badge with bold monospace `HunterIsLive` text and underline.
 */
@Composable
fun MayaSignatureWidget(
    modifier: Modifier = Modifier,
    signatureText: String = stringResource(R.string.hud_signature_text)
) {
    Surface(
        color = Color(20, 20, 40, 180),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.testTag("maya_signature_widget")
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = signatureText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(200, 200, 200)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(1.dp)
                    .background(Color(100, 100, 100))
            )
        }
    }
}

/**
 * Port of `ui/mic_visualizer.py` from `HunterisLive-1/maya-ui`:
 * 3-Layer flowing quadratic-bezier wave visualizer (`bg_path`, `mid_path`, `fg_path`)
 * with idle alive baseline jitter, edge tapering, cyan loudness brightening, and glowing top crest stroke.
 */
@Composable
fun MayaWaveMicVisualizer(
    micLevel: Float,
    isListening: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    waveHeightDp: Int = 64,
    showHeaderLabel: Boolean = true,
    testTagName: String = "maya_wave_mic_visualizer"
) {
    val smoothedLevel by animateFloatAsState(
        targetValue = micLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "wave_mic_smooth"
    )
    val infinite = rememberInfiniteTransition(label = "maya_wave_mic")
    val basePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (20.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val statusText = when {
        isListening -> "MIC WAVEFORM • LIVE"
        isSpeaking -> "VOICE OUTPUT • ACTIVE"
        else -> "MIC VISUALIZER • ALIVE"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagName)
    ) {
        if (showHeaderLabel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (isListening || isSpeaking) Color(0, 225, 255) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                val pct = (smoothedLevel * 100f).roundToInt()
                Text(
                    text = "MIC $pct%",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(136, 204, 255)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(waveHeightDp.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(6, 10, 24, 150))
        ) {
            val w = size.width
            val h = size.height
            if (w <= 4f || h <= 4f) return@Canvas

            val timePhase = basePhase + smoothedLevel * 1.4f
            // Idle baseline jitter from `mic_visualizer.py` so it always feels alive
            val effectiveLevel = if (smoothedLevel < 0.02f) {
                (0.05f + sin(timePhase * 0.5f) * 0.02f).coerceAtLeast(0.02f)
            } else {
                smoothedLevel
            }

            // Layer 1: Background dark blue, tall & slow
            val (bgFill, _) = buildFluidWavePaths(
                width = w,
                height = h,
                yOffset = h - 8.dp.toPx(),
                amplitudeMultiplier = 0.6f,
                phaseOffset = 0f,
                frequency = 2f,
                timePhase = timePhase,
                level = effectiveLevel
            )
            drawPath(
                path = bgFill,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(10, 50, 150, 120),
                        Color(0, 10, 50, 30)
                    ),
                    startY = 0f,
                    endY = h
                )
            )

            // Layer 2: Mid cyan, offset and wavy
            val (midFill, _) = buildFluidWavePaths(
                width = w,
                height = h,
                yOffset = h - 4.dp.toPx(),
                amplitudeMultiplier = 0.8f,
                phaseOffset = 1.5f,
                frequency = 3f,
                timePhase = timePhase,
                level = effectiveLevel
            )
            drawPath(
                path = midFill,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0, 150, 220, 155),
                        Color(0, 50, 100, 36)
                    ),
                    startY = 0f,
                    endY = h
                )
            )

            // Layer 3: Foreground bright cyan/white core + glowing top crest border
            val (fgFill, fgCrest) = buildFluidWavePaths(
                width = w,
                height = h,
                yOffset = h - 2.dp.toPx(),
                amplitudeMultiplier = 1.2f,
                phaseOffset = 3.14f,
                frequency = 2.5f,
                timePhase = timePhase,
                level = effectiveLevel
            )
            val loudness = min(effectiveLevel * 2f, 1f)
            val topRed = (50 + (200f * loudness).roundToInt()).coerceIn(0, 255)
            val crestRed = (150 + (100f * loudness).roundToInt()).coerceIn(0, 255)

            drawPath(
                path = fgFill,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(topRed, 255, 255, 220),
                        Color(0, 200, 255, 180),
                        Color(0, 100, 150, 50)
                    ),
                    startY = 0f,
                    endY = h
                )
            )
            drawPath(
                path = fgCrest,
                color = Color(crestRed, 255, 255, 255),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.buildFluidWavePaths(
    width: Float,
    height: Float,
    yOffset: Float,
    amplitudeMultiplier: Float,
    phaseOffset: Float,
    frequency: Float,
    timePhase: Float,
    level: Float,
    pointsCount: Int = 56
): Pair<Path, Path> {
    val fillPath = Path()
    val crestPath = Path()
    val step = width / (pointsCount - 1).coerceAtLeast(1)
    val pts = ArrayList<Offset>(pointsCount)

    for (i in 0 until pointsCount) {
        val x = i * step
        val norm = i.toFloat() / pointsCount.toFloat()
        val wave1 = sin(norm * PI.toFloat() * frequency + timePhase + phaseOffset)
        val wave2 = cos(norm * PI.toFloat() * frequency * 1.5f - timePhase * 1.2f + phaseOffset)
        val edgeTaper = sin((i.toFloat() / (pointsCount - 1).toFloat()) * PI.toFloat())

        val baseWobble = (wave1 + wave2) * 3.5.dp.toPx() * edgeTaper * amplitudeMultiplier
        val audioWobble = (wave1 * 1.5f + wave2 * 0.5f) * level * height * 0.56f * edgeTaper * amplitudeMultiplier
        val y = (yOffset - baseWobble - audioWobble).coerceIn(2f, height)
        pts.add(Offset(x, y))
    }

    fillPath.moveTo(0f, height)
    fillPath.lineTo(pts[0].x, pts[0].y)
    crestPath.moveTo(pts[0].x, pts[0].y)

    for (i in 0 until pts.size - 1) {
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val ctrlX = (p1.x + p2.x) * 0.5f
        val ctrlY = p1.y
        fillPath.quadraticTo(ctrlX, ctrlY, p2.x, p2.y)
        crestPath.quadraticTo(ctrlX, ctrlY, p2.x, p2.y)
    }

    fillPath.lineTo(width, height)
    fillPath.close()
    return fillPath to crestPath
}

/**
 * Port of `ui/left_panel.py` (`LeftPanel` & `SystemMetric`) from `HunterisLive-1/maya-ui`:
 * Displays `SYSTEM METRICS` (`CPU %`, `RAM %`, `TEMP °C`, `GPU %`, `DISK %`) with smooth animated
 * values, `<50` Green / `<80` Yellow / `>=80` Red color coding, and traveling-glow progress bars.
 */
@Composable
fun MayaLeftSystemPanel(
    snapshot: SystemHudSnapshot,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "left_panel_glow")
    val glowPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "metric_glow_phase"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(10, 10, 30, 200),
                        Color(20, 20, 50, 220),
                        Color(10, 10, 30, 200)
                    )
                )
            )
            .border(1.5.dp, Color(0, 150, 255, 120), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("maya_left_system_panel")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0, 50, 100, 65))
                .border(1.dp, Color(0xFF00C8FF), RoundedCornerShape(6.dp))
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.hud_left_panel_title),
                color = Color(0xFF00C8FF),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        MayaSystemMetricItem(
            name = "CPU",
            rawValue = snapshot.cpuLoadPercent.toFloat(),
            unit = "%",
            glowPhase = glowPhase,
            tag = "left_metric_cpu"
        )
        MayaSystemMetricItem(
            name = "RAM",
            rawValue = snapshot.ramUsedPercent.toFloat(),
            unit = "%",
            glowPhase = glowPhase + 0.8f,
            tag = "left_metric_ram"
        )
        MayaSystemMetricItem(
            name = "TEMP",
            rawValue = snapshot.temperatureCelsius.toFloat(),
            unit = "°C",
            glowPhase = glowPhase + 1.6f,
            tag = "left_metric_temp"
        )
        MayaSystemMetricItem(
            name = "GPU",
            rawValue = snapshot.gpuLoadPercent.toFloat(),
            unit = "%",
            glowPhase = glowPhase + 2.4f,
            tag = "left_metric_gpu"
        )
        MayaSystemMetricItem(
            name = "DISK",
            rawValue = snapshot.storageUsedPercent.toFloat(),
            unit = "%",
            glowPhase = glowPhase + 3.2f,
            tag = "left_metric_disk"
        )
    }
}

@Composable
private fun MayaSystemMetricItem(
    name: String,
    rawValue: Float,
    unit: String,
    glowPhase: Float,
    tag: String
) {
    val animatedVal by animateFloatAsState(
        targetValue = rawValue.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "metric_$name"
    )
    val valueColor = when {
        animatedVal < 50f -> Color(0, 255, 100)
        animatedVal < 80f -> Color(255, 200, 0)
        else -> Color(255, 50, 50)
    }
    val glowPos = ((sin(glowPhase.toDouble()).toFloat() + 1f) * 0.5f).coerceIn(0.05f, 0.95f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = name,
                color = Color(150, 200, 255),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = "${animatedVal.roundToInt()}$unit",
                color = valueColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
        ) {
            val r = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            drawRoundRect(
                color = Color(20, 20, 40),
                size = size,
                cornerRadius = r
            )
            val progW = (size.width * (animatedVal / 100f).coerceIn(0.03f, 1f))
            val darker = valueColor.copy(alpha = 0.58f)
            val lighter = Color(
                red = (valueColor.red + 0.25f).coerceAtMost(1f),
                green = (valueColor.green + 0.25f).coerceAtMost(1f),
                blue = (valueColor.blue + 0.25f).coerceAtMost(1f),
                alpha = 1f
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0.0f to darker,
                    glowPos to lighter,
                    1.0f to valueColor,
                    startX = 0f,
                    endX = progW.coerceAtLeast(4f)
                ),
                size = Size(progW, size.height),
                cornerRadius = r
            )
            drawRoundRect(
                color = valueColor.copy(alpha = 0.65f),
                size = size,
                cornerRadius = r,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Port of `ui/right_panel.py` (`RightPanel`) from `HunterisLive-1/maya-ui`:
 * Displays `AI STATUS`, live `hh:mm:ss` clock, formatted date, 3s auto-cycling status indicator,
 * `PERFORMANCE`, dashed `MODEL` core box, and live `APP THREADS` count.
 */
@Composable
fun MayaRightStatusPanel(
    snapshot: SystemHudSnapshot,
    apiKeyStatus: ApiKeyConnectionStatus,
    isListening: Boolean,
    isSpeaking: Boolean,
    permanentBrainEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "right_panel_cycle")
    // 12-second cycle -> 4 slices of 3 seconds each (matching `status_timer.start(3000)`)
    val cycleFloat by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "status_rotation"
    )
    // Observe cycleFloat so clock string refreshes smoothly during live execution without blocking Robolectric waitForIdle
    val nowDate = remember((cycleFloat * 4f).toInt()) { Date() }
    val clockStr = remember(nowDate) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(nowDate)
    }
    val dateStr = remember(nowDate) {
        SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(nowDate)
    }

    val statusIdx = cycleFloat.toInt().coerceIn(0, MAYA_STATUS_ROTATION.lastIndex)
    val liveStatusLabel = when {
        isSpeaking -> "● VOICE SYNTH ACTIVE"
        isListening -> "● SILENT MIC LIVE"
        permanentBrainEnabled -> "● BRAIN LOOP ONLINE"
        else -> MAYA_STATUS_ROTATION[statusIdx]
    }

    val modelLabel = when (apiKeyStatus) {
        is ApiKeyConnectionStatus.Connected -> "MODEL: ${apiKeyStatus.activeModel.uppercase(Locale.ROOT)}"
        else -> "MODEL: NEURAL ENGINE v4.2"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(10, 10, 30, 200),
                        Color(20, 20, 50, 220),
                        Color(10, 10, 30, 200)
                    )
                )
            )
            .border(1.5.dp, Color(0, 150, 255, 120), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("maya_right_status_panel")
    ) {
        // Title: AI STATUS
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0, 100, 50, 60))
                .border(1.dp, Color(0xFF00FF88), RoundedCornerShape(6.dp))
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.hud_right_panel_title),
                color = Color(0xFF00FF88),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // Futuristic Digital Clock
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0, 50, 100, 75))
                .border(2.dp, Color(0xFF00FFFF), RoundedCornerShape(8.dp))
                .padding(vertical = 10.dp)
                .testTag("right_panel_clock")
        ) {
            Text(
                text = clockStr,
                color = Color(0xFF00FFFF),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        // Date Display
        Text(
            text = dateStr,
            color = Color(0xFF88CCFF),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("right_panel_date")
        )

        // Cycling Online Status Indicator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0, 50, 0, 95))
                .border(1.dp, Color(0xFF00FF00), RoundedCornerShape(6.dp))
                .padding(vertical = 8.dp, horizontal = 6.dp)
                .testTag("right_panel_status")
        ) {
            Text(
                text = liveStatusLabel,
                color = Color(0xFF00FF00),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        // Performance Indicator
        Text(
            text = "PERFORMANCE: ${snapshot.healthLevel.label}",
            color = Color(0xFFFFAA00),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("right_panel_performance")
        )

        // AI Core Section (dashed border `#0088ff`)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0, 30, 60, 85))
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFF0088FF),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    )
                }
                .padding(vertical = 10.dp, horizontal = 8.dp)
                .testTag("right_panel_ai_core")
        ) {
            Text(
                text = modelLabel,
                color = Color(0xFF0088FF),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        // Process Thread Count (`APP THREADS: N`)
        Text(
            text = "APP THREADS: ${snapshot.appThreadCount}",
            color = Color(0xFF88FFCC),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("right_panel_threads")
        )
    }
}

/**
 * Port of `ui/main_window.py` (`MainWindow`) from `HunterisLive-1/maya-ui`:
 * Full-screen frameless-style Maya AI HUD featuring:
 * - Deep space background `rgb(5, 5, 10)` + animated sweeping cyan/blue linear gradient + dark corner vignette
 * - 3-layer animated glowing border (pulsing outer cyan, HSV hue-shifting middle, crisp inner) + pulsing L-corners
 * - `ParticleEngine` constellation & scanlines overlay + bottom-right `HunterIsLive` `SignatureWidget`
 * - Canonical 3-column layout (`LeftPanel` | `OrbWidget` + `MicVisualizer` | `RightPanel`) on wide/landscape displays
 *   and clean responsive vertical HUD on compact portrait phones
 * - Android `BackHandler` + `×` button for instant clean exit (counterpart to `ESC` / `Ctrl+Q`).
 */
@Composable
fun MayaFullAiHudOverlay(
    persona: MayaPersona,
    snapshot: SystemHudSnapshot,
    apiKeyStatus: ApiKeyConnectionStatus,
    micLevel: Float,
    speakerAmplitude: Float,
    audioLevel: Float,
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    guardArmed: Boolean,
    permanentBrainEnabled: Boolean,
    particlesEnabled: Boolean,
    floatingHudVisible: Boolean,
    onTapOrbOrMic: () -> Unit,
    onToggleParticles: () -> Unit,
    onTogglePermanentBrain: () -> Unit,
    onSwitchToFloatingMiniHud: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val infinite = rememberInfiniteTransition(label = "maya_main_window_hud")
    val animTime by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hud_main_time"
    )
    val hueShift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hud_hue_shift"
    )

    val orbEnergy = if (isSpeaking && speakerAmplitude > 0f) speakerAmplitude else audioLevel

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(5, 5, 10))
            .testTag("maya_full_hud_overlay")
    ) {
        // 1. Animated Space Gradient + Vignette + 3-Layer Glow Border (`MainWindow.paintEvent`)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 4f || h <= 4f) return@Canvas

            val gradX = w * 0.5f + sin(animTime) * (w * 0.3f)
            val gradY = h * 0.5f + cos(animTime * 0.8f) * (h * 0.3f)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0, 100, 200, 38),
                        Color(0, 150, 255, 68),
                        Color(0, 80, 180, 34)
                    ),
                    start = Offset(gradX - w * 0.4f, gradY - h * 0.4f),
                    end = Offset(gradX + w * 0.4f, gradY + h * 0.4f)
                )
            )

            // Dark corner radial vignette
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0, 0, 0, 175)),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = (maxOf(w, h) * 0.72f).coerceAtLeast(10f)
                )
            )

            // Outer glow border (pulsing cyan)
            val glowIntensity = (0.38f + 0.25f * sin(animTime * 2f)).coerceIn(0.15f, 0.85f)
            val padOuter = 4.dp.toPx()
            drawRoundRect(
                color = Color(0, 200, 255).copy(alpha = glowIntensity),
                topLeft = Offset(padOuter, padOuter),
                size = Size(w - padOuter * 2, h - padOuter * 2),
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )

            // Middle HSV color-shifting border
            val midColor = Color.hsv(hueShift % 360f, 0.78f, 1f, alpha = 0.48f)
            val padMid = 7.dp.toPx()
            drawRoundRect(
                color = midColor,
                topLeft = Offset(padMid, padMid),
                size = Size(w - padMid * 2, h - padMid * 2),
                cornerRadius = CornerRadius(15.dp.toPx(), 15.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner sharp border
            val padIn = 9.dp.toPx()
            drawRoundRect(
                color = Color(0, 150, 255, 200),
                topLeft = Offset(padIn, padIn),
                size = Size(w - padIn * 2, h - padIn * 2),
                cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Pulsing cyan-mint L-corner brackets (`QColor(0, 255, 200, corner_alpha)`)
            val cornerAlpha = (0.45f + 0.35f * sin(animTime * 3f)).coerceIn(0.2f, 0.9f)
            val cornerColor = Color(0, 255, 200).copy(alpha = cornerAlpha)
            val cLen = 28.dp.toPx()
            val cPad = 14.dp.toPx()
            val cStroke = 2.5.dp.toPx()
            // Top-left
            drawLine(cornerColor, Offset(cPad, cPad), Offset(cPad + cLen, cPad), cStroke, StrokeCap.Round)
            drawLine(cornerColor, Offset(cPad, cPad), Offset(cPad, cPad + cLen), cStroke, StrokeCap.Round)
            // Top-right
            drawLine(cornerColor, Offset(w - cPad - cLen, cPad), Offset(w - cPad, cPad), cStroke, StrokeCap.Round)
            drawLine(cornerColor, Offset(w - cPad, cPad), Offset(w - cPad, cPad + cLen), cStroke, StrokeCap.Round)
            // Bottom-left
            drawLine(cornerColor, Offset(cPad, h - cPad), Offset(cPad + cLen, h - cPad), cStroke, StrokeCap.Round)
            drawLine(cornerColor, Offset(cPad, h - cPad - cLen), Offset(cPad, h - cPad), cStroke, StrokeCap.Round)
            // Bottom-right
            drawLine(cornerColor, Offset(w - cPad - cLen, h - cPad), Offset(w - cPad, h - cPad), cStroke, StrokeCap.Round)
            drawLine(cornerColor, Offset(w - cPad, h - cPad - cLen), Offset(w - cPad, h - cPad), cStroke, StrokeCap.Round)
        }

        // 2. Particle Engine Overlay (`effects/particle_engine.py`)
        if (particlesEnabled) {
            MayaParticleEngineOverlay(
                particleCount = 110,
                maxConnections = 95,
                showScanlines = true,
                testTagName = "maya_particle_engine_full"
            )
        }

        // 3. Main Foreground HUD Layout (Safe Drawing Insets)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val isThreeColumn = maxWidth >= 680.dp

            Column(modifier = Modifier.fillMaxSize()) {
                // HUD Top Control Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Surface(
                            color = Color(0, 60, 110, 140),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, Color(0xFF00C8FF), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "MAYA AI HUD",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

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
                            label = { Text(stringResource(R.string.hud_particles_label), fontSize = 11.sp) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("full_hud_particles_toggle")
                        )

                        FilterChip(
                            selected = permanentBrainEnabled,
                            onClick = onTogglePermanentBrain,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    if (permanentBrainEnabled) "Silent Brain ON" else "Silent Brain",
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("full_hud_brain_toggle")
                        )

                        FilterChip(
                            selected = floatingHudVisible,
                            onClick = onSwitchToFloatingMiniHud,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.DashboardCustomize,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Mini Float", fontSize = 11.sp) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("full_hud_float_switch")
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onTapOrbOrMic,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isListening) Color(0xFFFF3B5C) else Color(0, 150, 255, 90))
                                .testTag("full_hud_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.cd_mic_button),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(30, 30, 55, 200))
                                .border(1.dp, Color(0, 200, 255, 140), CircleShape)
                                .testTag("close_full_maya_hud_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close_full_maya_hud),
                                tint = Color(0xFF00E5FF)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isThreeColumn) {
                    // Canonical 3-Column `MainWindow` Layout: LeftPanel | Center (Orb 2x + MicWave 1x) | RightPanel
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        MayaLeftSystemPanel(
                            snapshot = snapshot,
                            modifier = Modifier
                                .width(235.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(2f)
                                    .fillMaxWidth()
                            ) {
                                MayaReactiveOrb(
                                    audioLevel = orbEnergy,
                                    isListening = isListening,
                                    isThinking = isThinking,
                                    isSpeaking = isSpeaking,
                                    guardArmed = guardArmed,
                                    onTapOrb = onTapOrbOrMic,
                                    centerLabel = persona.displayName.uppercase(Locale.ROOT),
                                    testTagName = "full_hud_reactive_orb",
                                    modifier = Modifier.size(220.dp)
                                )
                            }

                            MayaWaveMicVisualizer(
                                micLevel = if (isListening) micLevel.coerceAtLeast(audioLevel) else audioLevel,
                                isListening = isListening,
                                isSpeaking = isSpeaking,
                                waveHeightDp = 110,
                                testTagName = "full_hud_wave_mic_visualizer",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        MayaRightStatusPanel(
                            snapshot = snapshot,
                            apiKeyStatus = apiKeyStatus,
                            isListening = isListening,
                            isSpeaking = isSpeaking,
                            permanentBrainEnabled = permanentBrainEnabled,
                            modifier = Modifier
                                .width(225.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                } else {
                    // Responsive Portrait Handheld HUD Layout: Orb + Wave Mic + Left System Metrics + Right AI Status
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 42.dp)
                    ) {
                        MayaReactiveOrb(
                            audioLevel = orbEnergy,
                            isListening = isListening,
                            isThinking = isThinking,
                            isSpeaking = isSpeaking,
                            guardArmed = guardArmed,
                            onTapOrb = onTapOrbOrMic,
                            centerLabel = persona.displayName.uppercase(Locale.ROOT),
                            testTagName = "full_hud_reactive_orb",
                            modifier = Modifier.size(186.dp)
                        )

                        MayaWaveMicVisualizer(
                            micLevel = if (isListening) micLevel.coerceAtLeast(audioLevel) else audioLevel,
                            isListening = isListening,
                            isSpeaking = isSpeaking,
                            waveHeightDp = 96,
                            testTagName = "full_hud_wave_mic_visualizer",
                            modifier = Modifier.fillMaxWidth()
                        )

                        MayaLeftSystemPanel(
                            snapshot = snapshot,
                            modifier = Modifier.fillMaxWidth()
                        )

                        MayaRightStatusPanel(
                            snapshot = snapshot,
                            apiKeyStatus = apiKeyStatus,
                            isListening = isListening,
                            isSpeaking = isSpeaking,
                            permanentBrainEnabled = permanentBrainEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 4. Bottom-Right Signature Widget (`effects/signature_widget.py` — `HunterIsLive`)
            MayaSignatureWidget(
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}
