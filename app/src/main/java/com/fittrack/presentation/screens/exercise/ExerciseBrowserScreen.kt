package com.fittrack.presentation.screens.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.fittrack.domain.model.Exercise
import com.fittrack.presentation.components.ExerciseGifHero
import com.fittrack.presentation.components.ExerciseGifThumbnail
import com.fittrack.presentation.screens.workout.WorkoutViewModel
import com.fittrack.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseBrowserScreen(
    onNavigateBack: () -> Unit,
    onExerciseSelected: () -> Unit,
    workoutVm: WorkoutViewModel,                      // injected from parent graph
    exerciseVm: ExerciseViewModel = hiltViewModel()   // scoped to this screen only
) {
    val exercises by exerciseVm.filteredExercises.collectAsState()
    val isLoading by exerciseVm.isLoading.collectAsState()
    val error by exerciseVm.error.collectAsState()
    val bodyParts by exerciseVm.bodyParts.collectAsState()
    val selectedBodyPart by exerciseVm.selectedBodyPart.collectAsState()
    val searchQuery by exerciseVm.searchQuery.collectAsState()
    val selectedExercise by exerciseVm.selectedExercise.collectAsState()

    selectedExercise?.let { exercise ->
        ExerciseDetailSheet(
            exercise = exercise,
            onDismiss = { exerciseVm.clearSelection() },
            onAdd = {
                workoutVm.addExercise(exercise)
                exerciseVm.clearSelection()
                onExerciseSelected()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Biblioteca de Exercícios",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = exerciseVm::search,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Pesquisar exercícios...") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotBlank())
                        IconButton(onClick = { exerciseVm.search("") }) { Icon(Icons.Filled.Clear, null) }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = OutlineSubtle,
                    focusedBorderColor = Primary,
                    unfocusedContainerColor = SurfaceVar,
                    focusedContainerColor = SurfaceVar
                ),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // Body part filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedBodyPart == null,
                        onClick = { exerciseVm.filterByBodyPart(null) },
                        label = { Text("Todos") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceVar,
                            selectedContainerColor = PrimaryMuted,
                            selectedLabelColor = Primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = selectedBodyPart == null,
                            borderColor = OutlineSubtle, selectedBorderColor = Primary.copy(alpha = 0.4f)
                        )
                    )
                }
                items(bodyParts) { part ->
                    val isSelected = selectedBodyPart == part
                    FilterChip(
                        selected = isSelected,
                        onClick = { exerciseVm.filterByBodyPart(if (isSelected) null else part) },
                        label = { Text(part.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceVar,
                            selectedContainerColor = PrimaryMuted,
                            selectedLabelColor = Primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = isSelected,
                            borderColor = OutlineSubtle, selectedBorderColor = Primary.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            error?.let { msg ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (isLoading && exercises.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(Modifier.height(12.dp))
                        Text("A carregar exercícios...", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(exercises, key = { it.id }) { exercise ->
                        ExerciseListItem(
                            exercise = exercise,
                            onClick = { exerciseVm.selectExercise(exercise) }
                        )
                    }
                    if (isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExerciseListItem(exercise: Exercise, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVar),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseGifThumbnail(
                gifUrl = exercise.gifUrl,
                contentDescription = exercise.name,
                size = 72.dp
            )

            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${exercise.bodyPart.replaceFirstChar { it.uppercase() }} • ${exercise.target}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Primary
                )
                if (exercise.equipment.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(exercise.equipment.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailSheet(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onAdd: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)
        ) {
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = PrimaryMuted, shape = RoundedCornerShape(8.dp)) {
                    Text(exercise.bodyPart.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = Primary)
                }
                Surface(color = Secondary.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
                    Text(exercise.target.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = Secondary)
                }
            }

            Spacer(Modifier.height(18.dp))

            ExerciseGifHero(
                gifUrl = exercise.gifUrl,
                contentDescription = exercise.name,
                height = 260.dp
            )

            Spacer(Modifier.height(20.dp))

            // Secondary muscles
            if (exercise.secondaryMuscles.isNotEmpty()) {
                Text("Músculos secundários", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(exercise.secondaryMuscles.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } },
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
            }

            // Instructions
            if (exercise.instructions.isNotEmpty()) {
                Text("Como executar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                exercise.instructions.forEachIndexed { index, instruction ->
                    Row(
                        modifier = Modifier.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PrimaryMuted,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall,
                                    color = Primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(instruction, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar ao Treino", fontWeight = FontWeight.Bold)
            }
        }
    }
}
