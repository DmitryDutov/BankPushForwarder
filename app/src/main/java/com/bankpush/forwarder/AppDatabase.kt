package com.bankpush.forwarder

import android.content.Context
import androidx.room.*
import com.bankpush.forwarder.models.BankNotification
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<BankNotification>>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAll(): List<BankNotification>

    @Query("SELECT * FROM notifications WHERE isSentToTelegram = 0 ORDER BY timestamp DESC")
    suspend fun getUnsent(): List<BankNotification>

    @Insert
    suspend fun insert(notification: BankNotification): Long

    @Update
    suspend fun update(notification: BankNotification)

    @Query("UPDATE notifications SET isSentToTelegram = 1 WHERE id = :id")
    suspend fun markSent(id: Long)

    @Query("UPDATE notifications SET isSelected = :selected WHERE id = :id")
    suspend fun setSelected(id: Long, selected: Boolean)

    @Query("UPDATE notifications SET isSelected = 0")
    suspend fun deselectAll()

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}

@Database(entities = [BankNotification::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bank_notifications.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
