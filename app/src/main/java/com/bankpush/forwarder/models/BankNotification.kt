package com.bankpush.forwarder.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class BankNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val packageName: String,
    val rawTitle: String,
    val rawText: String,
    val amount: Double?,
    val currency: String?,
    val operationType: String?,     // "Списание", "Зачисление", "Перевод"
    val cardLast4: String?,
    val merchant: String?,
    val balance: Double?,
    val timestamp: Long = System.currentTimeMillis(),
    val isSentToTelegram: Boolean = false,
    val isSelected: Boolean = false
)
