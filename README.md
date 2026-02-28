# Bank Push Forwarder — Android App

Перехватывает банковские push-уведомления, парсит сумму/карту/тип операции и отправляет в Telegram.

## Структура проекта

```
BankPushForwarder/
├── app/src/main/
│   ├── java/com/bankpush/forwarder/
│   │   ├── MainActivity.kt                  — главный экран
│   │   ├── NotificationCatcherService.kt    — перехват уведомлений
│   │   ├── BankNotificationParser.kt        — парсинг текста пуша
│   │   ├── TelegramSender.kt                — отправка в Telegram Bot API
│   │   ├── NotificationAdapter.kt           — RecyclerView адаптер
│   │   ├── AppDatabase.kt                   — Room БД + DAO
│   │   └── models/BankNotification.kt       — модель данных
│   ├── res/layout/
│   │   ├── activity_main.xml
│   │   └── item_notification.xml
│   ├── res/drawable/
│   │   ├── circle_green.xml
│   │   └── circle_red.xml
│   ├── res/xml/
│   │   └── network_security_config.xml
│   └── AndroidManifest.xml
├── build.gradle.kts (app)
├── build.gradle.kts (project)
└── settings.gradle.kts
```

## Требования

- Android Studio Hedgehog (2023.1.1) или новее
- Android SDK 34
- Kotlin 1.9.22
- minSdk 26 (Android 8.0)

## Сборка

1. Откройте Android Studio
2. File → Open → выберите папку `BankPushForwarder`
3. Дождитесь синхронизации Gradle
4. Build → Make Project
5. Run → Run 'app'

## Настройка Telegram бота

### Создание бота
1. Откройте [@BotFather](https://t.me/BotFather) в Telegram
2. Отправьте `/newbot`
3. Задайте имя и username боту
4. Скопируйте **токен** (формат: `123456789:AABBccdd...`)

### Получение Chat ID
1. Напишите боту любое сообщение
2. Откройте в браузере: `https://api.telegram.org/bot<ВАШ_ТОКЕН>/getUpdates`
3. Найдите `"chat":{"id": ЧИСЛО}` — это ваш Chat ID
4. Для группы/канала: добавьте бота, Chat ID будет отрицательным (`-100...`)

## Первый запуск

1. Установите APK на устройство
2. В приложении нажмите **«Включить»** → откроются настройки Android
3. Найдите **«Bank Push Forwarder»** и включите доступ к уведомлениям
4. Вернитесь в приложение — статус сменится на зелёный ✅
5. Введите Bot Token и Chat ID → **«Сохранить»**
6. Нажмите **«🧪 Тест»** — в Telegram должно прийти тестовое сообщение

## Поддерживаемые банки

| Банк | Package Name |
|------|-------------|
| Сбербанк | ru.sberbankmobile / ru.sberbank.android |
| Тинькофф | com.idamob.tinkoff.android |
| Альфа-Банк | ru.alfabank.mobile.android |
| ВТБ | ru.vtb24.mobilebanking.android |
| Газпромбанк | com.gazprombank.android |
| Росбанк | ru.rosbank.android |
| Райффайзен | ru.raiffeisennews |
| ЮMoney | ru.yoomoney.android |
| МТС Банк | ru.mw |
| и другие... | |

Для добавления своего банка используйте `BankNotificationParser.addCustomBank(packageName, bankName)`.

## Пример сообщения в Telegram

```
🏦 Сбербанк
🔴 Списание
💰 -1 234.56 RUB
💳 **4567
🏪 PYATEROCHKA
📊 Баланс: 45 678.90 RUB
🕐 15.01.2025 14:32:07

Исходное: Покупка 1234.56₽ PYATEROCHKA Баланс: 45678.90₽
```

## Важные замечания

- **Root не нужен** — используется стандартный `NotificationListenerService` API
- **Безопасность**: токен хранится в `SharedPreferences`. Для продакшена используйте `EncryptedSharedPreferences`
- **Батарея**: сервис работает в фоне с минимальным потреблением
- **Regex**: паттерны парсинга могут требовать доработки под конкретные банки
- **Автоотправка**: включается переключателем в приложении — каждый новый пуш сразу летит в Telegram
