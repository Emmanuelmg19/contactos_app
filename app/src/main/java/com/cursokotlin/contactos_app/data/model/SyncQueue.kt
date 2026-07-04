package com.cursokotlin.contactos_app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncAction {
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE
}

@Entity(tableName = "sync_queue")
data class SyncQueue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactLocalId: Long,
    val action: SyncAction,
    val status: String = "PENDING",
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)