package com.fittrack.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads each exercise's demonstration image once during the Wger sync and stores it in
 * the app's private storage, so the exercise library works with zero network access after
 * the initial download — matching ExerciseDB's GIFs being replaced by static photos here,
 * this is the one place in the app that still needs to reach the network at all once synced.
 */
@Singleton
class ExerciseImageDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val imagesDir: File by lazy {
        File(context.filesDir, "exercise_images").apply { mkdirs() }
    }

    /** Downloads [imageUrl] for [exerciseId] if not already present locally.
     *  Returns a "file://" URI Coil can load directly, or "" if there's no image / it failed
     *  (the UI already falls back to a generic icon whenever gifUrl is blank). */
    suspend fun download(exerciseId: Int, imageUrl: String): String = withContext(Dispatchers.IO) {
        if (imageUrl.isBlank()) return@withContext ""
        val extension = imageUrl.substringAfterLast('.', "jpg").substringBefore('?').take(4)
        val file = File(imagesDir, "$exerciseId.$extension")
        if (file.exists() && file.length() > 0) return@withContext "file://${file.absolutePath}"

        try {
            val request = Request.Builder().url(imageUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ""
                val body = response.body ?: return@withContext ""
                body.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
            "file://${file.absolutePath}"
        } catch (_: Exception) {
            // Missing image or a flaky connection mid-sync shouldn't fail the whole sync —
            // this exercise just falls back to the generic icon like any exercise with no photo.
            if (file.exists()) file.delete()
            ""
        }
    }

    /** Total bytes used by downloaded exercise images, for a "biblioteca offline" size hint. */
    fun totalSizeBytes(): Long = imagesDir.listFiles()?.sumOf { it.length() } ?: 0L
}
