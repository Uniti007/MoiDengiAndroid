package com.example.moidengi

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver as AndroidBroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.min

data class MonthlyPayment(
    val id: Long,
    val title: String,
    val amount: Double,
    val category: String,
    val dayOfMonth: Int
)

private const val PAYMENT_PREFS = "moi_dengi"
private const val PAYMENT_KEY = "monthly_payments"
private const val PAYMENT_CHANNEL_ID = "monthly_payment_reminders"
private const val EXTRA_PAYMENT_ID = "monthly_payment_id"

internal fun loadMonthlyPayments(context: Context): List<MonthlyPayment> {
    return try {
        val prefs = context.getSharedPreferences(PAYMENT_PREFS, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(PAYMENT_KEY, "[]") ?: "[]")
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            MonthlyPayment(
                id = item.getLong("id"),
                title = item.getString("title"),
                amount = item.getDouble("amount"),
                category = item.getString("category"),
                dayOfMonth = item.getInt("dayOfMonth").coerceIn(1, 31)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun saveMonthlyPayments(context: Context, payments: List<MonthlyPayment>) {
    val array = JSONArray()
    payments.forEach { payment ->
        array.put(JSONObject().apply {
            put("id", payment.id)
            put("title", payment.title)
            put("amount", payment.amount)
            put("category", payment.category)
            put("dayOfMonth", payment.dayOfMonth)
        })
    }
    context.getSharedPreferences(PAYMENT_PREFS, Context.MODE_PRIVATE)
        .edit().putString(PAYMENT_KEY, array.toString()).apply()
}

internal fun scheduleMonthlyPaymentReminder(context: Context, payment: MonthlyPayment) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = reminderPendingIntent(context, payment.id)
    alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        nextPaymentTime(payment.dayOfMonth),
        pendingIntent
    )
}

internal fun cancelMonthlyPaymentReminder(context: Context, paymentId: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(reminderPendingIntent(context, paymentId))
}

private fun reminderPendingIntent(context: Context, paymentId: Long): PendingIntent {
    val intent = Intent(context, MonthlyPaymentReminderReceiver::class.java)
        .putExtra(EXTRA_PAYMENT_ID, paymentId)
    val requestCode = (paymentId xor (paymentId ushr 32)).toInt()
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun nextPaymentTime(dayOfMonth: Int): Long {
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    fun occurrence(month: YearMonth): ZonedDateTime {
        val day = min(dayOfMonth, month.lengthOfMonth())
        return LocalDate.of(month.year, month.month, day).atTime(9, 0).atZone(zone)
    }
    val currentMonthTime = occurrence(YearMonth.from(now))
    val next = if (currentMonthTime.isAfter(now)) currentMonthTime else occurrence(YearMonth.from(now).plusMonths(1))
    return next.toInstant().toEpochMilli()
}

class MonthlyPaymentReminderReceiver : AndroidBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val paymentId = intent.getLongExtra(EXTRA_PAYMENT_ID, -1L)
        val payment = loadMonthlyPayments(context).firstOrNull { it.id == paymentId } ?: return
        showPaymentNotification(context, payment)
        scheduleMonthlyPaymentReminder(context, payment)
    }
}

class ReminderBootReceiver : AndroidBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        loadMonthlyPayments(context).forEach { scheduleMonthlyPaymentReminder(context, it) }
    }
}

private fun showPaymentNotification(context: Context, payment: MonthlyPayment) {
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(
                PAYMENT_CHANNEL_ID,
                "Ежемесячные платежи",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Напоминания о регулярных платежах" }
        )
    }

    val openApp = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = Notification.Builder(context, PAYMENT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Пора оплатить: ${payment.title}")
        .setContentText("${formatNotificationRubles(payment.amount)} · ${payment.category}")
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .setCategory(Notification.CATEGORY_REMINDER)
        .setPriority(Notification.PRIORITY_DEFAULT)
        .build()

    manager.notify((payment.id xor (payment.id ushr 32)).toInt(), notification)
}

private fun formatNotificationRubles(amount: Double): String {
    val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale("ru", "RU"))
    formatter.minimumFractionDigits = 0
    formatter.maximumFractionDigits = 2
    return "${formatter.format(amount)} ₽"
}
