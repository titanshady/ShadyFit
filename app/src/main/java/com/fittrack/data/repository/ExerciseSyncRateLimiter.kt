package com.fittrack.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

private val Context.syncDataStore by preferencesDataStore("exercise_sync_config")

/** 
 * Intelligent rate limiter for Wger API with adaptive strategies:
 * - Foreground sync: 5 concurrent requests, 200ms between pages
 * - Background sync: 3 concurrent requests, 400ms between pages
 * - Monitors errors and adjusts delays automatically
 */
@Singleton
class ExerciseSyncRateLimiter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val LAST_FOREGROUND_SYNC_KEY = longPreferencesKey("last_fg_sync")
    private val LAST_BACKGROUND_SYNC_KEY = longPreferencesKey("last_bg_sync")
    private val LAST_IMAGE_DOWNLOAD_KEY = longPreferencesKey("last_image_download")
    private val CONSECUTIVE_ERRORS_KEY = intPreferencesKey("consecutive_errors")
    private val ADAPTIVE_DELAY_MS_KEY = intPreferencesKey("adaptive_delay_ms")

    // ========== FOREGROUND SYNC CONFIG (User clicked "Update") ==========
    // More aggressive: faster completion when user is waiting
    private val FOREGROUND_CONCURRENT_REQUESTS = 5
    private val FOREGROUND_MIN_DELAY_BETWEEN_PAGES_MS = 200L
    private val FOREGROUND_MIN_SYNC_INTERVAL_MS = 60_000L      // Wait 1 min between full syncs

    // ========== BACKGROUND SYNC CONFIG (Auto-sync) ==========
    // Conservative: doesn't stress the API, just fills the DB slowly
    private val BACKGROUND_CONCURRENT_REQUESTS = 3
    private val BACKGROUND_MIN_DELAY_BETWEEN_PAGES_MS = 400L
    private val BACKGROUND_MIN_SYNC_INTERVAL_MS = 300_000L     // Wait 5 min between full syncs

    // ========== IMAGE DOWNLOAD CONFIG (Lazy loading on click) ==========
    private val IMAGE_MIN_DELAY_BETWEEN_DOWNLOADS_MS = 500L

    // ========== ERROR HANDLING & BACKOFF ==========
    private val MAX_CONSECUTIVE_ERRORS = 3
    private val BACKOFF_DURATION_MS = 30_000L                   // 30 sec pause after 3 errors
    private var consecutiveErrors = 0
    private var lastErrorTime = 0L

    // ========== ADAPTIVE DELAY (increases on errors, resets on success) ==========
    private var currentAdaptiveDelayMs = 0L

    // --- Foreground Sync (User initiated) ---

    suspend fun canStartForegroundSync(): Boolean {
        if (shouldBackoff()) return false
        val lastSync = getLastForegroundSyncTime()
        val timeSinceLastSync = System.currentTimeMillis() - lastSync
        return timeSinceLastSync >= FOREGROUND_MIN_SYNC_INTERVAL_MS
    }

    suspend fun getForegroundSyncConfig(): SyncConfig = SyncConfig(
        concurrentRequests = FOREGROUND_CONCURRENT_REQUESTS,
        delayBetweenPagesMs = FOREGROUND_MIN_DELAY_BETWEEN_PAGES_MS + currentAdaptiveDelayMs,
        syncType = "foreground"
    )

    suspend fun recordForegroundSyncStart() {
        context.syncDataStore.edit { prefs ->
            prefs[LAST_FOREGROUND_SYNC_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun recordForegroundSyncSuccess() {
        consecutiveErrors = 0
        currentAdaptiveDelayMs = 0L
    }

    // --- Background Sync (Auto-sync) ---

    suspend fun canStartBackgroundSync(): Boolean {
        if (shouldBackoff()) return false
        val lastSync = getLastBackgroundSyncTime()
        val timeSinceLastSync = System.currentTimeMillis() - lastSync
        return timeSinceLastSync >= BACKGROUND_MIN_SYNC_INTERVAL_MS
    }

    suspend fun getBackgroundSyncConfig(): SyncConfig = SyncConfig(
        concurrentRequests = BACKGROUND_CONCURRENT_REQUESTS,
        delayBetweenPagesMs = BACKGROUND_MIN_DELAY_BETWEEN_PAGES_MS + currentAdaptiveDelayMs,
        syncType = "background"
    )

    suspend fun recordBackgroundSyncStart() {
        context.syncDataStore.edit { prefs ->
            prefs[LAST_BACKGROUND_SYNC_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun recordBackgroundSyncSuccess() {
        consecutiveErrors = 0
        currentAdaptiveDelayMs = 0L
    }

    // --- Image Download (Lazy loading) ---

    suspend fun canDownloadImage(): Boolean {
        if (shouldBackoff()) return false
        val lastDownload = getLastImageDownloadTime()
        val timeSinceLastDownload = System.currentTimeMillis() - lastDownload
        return timeSinceLastDownload >= IMAGE_MIN_DELAY_BETWEEN_DOWNLOADS_MS
    }

    suspend fun recordImageDownload() {
        context.syncDataStore.edit { prefs ->
            prefs[LAST_IMAGE_DOWNLOAD_KEY] = System.currentTimeMillis()
        }
    }

    // --- Error Handling & Backoff ---

    suspend fun recordError() {
        consecutiveErrors++
        lastErrorTime = System.currentTimeMillis()

        // Adaptive delay: increase on each error
        currentAdaptiveDelayMs = (consecutiveErrors * 100L).coerceAtMost(500L)

        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            // Pause all syncing for a bit to let Wger cool down
            context.syncDataStore.edit { prefs ->
                prefs[LAST_FOREGROUND_SYNC_KEY] = System.currentTimeMillis() + BACKOFF_DURATION_MS
                prefs[LAST_BACKGROUND_SYNC_KEY] = System.currentTimeMillis() + BACKOFF_DURATION_MS
            }
        }
    }

    suspend fun shouldBackoff(): Boolean {
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            val timeSinceError = System.currentTimeMillis() - lastErrorTime
            return timeSinceError < BACKOFF_DURATION_MS
        }
        return false
    }

    suspend fun getWaitTimeMs(): Long {
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            val timeSinceError = System.currentTimeMillis() - lastErrorTime
            return max(0, BACKOFF_DURATION_MS - timeSinceError)
        }
        return 0
    }

    // --- Private helpers ---

    private suspend fun getLastForegroundSyncTime(): Long {
        val prefs = context.syncDataStore.data.first()
        return prefs[LAST_FOREGROUND_SYNC_KEY] ?: 0L
    }

    private suspend fun getLastBackgroundSyncTime(): Long {
        val prefs = context.syncDataStore.data.first()
        return prefs[LAST_BACKGROUND_SYNC_KEY] ?: 0L
    }

    private suspend fun getLastImageDownloadTime(): Long {
        val prefs = context.syncDataStore.data.first()
        return prefs[LAST_IMAGE_DOWNLOAD_KEY] ?: 0L
    }

    data class SyncConfig(
        val concurrentRequests: Int,
        val delayBetweenPagesMs: Long,
        val syncType: String  // "foreground" or "background"
    )
}
