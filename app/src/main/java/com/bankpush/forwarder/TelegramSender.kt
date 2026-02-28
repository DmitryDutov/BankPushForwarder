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
     * Отправка одного уведомления
     */
    suspend fun send(
        botToken: String,
        chatId: String,
        notification: BankNotification
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val message = BankNotificationParser.formatForTelegram(notification)
            sendMessage(botToken, chatId, message)
        } catch (e: Exception) {
            Log.e("TelegramSender", "Error sending", e)
            false
        }
    }

    /**
     * Отправка пачки уведомлений одним сообщением
     */
    suspend fun sendBatch(
        botToken: String,
        chatId: String,
        notifications: List<BankNotification>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            sb.appendLine("📋 *Пачка уведомлений \\(${notifications.size}\\)*")
            sb.appendLine("━━━━━━━━━━━━━━━━━━")

            notifications.forEach { n ->
                sb.appendLine(BankNotificationParser.formatForTelegram(n))
                sb.appendLine("━━━━━━━━━━━━━━━━━━")
            }

            sendMessage(botToken, chatId, sb.toString())
        } catch (e: Exception) {
            Log.e("TelegramSender", "Error sending batch", e)
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

    private fun sendMessage(botToken: String, chatId: String, text: String): Boolean {
        val url = "https://api.telegram.org/bot${botToken}/sendMessage"

        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text)
            put("parse_mode", "MarkdownV2")
            put("disable_web_page_preview", true)
        }

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        Log.d("TelegramSender", "Response ${response.code}: $responseBody")

        // Если MarkdownV2 сломался — отправляем как plain text
        if (!response.isSuccessful && response.code == 400) {
            return sendPlainText(botToken, chatId, text)
        }

        return response.isSuccessful
    }

    private fun sendPlainText(botToken: String, chatId: String, text: String): Boolean {
        val url = "https://api.telegram.org/bot${botToken}/sendMessage"

        val cleanText = text
            .replace("\\*", "*")
            .replace("\\_", "_")
            .replace("\\[", "[")
            .replace("\\]", "]")
            .replace("\\`", "`")
            .replace("*", "")
            .replace("_", "")
            .replace("`", "")

        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("text", cleanText)
        }

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        return response.isSuccessful
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
                val json = JSONObject(body)
                val botName = json.getJSONObject("result").getString("username")
                botName
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
