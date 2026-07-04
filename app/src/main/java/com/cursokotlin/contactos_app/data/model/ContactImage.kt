package com.cursokotlin.contactos_app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_images")
data class ContactImage(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Long? = null,
    val contactLocalId: Long,       // FK lógica hacia Contact.id
    val localPath: String? = null,  // ruta local si se tomó/seleccionó desde el dispositivo (para offline)
    val remoteUrl: String? = null,  // url que llega del backend
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)