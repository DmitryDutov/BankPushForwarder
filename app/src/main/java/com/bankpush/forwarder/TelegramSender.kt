package com.bankpush.forwarder

import android.util.Log
import com.bankpush.forwarder.models.BankNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object TelegramSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Конвертация уведомления в JSON для Telegram-бота.
     * Вся умная логика (маппинг карт, категории, трансферы) — на стороне бота.
     */
    fun notificationToJson(n: BankNotification): JSONObject {
        return JSONObject().apply {
            put("version", "2.0")
            put("bank_name", n.bankName)
            put("package_name", n.packageName)
            put("card_last4", n.cardLast4)
            put("amount", n.amount)
            put("currency", n.currency ?: "RUB")
            put("operation_type", n.operationType)
            put("merchant", n.merchant)
            put("balance", n.balance)
            put("raw_title", n.rawTitle)
            put("raw_text", n.rawText)
            put("timestamp", n.timestamp)
        }
    }

    /**
     * Отправка уведомления как JSON
     */
    suspend fun send(
        botToken: String,
        chatId: String,
        notification: BankNotification
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = notificationToJson(notification)
            // Оборачиваем в code-блок чтобы бот мог легко парсить
            val message = "```json\n${json.toString(2)}\n```"
            sendMessage(botToken, chatId, message, parseMode = "MarkdownV2")
        } catch (e: Exception) {
            Log.e("TelegramSender", "Error sending", e)
            false
        }
    }

    /**
     * Отправка простого текста (для тестирования)
     */
    suspend fun sendRaw(
        botToken: String,
        chatId: String,
        text: String
    ): Boolean = withContext(Dispatchers.IO) {
        sendMessage(botToken, chatId, text)
    }

    private fun sendMessage(
        botToken: String,
        chatId: String,
        text: String,
        parseMode: String? = null
    ): Boolean {
        val url = "https://api.telegram.org/bot${botToken}/sendMessage"

        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text)
            if (parseMode != null) put("parse_mode", parseMode)
            put("disable_web_page_preview", true)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.d("TelegramSender", "Response ${response.code}: $responseBody")

            // Если парсинг markdown не удался — шлём plain JSON
            if (!response.isSuccessful && response.code == 400 && parseMode != null) {
                sendMessage(botToken, chatId, text, parseMode = null)
            } else {
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("TelegramSender", "Network error", e)
            false
        }
    }

    /**
     * Проверка валидности токена
     */
    suspend fun testConnection(botToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot${botToken}/getMe"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                JSONObject(body).getJSONObject("result").getString("username")
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
