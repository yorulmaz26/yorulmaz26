package com.kuaforumcepte.app.data.registration

import android.content.Context
import com.kuaforumcepte.app.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Taslağı ve işlenmiş görselleri uygulamanın özel dizininde tutar (yedeklenmez). */
@Singleton
class OwnerDraftStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun dir(uid: String) = File(context.filesDir, "registration/$uid").apply { mkdirs() }
    private fun file(uid: String) = File(dir(uid), "owner_draft.json")
    fun imageDir(uid: String): File = File(dir(uid), "images").apply { mkdirs() }

    suspend fun loadOrCreate(uid: String): OwnerDraft = withContext(io) {
        val f = file(uid)
        val existing = if (f.exists()) runCatching { json.decodeFromString(OwnerDraft.serializer(), f.readText()) }.getOrNull() else null
        existing?.takeIf { it.uid == uid } ?: OwnerDraft(draftId = UUID.randomUUID().toString(), uid = uid)
    }

    suspend fun save(draft: OwnerDraft) = withContext(io) {
        val f = file(draft.uid)
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeText(json.encodeToString(OwnerDraft.serializer(), draft))
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    suspend fun clear(uid: String) = withContext(io) { dir(uid).deleteRecursively() }
}
