package com.fittrack.presentation.screens.exercise

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.fittrack.domain.model.Exercise
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
    val showFavoritesOnly by exerciseVm.showFavoritesOnly.collectAsState()
    val favoriteIds by exerciseVm.favoriteIds.collectAsState()

    selectedExercise?.let { exercise ->
        ExerciseDetailSheet(
            exercise = exercise,
            isFavorite = favoriteIds.contains(exercise.id),
            onToggleFavorite = { exerciseVm.toggleFavorite(exercise) },
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
                title = { Text("Biblioteca de Exercícios", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = exerciseVm::search,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Pesquisar exercícios...") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank())
                        IconButton(onClick = { exerciseVm.search("") }) { Icon(Icons.Filled.Clear, null) }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Body part filter chips (roadmap 7.1: "Favoritos" is a pseudo body-part filter)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = showFavoritesOnly,
                        onClick = { exerciseVm.toggleFavoritesOnly() },
                        leadingIcon = { Icon(Icons.Filled.Star, null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Favoritos") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent, selectedLabelColor = OnPrimary
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedBodyPart == null,
                        onClick = { exerciseVm.filterByBodyPart(null) },
                        label = { Text("Todos") }
                    )
                }
                items(bodyParts) { part ->
                    FilterChip(
                        selected = selectedBodyPart == part,
                        onClick = { exerciseVm.filterByBodyPart(if (selectedBodyPart == part) null else part) },
                        label = { Text(part.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = OnPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            error?.let { msg ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
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
            } else if (showFavoritesOnly && exercises.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.StarBorder, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Ainda sem favoritos", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Toca na estrela num exercício para o adicionar aqui.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(exercises, key = { it.id }) { exercise ->
                        ExerciseListItem(
                            exercise = exercise,
                            isFavorite = favoriteIds.contains(exercise.id),
                            onClick = { exerciseVm.selectExercise(exercise) },
                            onToggleFavorite = { exerciseVm.toggleFavorite(exercise) }
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
private fun ExerciseListItem(
    exercise: Exercise, isFavorite: Boolean,
    onClick: () -> Unit, onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceVar),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // GIF thumbnail
            if (exercise.gifUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(exercise.gifUrl)
                        .decoderFactory(if (android.os.Build.VERSION.SDK_INT >= 28)
                            ImageDecoderDecoder.Factory() else GifDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = exercise.name,
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.FitnessCenter, null, tint = Primary, modifier = Modifier.size(28.dp))
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${exercise.bodyPart.replaceFirstChar { it.uppercase() }} • ${exercise.target}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Primary
                )
                if (exercise.equipment.isNotBlank()) {
                    Text(exercise.equipment.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, null,
                    tint = if (isFavorite) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailSheet(
    exercise: Exercise,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceVar) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, null,
                        tint = if (isFavorite) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = Primary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(exercise.bodyPart.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = Primary)
                }
                Surface(color = Secondary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(exercise.target.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = Secondary)
                }
            }

            Spacer(Modifier.height(16.dp))

            // GIF
            if (exercise.gifUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(exercise.gifUrl)
                        .decoderFactory(if (android.os.Build.VERSION.SDK_INT >= 28)
                            ImageDecoderDecoder.Factory() else GifDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = exercise.name,
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(16.dp))
            }

            // Secondary muscles
            if (exercise.secondaryMuscles.isNotEmpty()) {
                Text("Músculos secundários", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(exercise.secondaryMuscles.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } },
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }

            // Instructions
            if (exercise.instructions.isNotEmpty()) {
                Text("Como executar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                exercise.instructions.forEachIndexed { index, instruction ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Primary,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall,
                                    color = OnPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(instruction, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar ao Treino", fontWeight = FontWeight.Bold)
            }
        }
    }
}
