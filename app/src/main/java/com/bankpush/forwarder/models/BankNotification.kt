package com.bankpush.forwarder.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class BankNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,           // "Сбербанк", "Тинькофф" и т.д.
    val packageName: String,        // com.sberbank.android
    val rawTitle: String,           // Заголовок пуша
    val rawText: String,            // Текст пуша
    val amount: Double?,            // Распарсенная сумма
    val currency: String?,          // RUB, USD...
    val operationType: String?,     // "Списание", "Зачисление", "Перевод"
    val cardLast4: String?,         // Последние 4 цифры карты
    val merchant: String?,          // Название магазина/отправителя
    val balance: Double?,           // Остаток (если есть)
    val timestamp: Long = System.currentTimeMillis(),
    val isSentToTelegram: Boolean = false,
    val isSelected: Boolean = false
)
