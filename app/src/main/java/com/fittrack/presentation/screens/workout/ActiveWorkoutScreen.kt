package com.fittrack.presentation.screens.workout

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fittrack.presentation.components.BodyFigureWidget
import com.fittrack.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    workoutId: Long,
    onFinish: () -> Unit,
    vm: WorkoutViewModel = hiltViewModel()
) {
    val workoutName     by vm.workoutName.collectAsState()
    val exercises       by vm.exercises.collectAsState()
    val durationSeconds by vm.durationSeconds.collectAsState()
    val restCountdown   by vm.restCountdown.collectAsState()

    var showFinishDialog by remember { mutableStateOf(false) }
    var showBodyFigure   by remember { mutableStateOf(false) }

    // Load template and start timer when screen opens
    LaunchedEffect(workoutId) {
        vm.loadSessionFromTemplate(workoutId)
        vm.startSessionTimer()
    }

    // Cleanup timer if we leave
    DisposableEffect(Unit) {
        onDispose { vm.stopSessionTimer() }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Terminar Treino?") },
            text = {
                val completedSets = exercises.sumOf { ex -> ex.sets.count { it.completed } }
                val totalSets     = exercises.sumOf { it.sets.size }
                Text("Séries completadas: $completedSets / $totalSets\nDuração: ${formatDuration(durationSeconds)}")
            },
            confirmButton = {
                Button(
                    onClick = { vm.finishSession(workoutId); onFinish() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("Terminar") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("Continuar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workoutName, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { showFinishDialog = true }) {
                        Icon(Icons.Filled.Close, null)
                    }
                },
                actions = {
                    // Live timer display
                    Surface(
                        color  = Primary.copy(alpha = 0.15f),
                        shape  = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            formatDuration(durationSeconds),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style    = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color    = Primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { showFinishDialog = true }) {
                        Icon(Icons.Filled.CheckCircle, "Terminar", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Rest countdown banner
            item {
                AnimatedVisibility(
                    visible = restCountdown > 0,
                    enter   = slideInVertically() + fadeIn(),
                    exit    = slideOutVertically() + fadeOut()
                ) {
                    RestCountdownBanner(
                        seconds  = restCountdown,
                        onSkip   = { vm.skipRest() }
                    )
                }
            }

            // Body figure toggle
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ver músculos ativados", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { showBodyFigure = !showBodyFigure }) {
                            Icon(if (showBodyFigure) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Primary)
                        }
                    }
                    AnimatedVisibility(visible = showBodyFigure) {
                        BodyFigureWidget(exercises = exercises, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp))
                    }
                }
            }

            // Progress summary
            item {
                val completedSets = exercises.sumOf { ex -> ex.sets.count { it.completed } }
                val totalSets     = exercises.sumOf { it.sets.size }
                val progress      = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f

                Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Progresso", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$completedSets / $totalSets séries", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress    = { progress },
                            modifier    = Modifier.fillMaxWidth().height(8.dp),
                            color       = Primary,
                            trackColor  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            strokeCap   = StrokeCap.Round
                        )
                    }
                }
            }

            // Exercise cards with completion toggles
            itemsIndexed(exercises, key = { i, _ -> i }) { index, workoutExercise ->
                ExerciseCard(
                    workoutExercise  = workoutExercise,
                    index            = index,
                    showCompleted    = true,
                    onAddSet         = { vm.addSet(index) },
                    onRemoveSet      = { setIdx -> vm.removeSet(index, setIdx) },
                    onUpdateSet      = { setIdx, reps, weight -> vm.updateSet(index, setIdx, reps, weight) },
                    onToggleSet      = { setIdx -> vm.toggleSetCompleted(index, setIdx) },
                    onRemoveExercise = { vm.removeExercise(index) }
                )
            }

            // Finish button at the bottom
            item {
                Button(
                    onClick  = { showFinishDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Icon(Icons.Filled.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Terminar Treino", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun RestCountdownBanner(seconds: Int, onSkip: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Secondary.copy(alpha = 0.2f)),
        shape  = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = Secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Tempo de descanso", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration(seconds), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Secondary)
                }
            }
            TextButton(onClick = onSkip) {
                Text("Saltar", color = Secondary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
