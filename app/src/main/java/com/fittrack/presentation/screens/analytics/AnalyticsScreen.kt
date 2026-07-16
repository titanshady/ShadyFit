package com.fittrack.presentation.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fittrack.data.local.dao.ExerciseFrequency
import com.fittrack.data.local.entity.PersonalRecordEntity
import com.fittrack.presentation.theme.*
import java.time.format.DateTimeFormatter

@Composable
fun AnalyticsScreen(
    onExerciseClick: (exerciseId: String, exerciseName: String) -> Unit = { _, _ -> },
    vm: AnalyticsViewModel = hiltViewModel()
) {
    val totalWorkouts  by vm.totalWorkouts.collectAsState()
    val volumePoints   by vm.volumeOverTime.collectAsState()
    val topExercises   by vm.topExercises.collectAsState()
    val personalRecs   by vm.personalRecords.collectAsState()
    val totalVolume    by vm.totalVolume.collectAsState()
    val streak         by vm.streakInfo.collectAsState()
    val weekChange     by vm.weekOverWeekChange.collectAsState()
    val mostTrainedMuscle by vm.mostTrainedMuscleGroup.collectAsState()

    // Roadmap 8.2: illustrated empty state for a brand-new user — showing a wall of
    // zeroed-out stat cards before there's any data feels broken, not empty.
    if (totalWorkouts == 0) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Filled.BarChart, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("Ainda sem dados para analisar", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("Completa o teu primeiro treino para veres estatísticas, gráficos e recordes aqui.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            Text("Análise", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // Top stat cards
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigStatCard("Treinos\nRealizados", "$totalWorkouts", Icons.Filled.FitnessCenter, Primary, Modifier.weight(1f))
                BigStatCard("Volume\nTotal", formatVolume(totalVolume), Icons.Filled.Scale, Accent, Modifier.weight(1f))
            }
        }

        // Streak + weekly goal (roadmap 5.1 / 5.2)
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigStatCard(
                    "Sequência\nAtual", "${streak.currentStreakDays}d", Icons.Filled.LocalFireDepartment,
                    Color(0xFFFF7A00), Modifier.weight(1f)
                )
                BigStatCard(
                    "Meta\nSemanal", "${streak.workoutsThisWeek}/${streak.weeklyGoal}", Icons.Filled.Flag,
                    if (streak.workoutsThisWeek >= streak.weeklyGoal) Color(0xFF2ECC71) else Primary, Modifier.weight(1f)
                )
            }
        }

        // Week-over-week volume comparison (roadmap 3.5)
        weekChange?.let { (thisWeek, _, pctChange) ->
            item {
                ChartCard(title = "Volume Esta Semana vs Anterior") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatVolume(thisWeek), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        val positive = pctChange >= 0
                        Surface(
                            color = (if (positive) Color(0xFF2ECC71) else Color(0xFFE74C3C)).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (positive) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                                    null, tint = if (positive) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${if (positive) "+" else ""}${String.format("%.0f", pctChange)}%",
                                    color = if (positive) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Most trained muscle group (roadmap 5.3)
        mostTrainedMuscle?.let { muscle ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = muscleColor(muscle).copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Whatshot, null, tint = muscleColor(muscle))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Grupo Muscular Mais Treinado", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(muscle.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = muscleColor(muscle))
                        }
                    }
                }
            }
        }

        // Volume over time chart (custom bar chart)
        if (volumePoints.isNotEmpty()) {
            item {
                ChartCard(title = "Volume por Treino (kg)") {
                    SimpleBarChart(
                        data   = volumePoints.takeLast(15).map { it.totalVolume },
                        labels = volumePoints.takeLast(15).map {
                            it.date.format(DateTimeFormatter.ofPattern("dd/MM"))
                        },
                        barColor = Primary
                    )
                }
            }
        }

        // Personal Records
        if (personalRecs.isNotEmpty()) {
            item {
                Text("Recordes Pessoais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(personalRecs.take(10)) { record ->
                PersonalRecordCard(record, onClick = { onExerciseClick(record.exerciseId, record.exerciseName) })
            }
        }

        // Top Exercises
        if (topExercises.isNotEmpty()) {
            item {
                ChartCard(title = "Exercícios Mais Realizados") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val max = topExercises.maxOf { it.count }.toFloat()
                        topExercises.take(6).forEach { ex ->
                            Column(
                                modifier = Modifier.clickable { onExerciseClick(ex.exerciseId, ex.exerciseName) }
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(ex.exerciseName, style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f))
                                    Text("${ex.count}x", style = MaterialTheme.typography.labelMedium,
                                        color = Primary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(3.dp))
                                LinearProgressIndicator(
                                    progress = { ex.count / max },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = muscleColor(ex.bodyPart),
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Empty state
        if (totalWorkouts == 0) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.BarChart, null, tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Sem dados ainda", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Completa o teu primeiro treino para\nveres as tuas estatísticas aqui!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// --- Helper composables -------------------------------------------------------

@Composable
private fun BigStatCard(
    label: String, value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color, modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = SurfaceVar),
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun PersonalRecordCard(record: PersonalRecordEntity, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.exerciseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(record.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${record.maxWeightKg} kg", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = Primary)
                Text("× ${record.repsAtMax} reps", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

// --- Simple Bar Chart (no external lib needed) --------------------------------

@Composable
private fun SimpleBarChart(data: List<Float>, labels: List<String>, barColor: Color) {
    if (data.isEmpty()) return
    val max = data.max().coerceAtLeast(1f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            data.forEach { value ->
                val fraction = value / max
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            labels.forEach { label ->
                Text(
                    label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }
    }
}

private fun muscleColor(bodyPart: String): Color = when (bodyPart.lowercase()) {
    "chest"      -> MuscleChest
    "back"       -> MuscleBack
    "shoulders"  -> MuscleShoulders
    "upper arms" -> MuscleArms
    "upper legs" -> MuscleLegs
    "lower legs" -> MuscleCalves
    "waist"      -> MuscleCore
    else         -> Primary
}

private fun formatVolume(v: Float): String = when {
    v >= 1_000_000 -> "${String.format("%.1f", v / 1_000_000)}M kg"
    v >= 1_000     -> "${String.format("%.1f", v / 1_000)}k kg"
    else           -> "${v.toInt()} kg"
}
