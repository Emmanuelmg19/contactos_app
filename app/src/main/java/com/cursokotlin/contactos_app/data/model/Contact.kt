package com.cursokotlin.contactos_app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    ERROR
}

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: Long? = null,
    val name: String,
    val surname: String = "",
    val phone: String,
    val email: String = "",
    val photoUri: String? = null,
    val remoteImageUrl: String? = null,
    val isFavorite: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)