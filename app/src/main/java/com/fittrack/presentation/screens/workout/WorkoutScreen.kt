package com.fittrack.presentation.screens.workout

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fittrack.domain.model.Workout
import com.fittrack.presentation.theme.*
import java.time.format.DateTimeFormatter

@Composable
fun WorkoutScreen(
    onCreateWorkout: () -> Unit,
    onWorkoutClick: (Long) -> Unit,
    vm: WorkoutViewModel = hiltViewModel()
) {
    val workouts by vm.workouts.collectAsState()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateWorkout,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Novo Treino") },
                containerColor = Primary,
                contentColor = OnPrimary
            )
        }
    ) { padding ->
        if (workouts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FitnessCenter, null, tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Nenhum treino ainda", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Cria o teu primeiro treino!", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Os Meus Treinos", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                items(workouts, key = { it.id }) { workout ->
                    WorkoutCard(workout = workout, onClick = { onWorkoutClick(workout.id) })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun WorkoutCard(workout: Workout, onClick: () -> Unit) {
    val totalSets = workout.exercises.sumOf { it.sets.size }
    val totalVolume = workout.exercises.sumOf { ex -> ex.sets.sumOf { (it.weightKg * it.reps).toDouble() } }
    val muscleGroups = workout.exercises.map { it.bodyPart }.distinct().take(3)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVar),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(workout.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        workout.date.format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", java.util.Locale("pt", "PT"))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (workout.durationMinutes > 0) {
                    Surface(color = Primary.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                        Text("${workout.durationMinutes} min",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = Primary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatPill(Icons.Filled.FitnessCenter, "${workout.exercises.size} exerc.")
                StatPill(Icons.Filled.Repeat, "$totalSets séries")
                if (totalVolume > 0)
                    StatPill(Icons.Filled.Scale, "${String.format("%.0f", totalVolume)} kg")
            }
            if (muscleGroups.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    muscleGroups.forEach { group ->
                        Surface(color = Secondary.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                            Text(group.replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = Secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
