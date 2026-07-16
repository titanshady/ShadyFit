package com.fittrack.presentation.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.fittrack.data.remote.exerciseGifUrl
import com.fittrack.domain.model.ExerciseSet
import com.fittrack.domain.model.WorkoutExercise
import com.fittrack.presentation.components.BodyFigureWidget
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
                    onRemoveExercise = { vm.removeExercise(index) },
                    onUpdateNotes = { notes -> vm.updateExerciseNotes(index, notes) }
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
    onToggleSet: (Int) -> Unit, onRemoveExercise: () -> Unit,
    lastSession: List<ExerciseSet>? = null,
    onUpdateNotes: ((String) -> Unit)? = null,
    onUpdateRpe: ((Int, Int?) -> Unit)? = null,
    gifSize: Dp = 48.dp,
    // When null, the card manages its own expanded/collapsed state (CreateWorkoutScreen).
    // When provided, the caller drives it (ActiveWorkoutScreen — only one exercise expanded
    // at a time, auto-advancing as sets are completed).
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    var internalExpanded by remember { mutableStateOf(true) }
    val isExpanded = expanded ?: internalExpanded
    val toggleExpanded: () -> Unit = {
        if (onExpandedChange != null) onExpandedChange(!isExpanded) else internalExpanded = !internalExpanded
    }
    var showNotesField by remember { mutableStateOf(workoutExercise.notes.isNotBlank()) }
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                val gifUrl = remember(workoutExercise.exerciseId) { exerciseGifUrl(workoutExercise.exerciseId) }
                if (gifUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(gifUrl)
                            .decoderFactory(if (android.os.Build.VERSION.SDK_INT >= 28)
                                ImageDecoderDecoder.Factory() else GifDecoder.Factory())
                            .crossfade(true)
                            .build(),
                        contentDescription = workoutExercise.exerciseName,
                        modifier = Modifier.size(gifSize).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(workoutExercise.exerciseName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${workoutExercise.bodyPart.replaceFirstChar { it.uppercase() }} • ${workoutExercise.targetMuscle}",
                        style = MaterialTheme.typography.labelSmall, color = Primary)
                    // "Última vez" (roadmap 1.3)
                    lastSession?.firstOrNull()?.let { lastSet ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Última vez: ${formatWeight(lastSet.weightKg)}kg × ${lastSet.reps}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = toggleExpanded) {
                        Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onRemoveExercise) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
            AnimatedVisibility(visible = isExpanded) {
                Column {
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
                            onDelete = { if (workoutExercise.sets.size > 1) onRemoveSet(setIdx) },
                            onRpeChange = onUpdateRpe?.let { cb -> { rpe: Int? -> cb(setIdx, rpe) } })
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Adicionar Série")
                    }

                    // Notes per exercise (roadmap 2.5)
                    if (onUpdateNotes != null) {
                        if (!showNotesField) {
                            TextButton(onClick = { showNotesField = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.EditNote, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text("Adicionar nota", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Spacer(Modifier.height(4.dp))
                            var notesText by remember(workoutExercise.id, workoutExercise.exerciseId) {
                                mutableStateOf(workoutExercise.notes)
                            }
                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it; onUpdateNotes(it) },
                                label = { Text("Nota (ex: técnica, ajuste do banco)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1, maxLines = 3
                            )
                        }
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
    onToggle: () -> Unit, onDelete: () -> Unit,
    onRpeChange: ((Int?) -> Unit)? = null
) {
    var repsText   by remember(set.reps) { mutableStateOf(set.reps.toString()) }
    var weightText by remember(set.weightKg) {
        mutableStateOf(if (set.weightKg == set.weightKg.toLong().toFloat()) set.weightKg.toLong().toString() else set.weightKg.toString())
    }
    var showRpeMenu by remember { mutableStateOf(false) }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (set.completed && showCompleted) Primary.copy(alpha = 0.1f) else Color.Transparent)
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

        // Optional RPE picker (roadmap 3.6) - only shown once the set is completed
        if (showCompleted && set.completed && onRpeChange != null) {
            Box {
                Row(
                    Modifier.padding(start = 40.dp, bottom = 4.dp).clip(RoundedCornerShape(6.dp))
                        .background(Accent.copy(alpha = 0.12f))
                        .clickable { showRpeMenu = true }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        set.rpe?.let { "RPE $it" } ?: "Adicionar RPE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent
                    )
                }
                DropdownMenu(expanded = showRpeMenu, onDismissRequest = { showRpeMenu = false }) {
                    (6..10).forEach { rpeValue ->
                        DropdownMenuItem(text = { Text("RPE $rpeValue") }, onClick = { onRpeChange(rpeValue); showRpeMenu = false })
                    }
                    DropdownMenuItem(text = { Text("Limpar") }, onClick = { onRpeChange(null); showRpeMenu = false })
                }
            }
        }
    }
}

private fun formatWeight(w: Float): String =
    if (w == w.toLong().toFloat()) w.toLong().toString() else w.toString()
