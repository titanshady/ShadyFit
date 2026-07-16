package com.fittrack.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrack.domain.model.WorkoutExercise
import com.fittrack.presentation.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Which muscles are activated – derived from exercises added to the session
data class MuscleActivation(
    val chest: Float = 0f,
    val frontShoulders: Float = 0f,
    val biceps: Float = 0f,
    val forearms: Float = 0f,
    val quads: Float = 0f,
    val abs: Float = 0f,
    val obliques: Float = 0f,
    // back side
    val traps: Float = 0f,
    val backShoulders: Float = 0f,
    val lats: Float = 0f,
    val triceps: Float = 0f,
    val glutes: Float = 0f,
    val hamstrings: Float = 0f,
    val calves: Float = 0f
)

fun List<WorkoutExercise>.toMuscleActivation(): MuscleActivation {
    var chest = 0f; var fShoulder = 0f; var biceps = 0f; var forearms = 0f
    var quads = 0f; var abs = 0f; var obliques = 0f
    var traps = 0f; var bShoulder = 0f; var lats = 0f; var triceps = 0f
    var glutes = 0f; var hams = 0f; var calves = 0f

    forEach { ex ->
        val t = ex.targetMuscle.lowercase()
        val b = ex.bodyPart.lowercase()
        // Primary
        when {
            t.contains("pectoral")            -> chest    = minOf(1f, chest + 0.6f)
            t.contains("delt") || b == "shoulders" -> { fShoulder = minOf(1f, fShoulder + 0.5f); bShoulder = minOf(1f, bShoulder + 0.3f) }
            t.contains("bicep")               -> biceps   = minOf(1f, biceps + 0.7f)
            t.contains("tricep")              -> triceps  = minOf(1f, triceps + 0.7f)
            t.contains("forearm") || t.contains("brachioradialis") -> forearms = minOf(1f, forearms + 0.5f)
            t.contains("quad")                -> quads    = minOf(1f, quads + 0.7f)
            t.contains("abs") || t.contains("abdominal") -> abs = minOf(1f, abs + 0.7f)
            t.contains("oblique")             -> obliques = minOf(1f, obliques + 0.6f)
            t.contains("trap")                -> traps    = minOf(1f, traps + 0.6f)
            t.contains("lat")                 -> lats     = minOf(1f, lats + 0.7f)
            t.contains("spine") || t.contains("erector") -> lats = minOf(1f, lats + 0.4f)
            t.contains("glut")                -> glutes   = minOf(1f, glutes + 0.7f)
            t.contains("hamstring")           -> hams     = minOf(1f, hams + 0.7f)
            t.contains("calf") || t.contains("calves") || t.contains("gastrocnemius") -> calves = minOf(1f, calves + 0.7f)
        }
        // Body part secondary
        when (b) {
            "chest"      -> chest    = minOf(1f, chest + 0.3f)
            "back"       -> { lats = minOf(1f, lats + 0.3f); traps = minOf(1f, traps + 0.2f) }
            "upper legs" -> { quads = minOf(1f, quads + 0.2f); hams = minOf(1f, hams + 0.2f); glutes = minOf(1f, glutes + 0.1f) }
            "lower legs" -> calves  = minOf(1f, calves + 0.3f)
            "upper arms" -> { biceps = minOf(1f, biceps + 0.2f); triceps = minOf(1f, triceps + 0.2f) }
            "waist"      -> abs     = minOf(1f, abs + 0.3f)
        }
    }
    return MuscleActivation(chest, fShoulder, biceps, forearms, quads, abs, obliques,
        traps, bShoulder, lats, triceps, glutes, hams, calves)
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = 1f
)

private fun muscleColor(activation: Float) = if (activation < 0.01f)
    MuscleInactive
else lerp(Color(0xFFFF8C00), MuscleChest, activation)   // orange → red

@Composable
fun BodyFigureWidget(
    exercises: List<WorkoutExercise>,
    modifier: Modifier = Modifier
) {
    val activation = remember(exercises) { exercises.toMuscleActivation() }

    // pulse animation for active muscles
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Frontal", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Canvas(modifier = Modifier.size(110.dp, 220.dp)) {
                    drawFrontBody(activation, pulse)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Posterior", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Canvas(modifier = Modifier.size(110.dp, 220.dp)) {
                    drawBackBody(activation, pulse)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Legend
        val active = buildActiveMuscleList(activation)
        if (active.isNotEmpty()) {
            Text("Músculos ativados", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
            active.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (name, color) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Canvas(Modifier.size(8.dp)) {
                                drawCircle(color)
                            }
                            Spacer(Modifier.width(3.dp))
                            Text(name, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        } else {
            Text("Adiciona exercícios para ver os músculos ativados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun buildActiveMuscleList(a: MuscleActivation): List<Pair<String, Color>> = buildList {
    if (a.chest > 0f)          add("Peito" to muscleColor(a.chest))
    if (a.frontShoulders > 0f) add("Ombros" to muscleColor(a.frontShoulders))
    if (a.biceps > 0f)         add("Bicípite" to muscleColor(a.biceps))
    if (a.triceps > 0f)        add("Tricípite" to muscleColor(a.triceps))
    if (a.forearms > 0f)       add("Antebraço" to muscleColor(a.forearms))
    if (a.abs > 0f)            add("Abs" to muscleColor(a.abs))
    if (a.quads > 0f)          add("Quadricípite" to muscleColor(a.quads))
    if (a.lats > 0f)           add("Dorsal" to muscleColor(a.lats))
    if (a.traps > 0f)          add("Trapézio" to muscleColor(a.traps))
    if (a.glutes > 0f)         add("Glúteos" to muscleColor(a.glutes))
    if (a.hamstrings > 0f)     add("Posteriores" to muscleColor(a.hamstrings))
    if (a.calves > 0f)         add("Gémeos" to muscleColor(a.calves))
}

// --- Drawing functions --------------------------------------------------------

private fun DrawScope.drawFrontBody(a: MuscleActivation, pulse: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    // -- Head --
    drawCircle(Color(0xFF3A3A4A), radius = w * 0.15f, center = Offset(cx, h * 0.06f))

    // -- Neck --
    drawRect(Color(0xFF3A3A4A), topLeft = Offset(cx - w * 0.06f, h * 0.11f),
        size = Size(w * 0.12f, h * 0.05f))

    // -- Trapezius / Shoulders --
    val shoulderColor = muscleColor(a.frontShoulders).copy(
        alpha = if (a.frontShoulders > 0f) pulse else 1f)
    drawOval(shoulderColor, topLeft = Offset(cx - w * 0.45f, h * 0.14f),
        size = Size(w * 0.38f, h * 0.08f))
    drawOval(shoulderColor, topLeft = Offset(cx + w * 0.07f, h * 0.14f),
        size = Size(w * 0.38f, h * 0.08f))

    // -- Chest --
    val chestColor = muscleColor(a.chest).copy(alpha = if (a.chest > 0f) pulse else 1f)
    drawOval(chestColor, topLeft = Offset(cx - w * 0.35f, h * 0.16f),
        size = Size(w * 0.35f, h * 0.12f))
    drawOval(chestColor, topLeft = Offset(cx, h * 0.16f),
        size = Size(w * 0.35f, h * 0.12f))

    // -- Abdomen --
    val absColor = muscleColor(a.abs).copy(alpha = if (a.abs > 0f) pulse else 1f)
    // 6-pack grid
    for (row in 0..2) {
        for (col in 0..1) {
            drawRoundRect(absColor,
                topLeft = Offset(cx - w * 0.22f + col * w * 0.23f, h * 0.29f + row * h * 0.06f),
                size = Size(w * 0.18f, h * 0.05f),
                cornerRadius = CornerRadius(4f, 4f))
        }
    }

    // -- Obliques --
    val oblColor = muscleColor(a.obliques).copy(alpha = if (a.obliques > 0f) pulse else 1f)
    drawOval(oblColor, topLeft = Offset(cx - w * 0.45f, h * 0.29f), size = Size(w * 0.18f, h * 0.17f))
    drawOval(oblColor, topLeft = Offset(cx + w * 0.27f, h * 0.29f), size = Size(w * 0.18f, h * 0.17f))

    // -- Biceps --
    val bicColor = muscleColor(a.biceps).copy(alpha = if (a.biceps > 0f) pulse else 1f)
    drawOval(bicColor, topLeft = Offset(cx - w * 0.48f, h * 0.22f), size = Size(w * 0.14f, h * 0.13f))
    drawOval(bicColor, topLeft = Offset(cx + w * 0.34f, h * 0.22f), size = Size(w * 0.14f, h * 0.13f))

    // -- Forearms --
    val forearmColor = muscleColor(a.forearms).copy(alpha = if (a.forearms > 0f) pulse else 1f)
    drawOval(forearmColor, topLeft = Offset(cx - w * 0.50f, h * 0.35f), size = Size(w * 0.12f, h * 0.11f))
    drawOval(forearmColor, topLeft = Offset(cx + w * 0.38f, h * 0.35f), size = Size(w * 0.12f, h * 0.11f))

    // -- Quads --
    val quadColor = muscleColor(a.quads).copy(alpha = if (a.quads > 0f) pulse else 1f)
    drawOval(quadColor, topLeft = Offset(cx - w * 0.35f, h * 0.50f), size = Size(w * 0.30f, h * 0.22f))
    drawOval(quadColor, topLeft = Offset(cx + w * 0.05f, h * 0.50f), size = Size(w * 0.30f, h * 0.22f))

    // -- Calves (front) --
    val calvesColor = muscleColor(a.calves).copy(alpha = if (a.calves > 0f) pulse else 1f)
    drawOval(calvesColor, topLeft = Offset(cx - w * 0.32f, h * 0.74f), size = Size(w * 0.22f, h * 0.17f))
    drawOval(calvesColor, topLeft = Offset(cx + w * 0.10f, h * 0.74f), size = Size(w * 0.22f, h * 0.17f))

    // -- Torso outline --
    val path = Path().apply {
        moveTo(cx - w * 0.32f, h * 0.15f)
        lineTo(cx - w * 0.32f, h * 0.48f)
        lineTo(cx - w * 0.22f, h * 0.50f)
        lineTo(cx - w * 0.20f, h * 0.73f)
        lineTo(cx + w * 0.20f, h * 0.73f)
        lineTo(cx + w * 0.22f, h * 0.50f)
        lineTo(cx + w * 0.32f, h * 0.48f)
        lineTo(cx + w * 0.32f, h * 0.15f)
        close()
    }
    drawPath(path, Color(0xFF4A4A5A), style = Stroke(width = 1.5f))
}

private fun DrawScope.drawBackBody(a: MuscleActivation, pulse: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f

    // -- Head --
    drawCircle(Color(0xFF3A3A4A), radius = w * 0.15f, center = Offset(cx, h * 0.06f))
    drawRect(Color(0xFF3A3A4A), topLeft = Offset(cx - w * 0.06f, h * 0.11f), size = Size(w * 0.12f, h * 0.05f))

    // -- Trapezius --
    val trapsColor = muscleColor(a.traps).copy(alpha = if (a.traps > 0f) pulse else 1f)
    drawOval(trapsColor, topLeft = Offset(cx - w * 0.26f, h * 0.14f), size = Size(w * 0.52f, h * 0.11f))

    // -- Back Shoulders --
    val bShoulder = muscleColor(a.backShoulders).copy(alpha = if (a.backShoulders > 0f) pulse else 1f)
    drawOval(bShoulder, topLeft = Offset(cx - w * 0.48f, h * 0.16f), size = Size(w * 0.22f, h * 0.1f))
    drawOval(bShoulder, topLeft = Offset(cx + w * 0.26f, h * 0.16f), size = Size(w * 0.22f, h * 0.1f))

    // -- Lats --
    val latsColor = muscleColor(a.lats).copy(alpha = if (a.lats > 0f) pulse else 1f)
    drawOval(latsColor, topLeft = Offset(cx - w * 0.42f, h * 0.24f), size = Size(w * 0.30f, h * 0.20f))
    drawOval(latsColor, topLeft = Offset(cx + w * 0.12f, h * 0.24f), size = Size(w * 0.30f, h * 0.20f))

    // -- Lower Back --
    drawRect(latsColor.copy(alpha = latsColor.alpha * 0.6f),
        topLeft = Offset(cx - w * 0.18f, h * 0.38f), size = Size(w * 0.36f, h * 0.09f))

    // -- Triceps --
    val triColor = muscleColor(a.triceps).copy(alpha = if (a.triceps > 0f) pulse else 1f)
    drawOval(triColor, topLeft = Offset(cx - w * 0.49f, h * 0.22f), size = Size(w * 0.14f, h * 0.13f))
    drawOval(triColor, topLeft = Offset(cx + w * 0.35f, h * 0.22f), size = Size(w * 0.14f, h * 0.13f))

    // -- Forearms back --
    val forearmColor = muscleColor(a.forearms).copy(alpha = if (a.forearms > 0f) pulse else 1f)
    drawOval(forearmColor, topLeft = Offset(cx - w * 0.50f, h * 0.35f), size = Size(w * 0.12f, h * 0.11f))
    drawOval(forearmColor, topLeft = Offset(cx + w * 0.38f, h * 0.35f), size = Size(w * 0.12f, h * 0.11f))

    // -- Glutes --
    val glutesColor = muscleColor(a.glutes).copy(alpha = if (a.glutes > 0f) pulse else 1f)
    drawOval(glutesColor, topLeft = Offset(cx - w * 0.34f, h * 0.48f), size = Size(w * 0.30f, h * 0.13f))
    drawOval(glutesColor, topLeft = Offset(cx + w * 0.04f, h * 0.48f), size = Size(w * 0.30f, h * 0.13f))

    // -- Hamstrings --
    val hamsColor = muscleColor(a.hamstrings).copy(alpha = if (a.hamstrings > 0f) pulse else 1f)
    drawOval(hamsColor, topLeft = Offset(cx - w * 0.35f, h * 0.52f), size = Size(w * 0.30f, h * 0.22f))
    drawOval(hamsColor, topLeft = Offset(cx + w * 0.05f, h * 0.52f), size = Size(w * 0.30f, h * 0.22f))

    // -- Calves back --
    val calvesColor = muscleColor(a.calves).copy(alpha = if (a.calves > 0f) pulse else 1f)
    drawOval(calvesColor, topLeft = Offset(cx - w * 0.32f, h * 0.75f), size = Size(w * 0.22f, h * 0.16f))
    drawOval(calvesColor, topLeft = Offset(cx + w * 0.10f, h * 0.75f), size = Size(w * 0.22f, h * 0.16f))

    // -- Torso outline --
    val path = Path().apply {
        moveTo(cx - w * 0.32f, h * 0.15f)
        lineTo(cx - w * 0.32f, h * 0.48f)
        lineTo(cx - w * 0.22f, h * 0.50f)
        lineTo(cx - w * 0.20f, h * 0.73f)
        lineTo(cx + w * 0.20f, h * 0.73f)
        lineTo(cx + w * 0.22f, h * 0.50f)
        lineTo(cx + w * 0.32f, h * 0.48f)
        lineTo(cx + w * 0.32f, h * 0.15f)
        close()
    }
    drawPath(path, Color(0xFF4A4A5A), style = Stroke(width = 1.5f))
}
