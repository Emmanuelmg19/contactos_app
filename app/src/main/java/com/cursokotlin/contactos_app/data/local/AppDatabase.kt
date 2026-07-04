package com.cursokotlin.contactos_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cursokotlin.contactos_app.data.local.dao.ContactDao
import com.cursokotlin.contactos_app.data.model.Contact
import com.cursokotlin.contactos_app.data.model.ContactImage
import com.cursokotlin.contactos_app.data.model.SyncQueue

@Database(
    entities = [Contact::class, SyncQueue::class, ContactImage::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "contact_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}