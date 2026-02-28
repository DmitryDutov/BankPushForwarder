package com.bankpush.forwarder

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bankpush.forwarder.models.BankNotification
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private val onCheckedChange: (BankNotification, Boolean) -> Unit
) : ListAdapter<BankNotification, NotificationAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BankNotification>() {
            override fun areItemsTheSame(a: BankNotification, b: BankNotification) = a.id == b.id
            override fun areContentsTheSame(a: BankNotification, b: BankNotification) = a == b
        }
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale("ru"))
    private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale("ru"))

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkbox)
        val tvBank: TextView = view.findViewById(R.id.tvBank)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvOperation: TextView = view.findViewById(R.id.tvOperation)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvRawText: TextView = view.findViewById(R.id.tvRawText)
        val tvSentStatus: TextView = view.findViewById(R.id.tvSentStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = item.isSelected
        holder.checkbox.setOnCheckedChangeListener { _, checked ->
            onCheckedChange(item, checked)
        }

        holder.tvBank.text = "🏦 ${item.bankName}"

        val now = System.currentTimeMillis()
        val isToday = (now - item.timestamp) < 86400000
        holder.tvTime.text = if (isToday) timeFormat.format(Date(item.timestamp))
                             else dateFormat.format(Date(item.timestamp))

        if (item.operationType != null) {
            holder.tvOperation.visibility = View.VISIBLE
            holder.tvOperation.text = item.operationType
            val bgColor = when (item.operationType) {
                "Списание" -> "#FFEBEE"
                "Зачисление" -> "#E8F5E9"
                "Перевод" -> "#E3F2FD"
                else -> "#F5F5F5"
            }
            holder.tvOperation.setBackgroundColor(Color.parseColor(bgColor))
        } else {
            holder.tvOperation.visibility = View.GONE
        }

        if (item.amount != null) {
            val sign = when (item.operationType) {
                "Списание" -> "-"
                "Зачисление" -> "+"
                else -> ""
            }
            holder.tvAmount.text = "$sign${String.format("%,.2f", item.amount)} ${item.currency ?: "₽"}"
            holder.tvAmount.setTextColor(
                when (item.operationType) {
                    "Списание" -> Color.parseColor("#D32F2F")
                    "Зачисление" -> Color.parseColor("#388E3C")
                    else -> Color.parseColor("#333333")
                }
            )
        } else {
            holder.tvAmount.text = ""
        }

        holder.tvRawText.text = item.rawText

        if (item.isSentToTelegram) {
            holder.tvSentStatus.visibility = View.VISIBLE
            holder.tvSentStatus.text = "✅ Отправлено"
            holder.tvSentStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            holder.tvSentStatus.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            holder.checkbox.isChecked = !holder.checkbox.isChecked
        }
    }
}
