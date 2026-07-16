package com.fittrack.presentation.screens.workout

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fittrack.domain.model.WorkoutExercise
import com.fittrack.presentation.components.BodyFigureWidget
import com.fittrack.presentation.theme.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    onNavigateBack: () -> Unit,
    onEditWorkout: () -> Unit,
    onStartWorkout: () -> Unit,
    vm: WorkoutViewModel = hiltViewModel()
) {
    val workout by vm.selectedWorkout.collectAsState()

    LaunchedEffect(workoutId) { vm.loadWorkoutDetail(workoutId) }

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar treino") },
            text = { Text("Tens a certeza que queres eliminar este treino?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteWorkout(workoutId); onNavigateBack() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Eliminar")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.name ?: "Treino", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = onEditWorkout) { Icon(Icons.Filled.Edit, null, tint = Primary) }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        workout?.let { w ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Summary card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceVar),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                w.date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", java.util.Locale("pt", "PT"))),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                SummaryItem("Exercícios", "${w.exercises.size}")
                                SummaryItem("Séries", "${w.exercises.sumOf { it.sets.size }}")
                                if (w.durationMinutes > 0) SummaryItem("Duração", "${w.durationMinutes} min")
                                val vol = w.exercises.sumOf { ex -> ex.sets.sumOf { (it.weightKg * it.reps).toDouble() } }
                                if (vol > 0) SummaryItem("Volume", "${String.format("%.0f", vol)} kg")
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceVar),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Músculos Trabalhados", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            BodyFigureWidget(exercises = w.exercises, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                item { Text("Exercícios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

                items(w.exercises) { ex ->
                    ExerciseDetailCard(ex)
                }

                if (w.notes.isNotBlank()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Notas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(w.notes, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onStartWorkout,
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Iniciar Treino", fontWeight = FontWeight.Bold)
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseDetailCard(ex: WorkoutExercise) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(ex.exerciseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${ex.bodyPart} • ${ex.targetMuscle}", style = MaterialTheme.typography.labelSmall, color = Primary)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text("Série", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                Text("Reps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Peso", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Volume", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ex.sets.forEach { set ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${set.setNumber}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp),
                        color = if (set.completed) Primary else MaterialTheme.colorScheme.onSurface)
                    Text("${set.reps}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${set.weightKg} kg", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${String.format("%.1f", set.reps * set.weightKg)} kg", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
