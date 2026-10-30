package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        VaultMediaItem::class,
        VaultAlbum::class,
        SecurityLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "calcvault_internal.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Pre-populate default system albums
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = getInstance(context).vaultDao()
                                dao.insertAlbum(
                                    VaultAlbum(id = 1, name = "Recents", isSystemDefault = true)
                                )
                                dao.insertAlbum(
                                    VaultAlbum(id = 2, name = "Personal", isSystemDefault = true)
                                )
                                dao.insertAlbum(
                                    VaultAlbum(id = 3, name = "Favorites", isSystemDefault = true)
                                )
                                dao.insertLog(
                                    SecurityLog(
                                        eventType = "SYSTEM_INITIALIZED",
                                        details = "CalcVault secure storage sandbox initialized."
                                    )
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
