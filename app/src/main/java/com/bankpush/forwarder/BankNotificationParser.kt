package com.bankpush.forwarder

import com.bankpush.forwarder.models.BankNotification

object BankNotificationParser {

    // Пакеты банковских приложений
    private val BANK_PACKAGES = mapOf(
        "ru.sberbankmobile" to "Сбербанк",
        "ru.sberbank.android" to "Сбербанк",
        "com.idamob.tinkoff.android" to "Тинькофф",
        "ru.tinkoff.investing" to "Тинькофф Инвестиции",
        "ru.alfabank.mobile.android" to "Альфа-Банк",
        "com.openbank" to "Открытие",
        "ru.vtb24.mobilebanking.android" to "ВТБ",
        "com.bspb.android" to "БСПБ",
        "ru.raiffeisennews" to "Райффайзен",
        "com.gazprombank.android" to "Газпромбанк",
        "ru.rosbank.android" to "Росбанк",
        "com.sovcomcard.halva" to "Совкомбанк",
        "ru.psbank.online" to "ПСБ",
        "ru.yoomoney.android" to "ЮMoney",
        "com.ubrir" to "УБРиР",
        "ru.mw" to "МТС Банк",
        "com.beeline.dc" to "Билайн",
    )

    // Дополнительные пакеты, которые можно добавить
    private val CUSTOM_PACKAGES = mutableMapOf<String, String>()

    fun addCustomBank(packageName: String, bankName: String) {
        CUSTOM_PACKAGES[packageName] = bankName
    }

    fun isBankNotification(packageName: String): Boolean {
        return packageName in BANK_PACKAGES || packageName in CUSTOM_PACKAGES
    }

    fun getBankName(packageName: String): String {
        return BANK_PACKAGES[packageName]
            ?: CUSTOM_PACKAGES[packageName]
            ?: packageName
    }

    /**
     * Главный метод парсинга
     */
    fun parse(packageName: String, title: String, text: String): BankNotification {
        val bankName = getBankName(packageName)
        val fullText = "$title $text"

        return BankNotification(
            bankName = bankName,
            packageName = packageName,
            rawTitle = title,
            rawText = text,
            amount = extractAmount(fullText),
            currency = extractCurrency(fullText),
            operationType = extractOperationType(fullText),
            cardLast4 = extractCardLast4(fullText),
            merchant = extractMerchant(text, bankName),
            balance = extractBalance(fullText)
        )
    }

    /**
     * Извлечение суммы операции
     */
    private fun extractAmount(text: String): Double? {
        val patterns = listOf(
            Regex("""(?:списан[иеоа]|покупка|оплата|перевод|зачислен[иеоа]|пополнение|возврат|снятие)\s*:?\s*([\d\s]+[.,]\d{2})\s*([₽$€\w]{1,3})?""", RegexOption.IGNORE_CASE),
            Regex("""сумм[аы]\s*:?\s*([\d\s]+[.,]\d{2})""", RegexOption.IGNORE_CASE),
            Regex("""([\d\s]+[.,]\d{2})\s*(?:₽|руб|RUB|USD|\$|EUR|€)""", RegexOption.IGNORE_CASE),
            Regex("""(?:₽|руб|RUB|USD|\$|EUR|€)\s*([\d\s]+[.,]\d{2})""", RegexOption.IGNORE_CASE),
            Regex("""(\d[\d\s]*[.,]\d{2})(?:\s|$|[^0-9])"""),
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val raw = match.groupValues[1]
                    .replace(" ", "")
                    .replace(",", ".")
                return raw.toDoubleOrNull()
            }
        }
        return null
    }

    private fun extractCurrency(text: String): String? {
        return when {
            Regex("""[₽]|руб|RUB""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "RUB"
            Regex("""[$]|USD|долл""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "USD"
            Regex("""[€]|EUR|евро""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "EUR"
            else -> "RUB"
        }
    }

    private fun extractOperationType(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("списан") || lower.contains("покупка") ||
            lower.contains("оплата") || lower.contains("снятие") -> "Списание"

            lower.contains("зачислен") || lower.contains("пополнен") ||
            lower.contains("получен") || lower.contains("начислен") ||
            lower.contains("возврат") -> "Зачисление"

            lower.contains("перевод") || lower.contains("отправлен") -> "Перевод"

            else -> null
        }
    }

    /**
     * Извлечение последних 4 цифр карты
     */
    private fun extractCardLast4(text: String): String? {
        val patterns = listOf(
            Regex("""[*·•]+\s*(\d{4})"""),
            Regex("""(?:карт[аы]|card|счёт|счет)\s*(?:[*·•]*\s*)?(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(?:Visa|MasterCard|Мир|МИР)\s*(\d{4})""", RegexOption.IGNORE_CASE),
        )

        for (p in patterns) {
            val match = p.find(text)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    /**
     * Извлечение названия мерчанта/магазина
     */
    private fun extractMerchant(text: String, bankName: String): String? {
        val patterns = listOf(
            Regex("""(?:[\d.,]+\s*(?:₽|руб|RUB))\s+([A-Za-zА-Яа-яЁё][\w\s\-А-Яа-яЁё]{2,30}?)(?:\s*\.?\s*(?:Баланс|Остаток|Доступно)|$)""", RegexOption.IGNORE_CASE),
            Regex("""в\s+(?:магазине|ресторане)?\s*([А-Яа-яЁё\w][\w\s\-]{2,30})""", RegexOption.IGNORE_CASE),
        )

        for (p in patterns) {
            val match = p.find(text)
            if (match != null) {
                val merchant = match.groupValues[1].trim()
                if (merchant.length > 2) return merchant
            }
        }
        return null
    }

    /**
     * Извлечение баланса
     */
    private fun extractBalance(text: String): Double? {
        val patterns = listOf(
            Regex("""(?:баланс|остаток|доступно)\s*:?\s*([\d\s]+[.,]\d{2})""", RegexOption.IGNORE_CASE),
        )
        for (p in patterns) {
            val match = p.find(text)
            if (match != null) {
                val raw = match.groupValues[1].replace(" ", "").replace(",", ".")
                return raw.toDoubleOrNull()
            }
        }
        return null
    }

    /**
     * Форматирование для отправки в Telegram
     */
    fun formatForTelegram(n: BankNotification): String {
        val sb = StringBuilder()
        sb.appendLine("🏦 *${escMd(n.bankName)}*")

        if (n.operationType != null) {
            val emoji = when (n.operationType) {
                "Списание" -> "🔴"
                "Зачисление" -> "🟢"
                "Перевод" -> "🔵"
                else -> "⚪"
            }
            sb.appendLine("$emoji ${escMd(n.operationType)}")
        }

        if (n.amount != null) {
            val sign = if (n.operationType == "Списание") "-" else
                       if (n.operationType == "Зачисление") "+" else ""
            sb.appendLine("💰 ${sign}${formatMoney(n.amount)} ${n.currency ?: ""}")
        }

        if (n.cardLast4 != null) {
            sb.appendLine("💳 \\*\\*${n.cardLast4}")
        }

        if (n.merchant != null) {
            sb.appendLine("🏪 ${escMd(n.merchant)}")
        }

        if (n.balance != null) {
            sb.appendLine("📊 Баланс: ${formatMoney(n.balance)} ${n.currency ?: ""}")
        }

        sb.appendLine("🕐 ${formatTime(n.timestamp)}")
        sb.appendLine()
        sb.appendLine("_Исходное:_ `${escMd(n.rawText)}`")

        return sb.toString()
    }

    private fun escMd(s: String): String {
        return s.replace("_", "\\_")
            .replace("*", "\\*")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("`", "\\`")
    }

    private fun formatMoney(amount: Double): String {
        return String.format("%,.2f", amount)
    }

    private fun formatTime(ts: Long): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale("ru"))
        return sdf.format(java.util.Date(ts))
    }
}
