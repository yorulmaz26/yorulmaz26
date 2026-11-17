package com.kuaforumcepte.app.data.media

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.kuaforumcepte.app.core.data.AppException
import dagger.Lazy
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageUploader @Inject constructor(private val storage: Lazy<FirebaseStorage>) {

    /** uploads/{uid}/pending/{name} alanına JPEG yükler; aynı ad tekrar yüklenirse üzerine yazılır. */
    suspend fun uploadPending(uid: String, file: File, name: String, onProgress: (Float) -> Unit = {}): String {
        val path = "uploads/$uid/pending/$name"
        try {
            val task = storage.get().reference.child(path).putFile(
                Uri.fromFile(file),
                StorageMetadata.Builder().setContentType("image/jpeg").build(),
            )
            task.addOnProgressListener { s ->
                if (s.totalByteCount > 0) onProgress(s.bytesTransferred.toFloat() / s.totalByteCount)
            }
            task.await()
            return path
        } catch (t: Throwable) {
            throw AppException.from(t)
        }
    }
}
