package com.fittrack.presentation.screens.history

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fittrack.domain.model.OneRepMaxPoint
import com.fittrack.presentation.theme.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistoryScreen(
    onNavigateBack: () -> Unit,
    vm: ExerciseHistoryViewModel = hiltViewModel()
) {
    val history by vm.history.collectAsState()
    val oneRepMax by vm.oneRepMaxProgress.collectAsState()
    val bestVolume by vm.bestSingleSetVolume.collectAsState()

    val sessions = remember(history) {
        history.groupBy { it.workoutId to it.date }
            .toList()
            .sortedByDescending { (key, _) -> key.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.exerciseName.ifBlank { "Histórico" }, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Ainda sem histórico", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Este exercício aparece aqui depois de completares um treino com ele.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // 1RM estimado + melhor volume numa série (roadmap 3.3)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("1RM Estimado", "${String.format("%.1f", oneRepMax.lastOrNull()?.estimated1RM ?: 0f)} kg", Modifier.weight(1f))
                    StatBox("Melhor Série", "${String.format("%.0f", bestVolume)} kg", Modifier.weight(1f))
                }
            }

            // Gráfico de evolução (roadmap 3.2)
            if (oneRepMax.size >= 2) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Evolução de 1RM Estimado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            OneRepMaxLineChart(points = oneRepMax)
                        }
                    }
                }
            }

            item { Text("Sessões", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            items(sessions) { (key, rows) ->
                val (_, date) = key
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        rows.sortedBy { it.setNumber }.forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Série ${row.setNumber}", style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${row.reps} × ${row.weightKg} kg", style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold)
                                row.rpe?.let { rpe ->
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = Accent.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                        Text("RPE $rpe", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall, color = Accent)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Small self-contained line chart drawn on Canvas.
 *
 * Note: the project already depends on Vico (see build.gradle.kts) and the roadmap recommends
 * migrating to it for exactly this kind of chart. We didn't wire it up here because the exact
 * Vico API differs meaningfully between versions and the pinned version isn't visible from this
 * module alone (no version catalog was included in the zip) — guessing at the API would risk a
 * broken build. This Canvas chart is a safe drop-in that can be swapped for a Vico `LineChart`
 * later once the version is confirmed, without touching the ViewModel.
 */
@Composable
private fun OneRepMaxLineChart(points: List<OneRepMaxPoint>) {
    val max = (points.maxOfOrNull { it.estimated1RM } ?: 1f).coerceAtLeast(1f)
    val min = (points.minOfOrNull { it.estimated1RM } ?: 0f)
    val range = (max - min).coerceAtLeast(1f)

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            if (points.size < 2) return@Canvas
            val stepX = size.width / (points.size - 1)
            val coords = points.mapIndexed { i, p ->
                val x = i * stepX
                val y = size.height - ((p.estimated1RM - min) / range) * size.height
                Offset(x, y)
            }
            for (i in 0 until coords.size - 1) {
                drawLine(
                    color = Primary,
                    start = coords[i],
                    end = coords[i + 1],
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
            }
            coords.forEach { c ->
                drawCircle(color = Primary, radius = 6f, center = c)
                drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 2.5f, center = c)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().date.format(DateTimeFormatter.ofPattern("dd/MM")),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(points.last().date.format(DateTimeFormatter.ofPattern("dd/MM")),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
