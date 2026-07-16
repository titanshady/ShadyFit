package com.fittrack.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fittrack.presentation.screens.analytics.AnalyticsViewModel
import com.fittrack.presentation.screens.nutrition.NutritionViewModel
import com.fittrack.presentation.screens.workout.WorkoutViewModel
import com.fittrack.presentation.theme.*
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onViewAnalytics: () -> Unit,
    workoutVm: WorkoutViewModel = hiltViewModel(),
    nutritionVm: NutritionViewModel = hiltViewModel(),
    analyticsVm: AnalyticsViewModel = hiltViewModel()
) {
    val workouts by workoutVm.workouts.collectAsState()
    val totalWorkouts by analyticsVm.totalWorkouts.collectAsState()
    val totalVolume by analyticsVm.totalVolume.collectAsState()
    val dailyNutrition by nutritionVm.dailyNutrition.collectAsState()
    val profile by nutritionVm.profile.collectAsState()
    val recentWorkouts = workouts.take(3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // Header
        Text("ShadyFit", style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Text("Bom treino!", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(20.dp))

        // Start Workout CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(listOf(PrimaryDark, Primary))
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Iniciar Treino", style = MaterialTheme.typography.titleLarge,
                        color = OnPrimary, fontWeight = FontWeight.Bold)
                    Text("Cria uma nova sessão de treino", style = MaterialTheme.typography.bodySmall,
                        color = OnPrimary.copy(alpha = 0.75f))
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onStartWorkout,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OnPrimary, contentColor = Primary)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Começar", fontWeight = FontWeight.SemiBold)
                    }
                }
                Icon(
                    Icons.Filled.FitnessCenter, null,
                    tint = OnPrimary.copy(alpha = 0.18f),
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Stats Row
        Text("Estatísticas", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Treinos", "$totalWorkouts", Icons.Filled.FitnessCenter, Primary, Modifier.weight(1f))
            StatCard("Volume Total", "${String.format("%.0f", totalVolume)} kg", Icons.Filled.Scale, Accent, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Calorias Hoje", "${String.format("%.0f", dailyNutrition.calories)} kcal",
                Icons.Filled.LocalFireDepartment, Color(0xFFFF6B35), Modifier.weight(1f))
            StatCard("Proteína Hoje", "${String.format("%.0f", dailyNutrition.protein)} g",
                Icons.Filled.Egg, Color(0xFF9B59B6), Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // Today's calories progress
        profile?.let { p ->
            Text("Progresso de Hoje", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            CaloriesProgressCard(
                consumed = dailyNutrition.calories,
                goal = p.goalCalories.toFloat(),
                protein = dailyNutrition.protein,
                proteinGoal = p.goalProtein.toFloat(),
                carbs = dailyNutrition.carbs,
                carbsGoal = p.goalCarbs.toFloat(),
                fat = dailyNutrition.fat,
                fatGoal = p.goalFat.toFloat()
            )
            Spacer(Modifier.height(20.dp))
        }

        // Recent Workouts
        if (recentWorkouts.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Últimos Treinos", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onViewAnalytics) { Text("Ver tudo") }
            }
            Spacer(Modifier.height(8.dp))
            recentWorkouts.forEach { workout ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVar),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FitnessCenter, null, tint = Primary,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(workout.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${workout.exercises.size} exercícios • ${workout.durationMinutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            workout.date.format(DateTimeFormatter.ofPattern("dd/MM")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StatCard(
    label: String, value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceVar),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CaloriesProgressCard(
    consumed: Float, goal: Float,
    protein: Float, proteinGoal: Float,
    carbs: Float, carbsGoal: Float,
    fat: Float, fatGoal: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVar),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${consumed.toInt()} kcal", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = Accent)
                Text("/ ${goal.toInt()} kcal", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (consumed / goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Accent,
                trackColor = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MacroChip("P", protein, proteinGoal, Color(0xFF9B59B6))
                MacroChip("H", carbs, carbsGoal, Color(0xFF3498DB))
                MacroChip("G", fat, fatGoal, Color(0xFFE74C3C))
            }
        }
    }
}

@Composable
private fun MacroChip(label: String, value: Float, goal: Float, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$label", style = MaterialTheme.typography.labelSmall, color = color)
        Text("${value.toInt()}g", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("/${goal.toInt()}g", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
