package com.bankpush.forwarder

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.*

class NotificationCatcherService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dao by lazy { AppDatabase.get(this).notificationDao() }

    companion object {
        const val ACTION_NEW_NOTIFICATION = "com.bankpush.NEW_NOTIFICATION"
        const val TAG = "NotifCatcher"

        // Настройка: автоотправка в Telegram
        var autoSend = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        Log.d(TAG, "Notification from: $pkg")

        // Фильтруем — только банковские
        if (!BankNotificationParser.isBankNotification(pkg)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        // Используем bigText если есть (там обычно полный текст)
        val fullText = bigText ?: text

        if (fullText.isBlank()) return

        Log.d(TAG, "Bank notification: $title | $fullText")

        val parsed = BankNotificationParser.parse(pkg, title, fullText)

        scope.launch {
            try {
                val id = dao.insert(parsed)
                Log.d(TAG, "Saved notification #$id")

                // Оповещаем Activity
                sendBroadcast(Intent(ACTION_NEW_NOTIFICATION))

                // Автоотправка если включена
                if (autoSend) {
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    val token = prefs.getString("bot_token", "") ?: ""
                    val chatId = prefs.getString("chat_id", "") ?: ""
                    if (token.isNotBlank() && chatId.isNotBlank()) {
                        val success = TelegramSender.send(token, chatId, parsed)
                        if (success) {
                            dao.markSent(id)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
