package com.cursokotlin.contactos_app.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cursokotlin.contactos_app.data.local.dao.ContactDao
import com.cursokotlin.contactos_app.data.model.Contact
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

    suspend fun insert(contact: Contact) {
        val localId = contactDao.insertContact(
            contact.copy(syncStatus = SyncStatus.PENDING_CREATE)
        )
        contactDao.insertSyncQueue(
            SyncQueue(contactLocalId = localId, action = SyncAction.PENDING_CREATE)
        )
        scheduleSyncWorker()
    }

    suspend fun update(contact: Contact) {
        android.util.Log.d("DEBUG_SYNC", "REPOSITORY UPDATE -> remoteId=${contact.remoteId}, id=${contact.id}")

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

            android.util.Log.d("DEBUG_SYNC", "ACTION DECIDIDA -> $action, contactLocalId usado=${contact.id}")

            val queueId = contactDao.insertSyncQueue(
                SyncQueue(contactLocalId = contact.id, action = action)
            )

            android.util.Log.d("DEBUG_SYNC", "QUEUE INSERTADO CON ID -> $queueId")

            scheduleSyncWorker()
            android.util.Log.d("DEBUG_SYNC", "WORKER PROGRAMADO OK")
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
        scheduleSyncWorker()
    }

    // Sincronización manual: trae contactos desde la API y los guarda localmente
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
                                remoteImageUrl = remote.image?.url,
                                syncStatus = SyncStatus.SYNCED
                            )
                        )
                    }
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