package com.cursokotlin.contactos_app.data.local.dao

import androidx.room.*
import com.cursokotlin.contactos_app.data.model.Contact
import com.cursokotlin.contactos_app.data.model.ContactImage
import com.cursokotlin.contactos_app.data.model.SyncQueue
import com.cursokotlin.contactos_app.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 AND isDeleted = 0 ORDER BY name ASC")
    fun getFavoriteContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: Long): Contact?

    @Query("SELECT * FROM contacts WHERE email = :email LIMIT 1")
    suspend fun getContactByEmail(email: String): Contact?

    @Query("SELECT * FROM contacts WHERE syncStatus != 'SYNCED' AND isDeleted = 0")
    suspend fun getPendingContacts(): List<Contact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("UPDATE contacts SET syncStatus = :status, remoteId = :remoteId WHERE id = :localId")
    suspend fun updateSyncStatus(localId: Long, status: SyncStatus, remoteId: Long?)

    @Query("UPDATE contacts SET syncStatus = :status WHERE id = :localId")
    suspend fun updateSyncStatusOnly(localId: Long, status: SyncStatus)

    // SyncQueue
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncQueue(item: SyncQueue): Long

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingQueue(): List<SyncQueue>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteSyncQueueItem(id: Long)

    @Query("UPDATE sync_queue SET attempts = attempts + 1, lastError = :error, status = :status WHERE id = :id")
    suspend fun updateSyncQueueItem(id: Long, error: String, status: String)

    @Query("SELECT * FROM contacts WHERE isDeleted = 0")
    suspend fun getAllContactsList(): List<Contact>

    // ---- Imágenes del contacto (galería) ----

    @Query("SELECT * FROM contact_images WHERE contactLocalId = :contactLocalId AND isDeleted = 0")
    fun getImagesForContact(contactLocalId: Long): Flow<List<ContactImage>>

    @Query("SELECT * FROM contact_images WHERE contactLocalId = :contactLocalId AND isDeleted = 0")
    suspend fun getImagesForContactList(contactLocalId: Long): List<ContactImage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ContactImage): Long

    @Update
    suspend fun updateImage(image: ContactImage)

    @Query("DELETE FROM contact_images WHERE contactLocalId = :contactLocalId")
    suspend fun deleteImagesForContact(contactLocalId: Long)

    @Query("DELETE FROM contact_images WHERE localId = :localId")
    suspend fun deleteImageById(localId: Long)

    // Imágenes que aún no se han subido al servidor (para la cola de sincronización)
    @Query("SELECT * FROM contact_images WHERE syncStatus != 'SYNCED' AND isDeleted = 0")
    suspend fun getPendingImages(): List<ContactImage>

    @Query("UPDATE contact_images SET syncStatus = :status, remoteId = :remoteId WHERE localId = :localId")
    suspend fun updateImageSyncStatus(localId: Long, status: SyncStatus, remoteId: Long?)

    @Query("UPDATE contact_images SET syncStatus = :status WHERE localId = :localId")
    suspend fun updateImageSyncStatusOnly(localId: Long, status: SyncStatus)
}