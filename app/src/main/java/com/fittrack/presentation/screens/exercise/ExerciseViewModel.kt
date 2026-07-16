package com.fittrack.presentation.screens.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrack.data.repository.ExerciseRepository
import com.fittrack.domain.model.Exercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val repository: ExerciseRepository
) : ViewModel() {

    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises: StateFlow<List<Exercise>> = _exercises

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedBodyPart = MutableStateFlow<String?>(null)
    val selectedBodyPart: StateFlow<String?> = _selectedBodyPart

    private val _bodyParts = MutableStateFlow<List<String>>(emptyList())
    val bodyParts: StateFlow<List<String>> = _bodyParts

    private val _selectedExercise = MutableStateFlow<Exercise?>(null)
    val selectedExercise: StateFlow<Exercise?> = _selectedExercise

    // -- Wger sync (metadata only, images are lazy-loaded) ----------------
    // The library metadata is downloaded once and stored locally.
    // Images are fetched on-demand when the user clicks an exercise (lazy loading).
    // After that, everything reads straight from Room, no network involved.
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _syncProgress = MutableStateFlow(0 to 0) // (processed, total)
    val syncProgress: StateFlow<Pair<Int, Int>> = _syncProgress

    // Only meaningfully different from _isSyncing at startup: distinguishes "we've never
    // synced, so the library is empty" from "synced already, just showing what's there".
    private val _hasSyncedLibrary = MutableStateFlow(true)
    val hasSyncedLibrary: StateFlow<Boolean> = _hasSyncedLibrary

    // -- Image downloading (lazy loading on click) -------------------------
    private val _isDownloadingImage = MutableStateFlow(false)
    val isDownloadingImage: StateFlow<Boolean> = _isDownloadingImage

    // -- Favorites (roadmap 7.1) -------------------------------------------------
    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly

    val favoriteIds: StateFlow<Set<String>> = repository.getFavorites()
        .map { list -> list.map { it.exerciseId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // Filtered exercises
    val filteredExercises: StateFlow<List<Exercise>> = combine(
        _exercises, _searchQuery, _selectedBodyPart, _showFavoritesOnly, favoriteIds
    ) { exercises, query, bodyPart, favoritesOnly, favIds ->
        exercises.filter { ex ->
            (query.isBlank() || ex.name.contains(query, ignoreCase = true) ||
                    ex.target.contains(query, ignoreCase = true)) &&
                    (bodyPart == null || ex.bodyPart.equals(bodyPart, ignoreCase = true)) &&
                    (!favoritesOnly || favIds.contains(ex.id))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // Always keep the built-in catalog up to date first (cheap, idempotent — see
            // seedLocalLibraryIfEmpty) so search/filter never come up empty, even for people
            // who already had an older/smaller version of this fallback list stored.
            repository.seedLocalLibraryIfEmpty()
            val synced = repository.hasSyncedLibrary()
            _hasSyncedLibrary.value = synced
            loadFromLocalLibrary()
            if (!synced) syncLibrary()
        }
    }

    /** Downloads (or re-downloads) the full Portuguese exercise library from Wger.
     *  LAZY LOADING: Images are NOT downloaded here — only metadata.
     *  Images are fetched on-demand in downloadExerciseImage() when user clicks. */
    fun syncLibrary() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _error.value = null
            _syncProgress.value = 0 to 0
            repository.syncAllExercisesFromWger { processed, total -> _syncProgress.value = processed to total }
                .onSuccess {
                    _hasSyncedLibrary.value = true
                    loadFromLocalLibrary()
                }
                .onFailure { e ->
                    _error.value = "Não foi possível transferir a biblioteca: ${e.message ?: e.javaClass.simpleName}"
                }
            _isSyncing.value = false
        }
    }

    private fun loadFromLocalLibrary() {
        viewModelScope.launch {
            repository.getExercises().onSuccess { _exercises.value = it }
            repository.getBodyParts().onSuccess { _bodyParts.value = it }
        }
    }

    fun filterByBodyPart(bodyPart: String?) {
        _selectedBodyPart.value = bodyPart
        viewModelScope.launch {
            repository.getExercises(bodyPart).onSuccess { _exercises.value = it }
        }
    }

    fun toggleFavoritesOnly() { _showFavoritesOnly.value = !_showFavoritesOnly.value }

    fun toggleFavorite(exercise: Exercise) {
        viewModelScope.launch {
            val isFav = favoriteIds.value.contains(exercise.id)
            repository.toggleFavorite(exercise, isFav)
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                repository.getExercises(_selectedBodyPart.value).onSuccess { _exercises.value = it }
            } else {
                repository.searchExercises(query).onSuccess { _exercises.value = it }
            }
        }
    }

    fun selectExercise(exercise: Exercise) { _selectedExercise.value = exercise }
    fun clearSelection() { _selectedExercise.value = null }

    // -- Lazy image loading (called when user opens detail sheet) ----------
    /** Download exercise image on-demand. Called when ExerciseDetailSheet opens.
     *  If image is already cached, returns immediately (no network).
     *  Otherwise fetches from Wger and saves locally. */
    fun downloadExerciseImageIfNeeded(exerciseId: String) {
        viewModelScope.launch {
            _isDownloadingImage.value = true
            repository.downloadExerciseImage(exerciseId)
                .onSuccess { wasDownloaded ->
                    if (wasDownloaded) {
                        // Reload exercise from DB to get updated gifUrl with local path
                        repository.getExerciseById(exerciseId).onSuccess { updated ->
                            _selectedExercise.value = updated
                        }
                    }
                }
                .onFailure { e ->
                    _error.value = "Não foi possível carregar a imagem: ${e.message ?: e.javaClass.simpleName}"
                }
            _isDownloadingImage.value = false
        }
    }
}
