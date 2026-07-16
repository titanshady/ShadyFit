package com.fittrack.presentation.screens.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fittrack.domain.model.FoodItem
import com.fittrack.domain.model.FoodLog
import com.fittrack.domain.model.MealType
import com.fittrack.domain.model.UserProfile
import com.fittrack.presentation.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(vm: NutritionViewModel = hiltViewModel()) {
    val selectedDate   by vm.selectedDate.collectAsState()
    val dailyNutrition by vm.dailyNutrition.collectAsState()
    val logsByMeal     by vm.logsByMeal.collectAsState()
    val profile        by vm.profile.collectAsState()
    val searchResults  by vm.searchResults.collectAsState()
    val isSearching    by vm.isSearching.collectAsState()
    val searchQuery    by vm.searchQuery.collectAsState()
    val searchError    by vm.searchError.collectAsState()

    var showAddFood    by remember { mutableStateOf(false) }
    var activeMealType by remember { mutableStateOf(MealType.LUNCH) }
    var showSettings   by remember { mutableStateOf(false) }

    if (showSettings && profile != null) {
        GoalSettingsDialog(
            profile = profile!!,
            onDismiss = { showSettings = false },
            onSave = { vm.updateProfile(it); showSettings = false }
        )
    }

    if (showAddFood) {
        FoodSearchSheet(
            mealType    = activeMealType,
            searchQuery = searchQuery,
            results     = searchResults,
            isSearching = isSearching,
            error       = searchError,
            onSearch    = vm::searchFood,
            onAdd       = { food, meal, grams ->
                vm.addFoodLog(food, meal, grams)
                showAddFood = false
                vm.clearSearch()
            },
            onDismiss   = { showAddFood = false; vm.clearSearch() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrição", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { activeMealType = MealType.LUNCH; showAddFood = true },
                containerColor = Primary, contentColor = OnPrimary
            ) { Icon(Icons.Filled.Add, null) }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date navigator
            item {
                DateNavigator(
                    date = selectedDate,
                    onPrev = { vm.setDate(selectedDate.minusDays(1)) },
                    onNext = { if (selectedDate < LocalDate.now()) vm.setDate(selectedDate.plusDays(1)) }
                )
            }

            // Calories ring + macros
            item {
                CaloriesSummaryCard(
                    nutrition = dailyNutrition,
                    profile   = profile
                )
            }

            // Macro progress bars
            profile?.let { p ->
                item {
                    MacroBreakdownCard(nutrition = dailyNutrition, profile = p)
                }
            }

            // Meal sections
            MealType.values().forEach { meal ->
                val logs = logsByMeal[meal] ?: emptyList()
                item {
                    MealSection(
                        mealType = meal,
                        logs     = logs,
                        onAdd    = { activeMealType = meal; showAddFood = true },
                        onDelete = vm::deleteFoodLog
                    )
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

// --- Date Navigator -----------------------------------------------------------

@Composable
private fun DateNavigator(date: LocalDate, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, null) }
        Text(
            if (date == LocalDate.now()) "Hoje"
            else date.format(DateTimeFormatter.ofPattern("EEE, dd MMM", java.util.Locale("pt", "PT"))),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onNext, enabled = date < LocalDate.now()) {
            Icon(Icons.Filled.ChevronRight, null,
                tint = if (date < LocalDate.now()) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline)
        }
    }
}

// --- Calories Summary ---------------------------------------------------------

@Composable
private fun CaloriesSummaryCard(nutrition: DailyNutrition, profile: UserProfile?) {
    val goal = profile?.goalCalories?.toFloat() ?: 2000f
    val remaining = (goal - nutrition.calories).coerceAtLeast(0f)
    val progress = (nutrition.calories / goal).coerceIn(0f, 1f)

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceVar),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Circular progress
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(110.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(110.dp),
                    color = if (progress >= 1f) MaterialTheme.colorScheme.error else Accent,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${nutrition.calories.toInt()}", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold, color = Accent)
                    Text("kcal", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CalItem("Objetivo",  "${goal.toInt()} kcal", Primary)
                CalItem("Consumido", "${nutrition.calories.toInt()} kcal", Accent)
                CalItem("Restante",  "${remaining.toInt()} kcal",
                    if (remaining == 0f) MaterialTheme.colorScheme.error else Color(0xFF2ECC71))
            }
        }
    }
}

@Composable
private fun CalItem(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

// --- Macro Breakdown ---------------------------------------------------------

@Composable
private fun MacroBreakdownCard(nutrition: DailyNutrition, profile: UserProfile) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceVar),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Macronutrientes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            MacroBar("Proteína", nutrition.protein, profile.goalProtein.toFloat(), Color(0xFF9B59B6))
            MacroBar("Hidratos", nutrition.carbs,   profile.goalCarbs.toFloat(),   Color(0xFF3498DB))
            MacroBar("Gordura",  nutrition.fat,     profile.goalFat.toFloat(),     Color(0xFFE74C3C))
            MacroBar("Fibra",    nutrition.fiber,   profile.goalFiber.toFloat(),   Color(0xFF2ECC71))
        }
    }
}

@Composable
private fun MacroBar(label: String, current: Float, goal: Float, color: Color) {
    val progress = (current / goal).coerceIn(0f, 1f)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${current.toInt()} / ${goal.toInt()} g",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    }
}

// --- Meal Section -------------------------------------------------------------

@Composable
private fun MealSection(
    mealType: MealType,
    logs: List<FoodLog>,
    onAdd: () -> Unit,
    onDelete: (FoodLog) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val mealCalories = logs.sumOf { it.calories.toDouble() }.toFloat()

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceVar),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header row
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val mealIcon = when (mealType) {
                        MealType.BREAKFAST -> Icons.Filled.WbSunny
                        MealType.LUNCH     -> Icons.Filled.LunchDining
                        MealType.DINNER    -> Icons.Filled.DinnerDining
                        MealType.SNACK     -> Icons.Filled.Cookie
                    }
                    Icon(mealIcon, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(mealType.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (mealCalories > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("${mealCalories.toInt()} kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row {
                    IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Add, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    if (logs.isEmpty()) {
                        Text("Nenhum alimento registado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                    } else {
                        Spacer(Modifier.height(8.dp))
                        logs.forEach { log ->
                            FoodLogItem(log = log, onDelete = { onDelete(log) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodLogItem(log: FoodLog, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(log.foodName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text("${log.grams.toInt()}g • P:${log.protein.toInt()}g H:${log.carbs.toInt()}g G:${log.fat.toInt()}g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${log.calories.toInt()} kcal",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Accent)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Delete, null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp))
        }
    }
}

// --- Food Search Sheet --------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSearchSheet(
    mealType: MealType,
    searchQuery: String,
    results: List<FoodItem>,
    isSearching: Boolean,
    error: String?,
    onSearch: (String) -> Unit,
    onAdd: (FoodItem, MealType, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFood by remember { mutableStateOf<FoodItem?>(null) }
    var grams by remember { mutableStateOf("100") }
    var selectedMeal by remember { mutableStateOf(mealType) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVar,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text("Adicionar Alimento", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // Meal type selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.values().forEach { meal ->
                    FilterChip(
                        selected = selectedMeal == meal,
                        onClick  = { selectedMeal = meal },
                        label    = { Text(meal.label, fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary, selectedLabelColor = OnPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Pesquisar alimento (ex: frango, arroz...)") },
                leadingIcon  = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (isSearching) CircularProgressIndicator(Modifier.size(20.dp), color = Primary, strokeWidth = 2.dp)
                    else if (searchQuery.isNotBlank())
                        IconButton(onClick = { onSearch("") }) { Icon(Icons.Filled.Clear, null) }
                },
                shape    = RoundedCornerShape(12.dp),
                singleLine = true
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }

            selectedFood?.let { food ->
                Spacer(Modifier.height(12.dp))
                FoodPortionSelector(
                    food  = food,
                    grams = grams,
                    onGramsChange = { grams = it },
                    onAdd = {
                        val g = grams.toFloatOrNull() ?: 100f
                        onAdd(food, selectedMeal, g)
                    },
                    onClear = { selectedFood = null }
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.id }) { food ->
                    FoodSearchItem(food = food, onClick = { selectedFood = food; grams = food.servingSize.toInt().toString() })
                }
                if (results.isEmpty() && searchQuery.length >= 2 && !isSearching) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhum resultado encontrado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }
}

@Composable
private fun FoodPortionSelector(
    food: FoodItem, grams: String,
    onGramsChange: (String) -> Unit, onAdd: () -> Unit, onClear: () -> Unit
) {
    val g = grams.toFloatOrNull() ?: 100f
    val ratio = g / 100f

    Card(colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(food.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Clear, null, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = grams,
                    onValueChange = onGramsChange,
                    label = { Text("Quantidade (g)") },
                    modifier = Modifier.width(140.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Column {
                    Text("${(food.calories * ratio).toInt()} kcal", fontWeight = FontWeight.Bold, color = Accent)
                    Text("P:${(food.protein * ratio).toInt()}g H:${(food.carbs * ratio).toInt()}g G:${(food.fat * ratio).toInt()}g",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp))
                Text("Adicionar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FoodSearchItem(food: FoodItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (food.imageUrl.isNotBlank()) {
                AsyncImage(model = food.imageUrl, contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                if (food.brand.isNotBlank())
                    Text(food.brand, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${food.calories.toInt()} kcal", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = Accent)
                Text("per 100g", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- Goal Settings Dialog -----------------------------------------------------

@Composable
private fun GoalSettingsDialog(profile: UserProfile, onDismiss: () -> Unit, onSave: (UserProfile) -> Unit) {
    var calories by remember { mutableStateOf(profile.goalCalories.toString()) }
    var protein  by remember { mutableStateOf(profile.goalProtein.toString()) }
    var carbs    by remember { mutableStateOf(profile.goalCarbs.toString()) }
    var fat      by remember { mutableStateOf(profile.goalFat.toString()) }
    var fiber    by remember { mutableStateOf(profile.goalFiber.toString()) }
    var name     by remember { mutableStateOf(profile.name) }
    var weight   by remember { mutableStateOf(profile.weightKg.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Objetivos Nutricionais") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalField("Nome", name) { name = it }
                GoalField("Peso (kg)", weight) { weight = it }
                GoalField("Calorias (kcal)", calories) { calories = it }
                GoalField("Proteína (g)", protein) { protein = it }
                GoalField("Hidratos (g)", carbs) { carbs = it }
                GoalField("Gordura (g)", fat) { fat = it }
                GoalField("Fibra (g)", fiber) { fiber = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(profile.copy(
                    name = name,
                    weightKg = weight.toFloatOrNull() ?: profile.weightKg,
                    goalCalories = calories.toIntOrNull() ?: profile.goalCalories,
                    goalProtein  = protein.toIntOrNull()  ?: profile.goalProtein,
                    goalCarbs    = carbs.toIntOrNull()    ?: profile.goalCarbs,
                    goalFat      = fat.toIntOrNull()      ?: profile.goalFat,
                    goalFiber    = fiber.toIntOrNull()    ?: profile.goalFiber
                ))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun GoalField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
