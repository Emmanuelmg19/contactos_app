package com.cursokotlin.contactos_app.data.repository

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cursokotlin.contactos_app.data.local.dao.ContactDao
import com.cursokotlin.contactos_app.data.model.Contact
import com.cursokotlin.contactos_app.data.model.ContactImage
import com.cursokotlin.contactos_app.data.model.SyncAction
import com.cursokotlin.contactos_app.data.model.SyncQueue
import com.cursokotlin.contactos_app.data.model.SyncStatus
import com.cursokotlin.contactos_app.data.network.RetrofitInstance
import com.cursokotlin.contactos_app.data.worker.SyncWorker
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class ContactRepository(
    private val contactDao: ContactDao,
    private val context: Context
) {
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()
    val favoriteContacts: Flow<List<Contact>> = contactDao.getFavoriteContacts()

    suspend fun getContactById(id: Long): Contact? = contactDao.getContactById(id)
    suspend fun getContactByEmail(email: String): Contact? = contactDao.getContactByEmail(email)

    fun getImagesForContact(contactLocalId: Long): Flow<List<ContactImage>> =
        contactDao.getImagesForContact(contactLocalId)

    // Devuelve el id local del contacto recién insertado, para poder asociarle imágenes de inmediato
    suspend fun insert(contact: Contact): Long {
        val localId = contactDao.insertContact(
            contact.copy(syncStatus = SyncStatus.PENDING_CREATE)
        )
        contactDao.insertSyncQueue(
            SyncQueue(contactLocalId = localId, action = SyncAction.PENDING_CREATE)
        )
        scheduleSyncWorker()
        return localId
    }

    suspend fun update(contact: Contact) {
        try {
            contactDao.updateContact(
                contact.copy(
                    syncStatus = if (contact.remoteId != null) SyncStatus.PENDING_UPDATE
                    else SyncStatus.PENDING_CREATE,
                    updatedAt = System.currentTimeMillis()
                )
            )

            val action = if (contact.remoteId != null) SyncAction.PENDING_UPDATE
            else SyncAction.PENDING_CREATE

            contactDao.insertSyncQueue(
                SyncQueue(contactLocalId = contact.id, action = action)
            )

            scheduleSyncWorker()
        } catch (e: Exception) {
            android.util.Log.e("DEBUG_SYNC", "ERROR EN UPDATE: ${e.message}", e)
        }
    }

    suspend fun delete(contact: Contact) {
        contactDao.updateContact(
            contact.copy(isDeleted = true, syncStatus = SyncStatus.PENDING_DELETE)
        )
        contactDao.insertSyncQueue(
            SyncQueue(contactLocalId = contact.id, action = SyncAction.PENDING_DELETE)
        )
        contactDao.deleteImagesForContact(contact.id)
        scheduleSyncWorker()
    }

    // Guarda localmente varias imágenes seleccionadas para un contacto (pendientes de subir)
    suspend fun addLocalImagesForContact(contactLocalId: Long, uris: List<Uri>) {
        uris.forEach { uri ->
            contactDao.insertImage(
                ContactImage(
                    contactLocalId = contactLocalId,
                    localPath = uri.toString(),
                    syncStatus = SyncStatus.PENDING_CREATE
                )
            )
        }
        scheduleSyncWorker()
    }

    // Elimina una imagen: si ya estaba sincronizada, primero la borra del servidor
    suspend fun deleteImage(image: ContactImage) {
        try {
            if (image.remoteId != null) {
                val response = RetrofitInstance.api.deleteImage(image.remoteId)
                if (response.isSuccessful || response.code() == 404) {
                    contactDao.deleteImageById(image.localId)
                } else {
                    contactDao.updateImage(image.copy(isDeleted = true, syncStatus = SyncStatus.ERROR))
                }
            } else {
                // Nunca se subió al servidor, la borramos directo
                contactDao.deleteImageById(image.localId)
            }
        } catch (_: Exception) {
            // Sin conexión: la marcamos como borrada localmente para que desaparezca de la UI
            contactDao.updateImage(image.copy(isDeleted = true))
        }
    }

    // Sincronización manual: trae contactos desde la API (index ligero) y los guarda localmente
    suspend fun syncFromApi() {
        try {
            val response = RetrofitInstance.api.getContacts()
            if (response.isSuccessful) {
                val remoteContacts = response.body()?.data ?: return
                for (remote in remoteContacts) {
                    val existing = contactDao.getAllContactsList()
                        .find { it.remoteId == remote.id }
                    if (existing == null) {
                        contactDao.insertContact(
                            Contact(
                                remoteId   = remote.id,
                                name       = remote.name,
                                email      = remote.email ?: "",
                                phone      = remote.phone,
                                photoUri   = remote.thumbnail,
                                syncStatus = SyncStatus.SYNCED
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // Trae el detalle completo (show) desde la API y guarda todas las imágenes localmente
    suspend fun fetchContactDetail(contactLocalId: Long, remoteId: Long) {
        try {
            val response = RetrofitInstance.api.getContact(remoteId)
            if (response.isSuccessful) {
                val remote = response.body()?.data ?: return

                contactDao.deleteImagesForContact(contactLocalId)
                remote.images?.forEach { img ->
                    contactDao.insertImage(
                        ContactImage(
                            remoteId = img.id,
                            contactLocalId = contactLocalId,
                            remoteUrl = img.url,
                            syncStatus = SyncStatus.SYNCED
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}