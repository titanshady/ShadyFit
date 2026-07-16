@file:OptIn(ExperimentalFoundationApi::class)

package com.fittrack.presentation.screens.workout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fittrack.domain.model.ExerciseSet
import com.fittrack.domain.model.WorkoutExercise
import com.fittrack.presentation.components.BodyFigureWidget
import com.fittrack.presentation.theme.*
import kotlinx.coroutines.launch

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
    val lastSessions    by vm.lastSessions.collectAsState()
    val estimatedCalories by vm.estimatedCalories.collectAsState()
    val restSoundEnabled by vm.restSoundEnabled.collectAsState()

    var showFinishDialog by remember { mutableStateOf(false) }
    var showBodyFigure   by remember { mutableStateOf(false) }
    // Only the first exercise starts expanded; the rest are collapsed until their turn.
    var expandedExerciseIndex by remember { mutableIntStateOf(0) }

    // Roadmap 1.1/1.2: modo foco — horizontal pager, one exercise at a time, with
    // Próximo/Anterior navigation. Off by default; toggled from the top bar.
    var focusMode by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { exercises.size })
    val coroutineScope = rememberCoroutineScope()

    // Keep list-mode's "expanded" index and the pager's current page in sync, so
    // switching between the two modes lands on the same exercise either way.
    LaunchedEffect(focusMode) {
        if (focusMode && expandedExerciseIndex >= 0 && expandedExerciseIndex < exercises.size) {
            pagerState.scrollToPage(expandedExerciseIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> expandedExerciseIndex = page }
    }

    // Shared by both list mode and modo foco: toggles the set and, if it was the last
    // set of the exercise, auto-advances to the next one (expanding it / paging to it).
    fun handleToggleSet(index: Int, setIdx: Int) {
        val ex = exercises[index]
        val wasCompleted = ex.sets[setIdx].completed
        val isLastSet = setIdx == ex.sets.size - 1
        vm.toggleSetCompleted(index, setIdx)
        if (!wasCompleted && isLastSet) {
            val nextIndex = if (index + 1 < exercises.size) index + 1 else -1
            expandedExerciseIndex = nextIndex
            if (nextIndex >= 0) {
                coroutineScope.launch { pagerState.animateScrollToPage(nextIndex) }
            }
        }
    }

    // Load template and start timer when screen opens
    LaunchedEffect(workoutId) {
        vm.loadSessionFromTemplate(workoutId)
        vm.startSessionTimer()
    }

    // Cleanup timer if we leave
    DisposableEffect(Unit) {
        onDispose { vm.stopSessionTimer() }
    }

    // Keep the screen on during an active session (roadmap 1.6) — nothing is more
    // annoying than the phone locking mid-set while your hands are on the bar.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Vibrate when the rest countdown reaches zero (roadmap 1.4)
    // + optional sound cue, if the user enabled it (roadmap 1.5)
    var previousCountdown by remember { mutableIntStateOf(0) }
    LaunchedEffect(restCountdown) {
        if (previousCountdown > 0 && restCountdown == 0) {
            vibrateOnce(context)
            if (restSoundEnabled) playRestEndTone()
        }
        previousCountdown = restCountdown
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Terminar Treino?") },
            text = {
                val completedSets = exercises.sumOf { ex -> ex.sets.count { it.completed } }
                val totalSets     = exercises.sumOf { it.sets.size }
                Column {
                    Text("Séries completadas: $completedSets / $totalSets\nDuração: ${formatDuration(durationSeconds)}")
                    if (estimatedCalories > 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFFF7A00), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("~$estimatedCalories kcal estimadas", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.finishSession(workoutId) { achievedPr ->
                            if (achievedPr) vibrateCelebration(context) else vibrateOnce(context)
                            onFinish()
                        }
                    },
                    colors  = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("Terminar") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("Continuar") }
            }
        )
    }

    val completedSets = exercises.sumOf { ex -> ex.sets.count { it.completed } }
    val totalSets     = exercises.sumOf { it.sets.size }
    val topProgress   = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f

    Scaffold(
        topBar = {
            Column {
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
                        // Roadmap 1.1/1.2: toggle between list view and modo foco
                        IconButton(onClick = { focusMode = !focusMode }) {
                            Icon(
                                if (focusMode) Icons.Filled.ViewList else Icons.Filled.CenterFocusStrong,
                                if (focusMode) "Ver lista" else "Modo foco",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        // Roadmap 1.5: toggle for the optional rest-end sound
                        IconButton(onClick = { vm.toggleRestSound() }) {
                            Icon(
                                if (restSoundEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                                "Som ao terminar descanso",
                                tint = if (restSoundEnabled) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showFinishDialog = true }) {
                            Icon(Icons.Filled.CheckCircle, "Terminar", tint = Primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                // Progress bar pinned to the top, always visible while scrolling (roadmap 1.8)
                LinearProgressIndicator(
                    progress   = { topProgress },
                    modifier   = Modifier.fillMaxWidth().height(3.dp),
                    color      = Primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            }
        }
    ) { padding ->
        if (focusMode) {
            FocusModeContent(
                modifier         = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                exercises        = exercises,
                pagerState       = pagerState,
                coroutineScope   = coroutineScope,
                restCountdown    = restCountdown,
                onSkipRest       = { vm.skipRest() },
                lastSessions     = lastSessions,
                onAddSet         = { idx -> vm.addSet(idx) },
                onRemoveSet      = { idx, setIdx -> vm.removeSet(idx, setIdx) },
                onUpdateSet      = { idx, setIdx, reps, weight -> vm.updateSet(idx, setIdx, reps, weight) },
                onToggleSet      = { idx, setIdx -> handleToggleSet(idx, setIdx) },
                onRemoveExercise = { idx -> vm.removeExercise(idx) },
                onUpdateNotes    = { idx, notes -> vm.updateExerciseNotes(idx, notes) },
                onUpdateRpe      = { idx, setIdx, rpe -> vm.updateSetRpe(idx, setIdx, rpe) }
            )
            return@Scaffold
        }
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
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceVar), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Progresso", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$completedSets / $totalSets séries", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress    = { topProgress },
                            modifier    = Modifier.fillMaxWidth().height(8.dp),
                            color       = Primary,
                            trackColor  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            strokeCap   = StrokeCap.Round
                        )
                        if (estimatedCalories > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFFF7A00), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("~$estimatedCalories kcal", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Exercise cards with completion toggles
            // Roadmap: only one exercise expanded at a time — starts on the first, and
            // auto-advances to the next when the last set of the current one is completed.
            itemsIndexed(exercises, key = { i, _ -> i }) { index, workoutExercise ->
                ExerciseCard(
                    workoutExercise  = workoutExercise,
                    index            = index,
                    showCompleted    = true,
                    onAddSet         = { vm.addSet(index) },
                    onRemoveSet      = { setIdx -> vm.removeSet(index, setIdx) },
                    onUpdateSet      = { setIdx, reps, weight -> vm.updateSet(index, setIdx, reps, weight) },
                    onToggleSet      = { setIdx -> handleToggleSet(index, setIdx) },
                    onRemoveExercise = { vm.removeExercise(index) },
                    lastSession      = lastSessions[workoutExercise.exerciseId],
                    onUpdateNotes    = { notes -> vm.updateExerciseNotes(index, notes) },
                    onUpdateRpe      = { setIdx, rpe -> vm.updateSetRpe(index, setIdx, rpe) },
                    gifSize          = 88.dp,
                    expanded         = index == expandedExerciseIndex,
                    onExpandedChange = { isExpanded -> expandedExerciseIndex = if (isExpanded) index else -1 }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusModeContent(
    modifier: Modifier,
    exercises: List<WorkoutExercise>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    restCountdown: Int,
    onSkipRest: () -> Unit,
    lastSessions: Map<String, List<ExerciseSet>>,
    onAddSet: (Int) -> Unit,
    onRemoveSet: (Int, Int) -> Unit,
    onUpdateSet: (Int, Int, Int, Float) -> Unit,
    onToggleSet: (Int, Int) -> Unit,
    onRemoveExercise: (Int) -> Unit,
    onUpdateNotes: (Int, String) -> Unit,
    onUpdateRpe: (Int, Int, Int?) -> Unit
) {
    if (exercises.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
        return
    }

    Column(modifier) {
        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(
            visible = restCountdown > 0,
            enter   = slideInVertically() + fadeIn(),
            exit    = slideOutVertically() + fadeOut()
        ) {
            Column {
                RestCountdownBanner(seconds = restCountdown, onSkip = onSkipRest)
                Spacer(Modifier.height(12.dp))
            }
        }

        // "Exercício X de N" indicator — the whole point of modo foco is knowing exactly
        // where you are in the workout without scrolling through everything else.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Exercício ${pagerState.currentPage + 1} de ${exercises.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            val completedInPage = exercises.getOrNull(pagerState.currentPage)?.sets?.count { it.completed } ?: 0
            val totalInPage = exercises.getOrNull(pagerState.currentPage)?.sets?.size ?: 0
            Text("$completedInPage / $totalInPage séries", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            val workoutExercise = exercises[page]
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ExerciseCard(
                    workoutExercise  = workoutExercise,
                    index            = page,
                    showCompleted    = true,
                    onAddSet         = { onAddSet(page) },
                    onRemoveSet      = { setIdx -> onRemoveSet(page, setIdx) },
                    onUpdateSet      = { setIdx, reps, weight -> onUpdateSet(page, setIdx, reps, weight) },
                    onToggleSet      = { setIdx -> onToggleSet(page, setIdx) },
                    onRemoveExercise = { onRemoveExercise(page) },
                    lastSession      = lastSessions[workoutExercise.exerciseId],
                    onUpdateNotes    = { notes -> onUpdateNotes(page, notes) },
                    onUpdateRpe      = { setIdx, rpe -> onUpdateRpe(page, setIdx, rpe) },
                    gifSize          = 140.dp,
                    expanded         = true,
                    onExpandedChange = null,
                    showExpandToggle = false
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Anterior/Próximo navigation (roadmap 1.2)
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick  = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                enabled  = pagerState.currentPage > 0,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Anterior")
            }
            Button(
                onClick  = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                enabled  = pagerState.currentPage < exercises.size - 1,
                modifier = Modifier.weight(1f).height(48.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Text("Próximo")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
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

/** Walks up the Context wrapper chain to find the hosting Activity, if any. */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun getVibrator(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
    manager?.defaultVibrator
} else {
    @Suppress("DEPRECATION")
    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
}

// Roadmap 1.5: short beep via ToneGenerator instead of SoundPool — no audio asset needed
// for a single simple cue, and it respects the notification volume stream.
private fun playRestEndTone() {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
    } catch (_: Exception) {
        // Some devices/emulators have no audio output available — fail silently,
        // the vibration cue still fires regardless.
    }
}

private fun vibrateOnce(context: Context) {
    val vibrator = getVibrator(context)
    if (vibrator?.hasVibrator() == true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }
}

// Roadmap 8.4: distinct celebratory pattern (three short pulses) when a set finishes
// with a new personal record, so it's felt differently from the plain rest-end buzz.
private fun vibrateCelebration(context: Context) {
    val vibrator = getVibrator(context)
    if (vibrator?.hasVibrator() == true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 120, 80, 120, 80, 200)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 120, 80, 120, 80, 200), -1)
        }
    }
}
