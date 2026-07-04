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
            // 1. Sincronizamos primero los contactos (crear/actualizar/borrar)
            val pendingItems = dao.getPendingQueue()

            for (item in pendingItems) {
                val contact = dao.getContactById(item.contactLocalId) ?: continue

                try {
                    val imageParts = buildImageParts(contact.photoUri)

                    when (item.action) {
                        SyncAction.PENDING_CREATE -> {
                            val nameBody = contact.name.toRequestBody("text/plain".toMediaTypeOrNull())
                            val phoneBody = contact.phone.toRequestBody("text/plain".toMediaTypeOrNull())
                            val emailBody = contact.email.toRequestBody("text/plain".toMediaTypeOrNull())

                            val response = api.createContact(nameBody, phoneBody, emailBody, imageParts)

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

                            val response = api.updateContact(remoteId, nameBody, phoneBody, emailBody, methodBody, imageParts)

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

            // 2. Sincronizamos las imágenes sueltas de la galería que quedaron pendientes
            //    (por ejemplo, varias imágenes seleccionadas en el formulario)
            val pendingImages = dao.getPendingImages()
            for (image in pendingImages) {
                val contact = dao.getContactById(image.contactLocalId) ?: continue
                val remoteId = contact.remoteId

                // Si el contacto todavía no tiene remoteId (sigue pendiente de crearse),
                // esperamos a la siguiente corrida del worker.
                if (remoteId == null) continue

                try {
                    val part = buildSingleImagePart(image.localPath)
                    if (part == null) continue

                    val response = api.addImages(remoteId, listOf(part))
                    if (response.isSuccessful) {
                        val newRemoteImageId = response.body()?.data?.images?.lastOrNull()?.id
                        dao.updateImageSyncStatus(image.localId, SyncStatus.SYNCED, newRemoteImageId)
                    } else {
                        dao.updateImageSyncStatusOnly(image.localId, SyncStatus.ERROR)
                    }
                } catch (e: Exception) {
                    dao.updateImageSyncStatusOnly(image.localId, SyncStatus.ERROR)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    // Convierte el Uri de la foto principal en un archivo temporal (una sola imagen, para el contacto)
    private fun buildImageParts(photoUriString: String?): List<MultipartBody.Part> {
        val part = buildSingleImagePart(photoUriString) ?: return emptyList()
        return listOf(part)
    }

    // Convierte cualquier Uri de imagen (content://) en un MultipartBody.Part listo para subir
    private fun buildSingleImagePart(uriString: String?): MultipartBody.Part? {
        if (uriString.isNullOrBlank()) return null
        // Si es una ruta remota (http...), no hay nada que subir, ya está en el servidor
        if (uriString.startsWith("http")) return null

        return try {
            val uri = Uri.parse(uriString)
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
            MultipartBody.Part.createFormData("images[]", tempFile.name, requestFile)
        } catch (e: Exception) {
            null
        }
    }
}