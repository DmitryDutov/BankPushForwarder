package com.bankpush.forwarder

import android.content.*
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bankpush.forwarder.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: NotificationAdapter
    private val dao by lazy { AppDatabase.get(this).notificationDao() }
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Новое уведомление пришло — список обновится через Flow
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSettings()
        setupButtons()
        observeNotifications()
        checkServiceStatus()

        registerReceiver(
            receiver,
            IntentFilter(NotificationCatcherService.ACTION_NEW_NOTIFICATION),
            RECEIVER_NOT_EXPORTED
        )
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter { notification, isChecked ->
            lifecycleScope.launch {
                dao.setSelected(notification.id, isChecked)
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupSettings() {
        val defaultToken = "8671501844:AAHnLA8WPGJ8CFyA08Q2yxOvClK5cLMruOQ"
        binding.etBotToken.setText(prefs.getString("bot_token", defaultToken))
        binding.etChatId.setText(prefs.getString("chat_id", ""))
        binding.switchAutoSend.isChecked = prefs.getBoolean("auto_send", false)

        NotificationCatcherService.autoSend = binding.switchAutoSend.isChecked

        binding.switchAutoSend.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("auto_send", checked).apply()
            NotificationCatcherService.autoSend = checked
        }

        binding.btnSaveSettings.setOnClickListener {
            val token = binding.etBotToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()

            prefs.edit()
                .putString("bot_token", token)
                .putString("chat_id", chatId)
                .apply()

            Toast.makeText(this, "✅ Сохранено", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestBot.setOnClickListener {
            val token = binding.etBotToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()

            if (token.isBlank() || chatId.isBlank()) {
                Toast.makeText(this, "Заполните Token и Chat ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val botName = TelegramSender.testConnection(token)
                if (botName == null) {
                    Toast.makeText(this@MainActivity, "❌ Бот не найден", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val success = TelegramSender.sendRaw(
                    token, chatId,
                    "✅ Тест успешен!\nБот: @$botName\nПриложение Bank Push Forwarder подключено."
                )

                if (success) {
                    Toast.makeText(this@MainActivity, "✅ Бот @$botName работает!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ Не удалось отправить. Проверьте Chat ID", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.btnSelectAll.setOnClickListener {
            val currentList = adapter.currentList
            val allSelected = currentList.all { it.isSelected }

            lifecycleScope.launch {
                if (allSelected) {
                    dao.deselectAll()
                } else {
                    currentList.forEach { dao.setSelected(it.id, true) }
                }
            }
        }

        binding.btnSendSelected.setOnClickListener {
            val selected = adapter.currentList.filter { it.isSelected }

            if (selected.isEmpty()) {
                Toast.makeText(this, "Выберите уведомления", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val token = prefs.getString("bot_token", "") ?: ""
            val chatId = prefs.getString("chat_id", "") ?: ""

            if (token.isBlank() || chatId.isBlank()) {
                Toast.makeText(this, "Настройте Telegram бота", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                binding.btnSendSelected.isEnabled = false
                binding.btnSendSelected.text = "⏳ Отправка..."

                var successCount = 0

                for (notif in selected) {
                    val success = TelegramSender.send(token, chatId, notif)
                    if (success) {
                        dao.markSent(notif.id)
                        dao.setSelected(notif.id, false)
                        successCount++
                    }
                    kotlinx.coroutines.delay(500)
                }

                binding.btnSendSelected.isEnabled = true
                binding.btnSendSelected.text = "📤 Отправить выбранные"

                Toast.makeText(
                    this@MainActivity,
                    "Отправлено: $successCount из ${selected.size}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Очистить историю?")
                .setMessage("Все уведомления будут удалены")
                .setPositiveButton("Удалить") { _, _ ->
                    lifecycleScope.launch { dao.deleteAll() }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun observeNotifications() {
        lifecycleScope.launch {
            dao.getAllFlow().collectLatest { list ->
                adapter.submitList(list)
                binding.tvNotifCount.text = "Уведомления (${list.size})"
            }
        }
    }

    private fun checkServiceStatus() {
        val enabled = isNotificationServiceEnabled()
        updateServiceStatus(enabled)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun updateServiceStatus(enabled: Boolean) {
        if (enabled) {
            binding.tvStatus.text = "✅ Сервис активен — ловим пуши"
            binding.statusDot.setBackgroundResource(R.drawable.circle_green)
            binding.btnPermission.text = "Настройки"
        } else {
            binding.tvStatus.text = "⚠️ Нужен доступ к уведомлениям"
            binding.statusDot.setBackgroundResource(R.drawable.circle_red)
            binding.btnPermission.text = "Включить"
        }
    }

    override fun onResume() {
        super.onResume()
        checkServiceStatus()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}
