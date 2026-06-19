package com.cursokotlin.contactos_app.data.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cursokotlin.contactos_app.data.local.AppDatabase
import com.cursokotlin.contactos_app.data.model.SyncAction
import com.cursokotlin.contactos_app.data.model.SyncStatus
import com.cursokotlin.contactos_app.data.network.RetrofitInstance
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.contactDao()
        val api = RetrofitInstance.api

        return try {
            val pendingItems = dao.getPendingQueue()

            for (item in pendingItems) {
                val contact = dao.getContactById(item.contactLocalId) ?: continue

                try {
                    val imagePart = buildImagePart(contact.photoUri)

                    when (item.action) {
                        SyncAction.PENDING_CREATE -> {
                            val nameBody = contact.name.toRequestBody("text/plain".toMediaTypeOrNull())
                            val phoneBody = contact.phone.toRequestBody("text/plain".toMediaTypeOrNull())
                            val emailBody = contact.email.toRequestBody("text/plain".toMediaTypeOrNull())

                            val response = api.createContact(nameBody, phoneBody, emailBody, imagePart)

                            if (response.isSuccessful) {
                                val remoteId = response.body()?.data?.id
                                dao.updateSyncStatus(contact.id, SyncStatus.SYNCED, remoteId)
                                dao.deleteSyncQueueItem(item.id)
                            } else {
                                dao.updateSyncQueueItem(item.id, "HTTP ${response.code()}", "ERROR")
                                dao.updateSyncStatusOnly(contact.id, SyncStatus.ERROR)
                            }
                        }

                        SyncAction.PENDING_UPDATE -> {
                            val remoteId = contact.remoteId ?: continue
                            val nameBody = contact.name.toRequestBody("text/plain".toMediaTypeOrNull())
                            val phoneBody = contact.phone.toRequestBody("text/plain".toMediaTypeOrNull())
                            val emailBody = contact.email.toRequestBody("text/plain".toMediaTypeOrNull())
                            val methodBody = "PUT".toRequestBody("text/plain".toMediaTypeOrNull())

                            val response = api.updateContact(remoteId, nameBody, phoneBody, emailBody, methodBody, imagePart)

                            if (response.isSuccessful) {
                                dao.updateSyncStatusOnly(contact.id, SyncStatus.SYNCED)
                                dao.deleteSyncQueueItem(item.id)
                            } else {
                                dao.updateSyncQueueItem(item.id, "HTTP ${response.code()}", "ERROR")
                                dao.updateSyncStatusOnly(contact.id, SyncStatus.ERROR)
                            }
                        }

                        SyncAction.PENDING_DELETE -> {
                            val remoteId = contact.remoteId
                            if (remoteId != null) {
                                val response = api.deleteContact(remoteId)
                                if (response.isSuccessful || response.code() == 404) {
                                    dao.deleteContact(contact)
                                    dao.deleteSyncQueueItem(item.id)
                                } else {
                                    dao.updateSyncQueueItem(item.id, "HTTP ${response.code()}", "ERROR")
                                }
                            } else {
                                dao.deleteContact(contact)
                                dao.deleteSyncQueueItem(item.id)
                            }
                        }
                    }
                } catch (e: Exception) {
                    dao.updateSyncQueueItem(item.id, e.message ?: "Error desconocido", "ERROR")
                    dao.updateSyncStatusOnly(contact.id, SyncStatus.ERROR)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    // Convierte el Uri de la foto (content://) en un archivo temporal y lo empaqueta como multipart
    private fun buildImagePart(photoUriString: String?): MultipartBody.Part? {
        if (photoUriString.isNullOrBlank()) return null

        return try {
            val uri = Uri.parse(photoUriString)
            val inputStream = applicationContext.contentResolver.openInputStream(uri) ?: return null

            val mimeType = applicationContext.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = when {
                mimeType.contains("png") -> "png"
                mimeType.contains("webp") -> "webp"
                else -> "jpg"
            }

            val tempFile = File.createTempFile("upload_", ".$extension", applicationContext.cacheDir)
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            val requestFile: RequestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("image", tempFile.name, requestFile)
        } catch (e: Exception) {
            null
        }
    }
}