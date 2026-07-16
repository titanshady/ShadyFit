package com.fittrack.presentation.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fittrack.data.remote.exerciseGifUrl
import com.fittrack.domain.model.ExerciseSet
import com.fittrack.domain.model.WorkoutExercise
import com.fittrack.presentation.components.BodyFigureWidget
import com.fittrack.presentation.components.ExerciseGifHero
import com.fittrack.presentation.components.ExerciseGifThumbnail
import com.fittrack.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorkoutScreen(
    onNavigateBack: () -> Unit,
    onBrowseExercises: () -> Unit,
    vm: WorkoutViewModel                              // always injected from parent graph in AppNavigation
) {
    val workoutName  by vm.workoutName.collectAsState()
    val exercises    by vm.exercises.collectAsState()
    val saveSuccess  by vm.saveSuccess.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    var showBodyFigure by remember { mutableStateOf(true) }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) { vm.resetSaveState(); onNavigateBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Criar Treino", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { vm.saveWorkoutTemplate() }, enabled = exercises.isNotEmpty()) {
                        Icon(Icons.Filled.Check, "Guardar", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        errorMessage?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); vm.clearError() }
            Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = workoutName, onValueChange = vm::setWorkoutName,
                    label = { Text("Nome do treino") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, leadingIcon = { Icon(Icons.Filled.Edit, null) }
                )
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Músculos Ativados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { showBodyFigure = !showBodyFigure }) {
                                Icon(if (showBodyFigure) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Primary)
                            }
                        }
                        AnimatedVisibility(visible = showBodyFigure) {
                            BodyFigureWidget(exercises = exercises, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                    }
                }
            }

            itemsIndexed(exercises, key = { i, _ -> i }) { index, workoutExercise ->
                ExerciseCard(
                    workoutExercise = workoutExercise, index = index, showCompleted = false,
                    onAddSet = { vm.addSet(index) },
                    onRemoveSet = { setIdx -> vm.removeSet(index, setIdx) },
                    onUpdateSet = { setIdx, reps, weight -> vm.updateSet(index, setIdx, reps, weight) },
                    onToggleSet = {},
                    onRemoveExercise = { vm.removeExercise(index) }
                )
            }

            item {
                OutlinedButton(onClick = onBrowseExercises, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Primary))) {
                    Icon(Icons.Filled.Add, null, tint = Primary); Spacer(Modifier.width(8.dp))
                    Text("Adicionar Exercício", color = Primary, fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Button(onClick = { vm.saveWorkoutTemplate() }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                    enabled = exercises.isNotEmpty()) {
                    Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp))
                    Text("Guardar Treino", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun ExerciseCard(
    workoutExercise: WorkoutExercise, index: Int, showCompleted: Boolean = true,
    onAddSet: () -> Unit, onRemoveSet: (Int) -> Unit, onUpdateSet: (Int, Int, Float) -> Unit,
    onToggleSet: (Int) -> Unit, onRemoveExercise: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val gifUrl = remember(workoutExercise.exerciseId) { exerciseGifUrl(workoutExercise.exerciseId) }

    Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (gifUrl.isNotBlank()) {
                    ExerciseGifThumbnail(
                        gifUrl = gifUrl,
                        contentDescription = workoutExercise.exerciseName,
                        size = 56.dp,
                        cornerRadius = 12.dp
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(workoutExercise.exerciseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${workoutExercise.bodyPart.replaceFirstChar { it.uppercase() }} • ${workoutExercise.targetMuscle}",
                        style = MaterialTheme.typography.labelSmall, color = Primary)
                }
                Row {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onRemoveExercise) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    // GIF em destaque — bem maior que a miniatura do cabeçalho,
                    // para dar pra ver a execução do movimento durante o treino.
                    if (gifUrl.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        ExerciseGifHero(
                            gifUrl = gifUrl,
                            contentDescription = workoutExercise.exerciseName,
                            height = 190.dp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Text("Série", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                        Text("Reps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("Peso (kg)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(if (showCompleted) 80.dp else 40.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    workoutExercise.sets.forEachIndexed { setIdx, set ->
                        SetRow(set = set, showCompleted = showCompleted,
                            onRepsChange = { reps -> onUpdateSet(setIdx, reps, set.weightKg) },
                            onWeightChange = { weight -> onUpdateSet(setIdx, set.reps, weight) },
                            onToggle = { onToggleSet(setIdx) },
                            onDelete = { if (workoutExercise.sets.size > 1) onRemoveSet(setIdx) })
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Adicionar Série")
                    }
                }
            }
        }
    }
}

@Composable
fun SetRow(
    set: ExerciseSet, showCompleted: Boolean = true,
    onRepsChange: (Int) -> Unit, onWeightChange: (Float) -> Unit,
    onToggle: () -> Unit, onDelete: () -> Unit
) {
    var repsText   by remember(set.reps) { mutableStateOf(set.reps.toString()) }
    var weightText by remember(set.weightKg) {
        mutableStateOf(if (set.weightKg == set.weightKg.toLong().toFloat()) set.weightKg.toLong().toString() else set.weightKg.toString())
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (set.completed && showCompleted) Primary.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${set.setNumber}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = if (set.completed && showCompleted) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp))
        OutlinedTextField(value = repsText, onValueChange = { v -> repsText = v; v.toIntOrNull()?.let { onRepsChange(it) } },
            modifier = Modifier.weight(1f).height(52.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(value = weightText, onValueChange = { v -> weightText = v; v.toFloatOrNull()?.let { onWeightChange(it) } },
            modifier = Modifier.weight(1f).height(52.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.width(4.dp))
        if (showCompleted) {
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(if (set.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                    tint = if (set.completed) Primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}
