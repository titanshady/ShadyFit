package com.fittrack.presentation.screens.analytics

import androidx.compose.foundation.background
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
import com.fittrack.data.local.entity.PersonalRecordEntity
import com.fittrack.presentation.theme.*
import java.time.format.DateTimeFormatter

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel = hiltViewModel()) {
    val totalWorkouts  by vm.totalWorkouts.collectAsState()
    val volumePoints   by vm.volumeOverTime.collectAsState()
    val topExercises   by vm.topExercises.collectAsState()
    val personalRecs   by vm.personalRecords.collectAsState()
    val totalVolume    by vm.totalVolume.collectAsState()

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
                BigStatCard("Volume\nTotal", "${formatVolume(totalVolume)}", Icons.Filled.Scale, Accent, Modifier.weight(1f))
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
                PersonalRecordCard(record)
            }
        }

        // Top Exercises
        if (topExercises.isNotEmpty()) {
            item {
                ChartCard(title = "Exercícios Mais Realizados") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val max = topExercises.maxOf { it.count }.toFloat()
                        topExercises.take(6).forEach { ex ->
                            Column {
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
private fun PersonalRecordCard(record: PersonalRecordEntity) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()) {
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
