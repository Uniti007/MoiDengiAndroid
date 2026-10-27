package com.example.moidengi

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale

private val AppBackground = Color(0xFFF8F6FB)
private val CardWhite = Color(0xFFFFFFFF)
private val Lavender = Color(0xFF8067B2)
private val LavenderDark = Color(0xFF59447F)
private val LavenderSoft = Color(0xFFF0EAF8)
private val Ink = Color(0xFF2C2635)
private val Muted = Color(0xFF817A89)
private val ExpenseColor = Color(0xFFC1687E)
private val IncomeColor = Color(0xFF4E8B70)
private val ChartColors = listOf(
    Color(0xFF8A70BD), Color(0xFFC28EB7), Color(0xFF6F9F9B),
    Color(0xFFE0A776), Color(0xFF7E8EB8), Color(0xFFB77A8B),
    Color(0xFFA0A86E), Color(0xFF9A83D1)
)

data class Transaction(
    val id: Long,
    val title: String,
    val amount: Double,
    val isIncome: Boolean,
    val category: String,
    val date: Long
)

private enum class ChartPeriod(val label: String) {
    DAY("День"), WEEK("Неделя"), MONTH("Месяц"), YEAR("Год")
}

private data class CategorySlice(val name: String, val amount: Double, val color: Color)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Lavender,
                    onPrimary = Color.White,
                    secondary = LavenderDark,
                    background = AppBackground,
                    surface = CardWhite,
                    onBackground = Ink,
                    onSurface = Ink,
                    outline = Color(0xFFE4DEEB)
                )
            ) {
                ExpenseApp(this)
            }
        }
    }
}

@Composable
private fun ExpenseApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("moi_dengi", Context.MODE_PRIVATE) }
    var transactions by remember { mutableStateOf(loadTransactions(prefs)) }
    var showAdd by remember { mutableStateOf(false) }
    var incomeMode by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var chartPeriod by remember { mutableStateOf(ChartPeriod.MONTH) }

    val monthTransactions = transactions.filter { transaction ->
        localDate(transaction.date).let {
            it.month == selectedDate.month && it.year == selectedDate.year
        }
    }
    val monthIncome = monthTransactions.filter { it.isIncome }.sumOf { it.amount }
    val monthExpenses = monthTransactions.filterNot { it.isIncome }.sumOf { it.amount }
    val dayTransactions = transactions
        .filter { localDate(it.date) == selectedDate }
        .sortedByDescending { it.date }
    val (periodStart, periodEnd) = periodBounds(selectedDate, chartPeriod)
    val periodExpenses = transactions.filter { transaction ->
        !transaction.isIncome && localDate(transaction.date) in periodStart..periodEnd
    }
    val periodTotal = periodExpenses.sumOf { it.amount }
    val categorySlices = periodExpenses
        .groupBy { it.category }
        .map { (name, items) -> name to items.sumOf { it.amount } }
        .sortedByDescending { it.second }
        .mapIndexed { index, (name, amount) ->
            CategorySlice(name, amount, ChartColors[index % ChartColors.size])
        }

    Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text("Мои траты", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Финансы в спокойном ритме", fontSize = 14.sp, color = Muted)
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("БАЛАНС ЗА ${monthName(selectedDate).uppercase(Locale("ru"))}",
                            color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            formatRubles(monthIncome - monthExpenses),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (monthIncome - monthExpenses >= 0) LavenderDark else ExpenseColor
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MonthlyTotal("Доходы", monthIncome, IncomeColor, Modifier.weight(1f))
                            MonthlyTotal("Расходы", monthExpenses, ExpenseColor, Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { incomeMode = false; showAdd = true },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = RoundedCornerShape(17.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Добавить трату", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { incomeMode = true; showAdd = true },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LavenderDark),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = RoundedCornerShape(17.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Доход", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Аналитика расходов", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Row(
                        modifier = Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(16.dp)).padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ChartPeriod.values().forEach { period ->
                            val selected = period == chartPeriod
                            Box(
                                modifier = Modifier.weight(1f)
                                    .background(if (selected) LavenderSoft else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { chartPeriod = period }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(period.label, color = if (selected) LavenderDark else Muted,
                                    fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                    DateSelector(selectedDate = selectedDate, onDateChange = { selectedDate = it }, context = context)
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Расходы · ${periodLabel(selectedDate, chartPeriod)}", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(formatRubles(periodTotal), color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        if (categorySlices.isEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Box(Modifier.size(150.dp).background(LavenderSoft, CircleShape), contentAlignment = Alignment.Center) {
                                Text("Пока пусто", color = LavenderDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Добавь расходы за этот период — диаграмма появится здесь",
                                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        } else {
                            Spacer(Modifier.height(10.dp))
                            SpendingPie(slices = categorySlices, modifier = Modifier.size(176.dp))
                            Spacer(Modifier.height(12.dp))
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                categorySlices.forEach { slice ->
                                    val percent = (slice.amount * 100 / periodTotal).toInt()
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).background(slice.color, CircleShape))
                                        Spacer(Modifier.width(8.dp))
                                        Text(slice.name, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text("$percent%", color = Muted, fontSize = 12.sp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(formatRubles(slice.amount), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Операции за день", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(dateLabel(selectedDate), fontSize = 13.sp, color = Muted)
                    }
                    Text(formatRubles(dayTransactions.filterNot { it.isIncome }.sumOf { it.amount }),
                        color = ExpenseColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (dayTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("На этот день операций нет", color = Ink, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Выбери другую дату или добавь трату", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(dayTransactions, key = { it.id }) { item ->
                    TransactionRow(item, onDelete = {
                        transactions = transactions.filterNot { it.id == item.id }
                        saveTransactions(prefs, transactions)
                    })
                }
            }
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            incomeMode = incomeMode,
            selectedDate = selectedDate,
            onDismiss = { showAdd = false },
            onSave = { title, amount, category ->
                val timestamp = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val newItem = Transaction(
                    id = System.currentTimeMillis(),
                    title = title,
                    amount = amount,
                    isIncome = incomeMode,
                    category = category,
                    date = timestamp
                )
                transactions = listOf(newItem) + transactions
                saveTransactions(prefs, transactions)
                showAdd = false
            }
        )
    }
}

@Composable
private fun MonthlyTotal(label: String, amount: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(AppBackground, RoundedCornerShape(15.dp)).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(formatRubles(amount), color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DateSelector(selectedDate: LocalDate, onDateChange: (LocalDate) -> Unit, context: Context) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(17.dp)).padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onDateChange(selectedDate.minusDays(1)) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий день", tint = LavenderDark)
        }
        Row(
            modifier = Modifier.weight(1f).clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onDateChange(LocalDate.of(year, month + 1, day)) },
                    selectedDate.year,
                    selectedDate.monthValue - 1,
                    selectedDate.dayOfMonth
                ).show()
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Lavender, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(dateLabel(selectedDate), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = { onDateChange(selectedDate.plusDays(1)) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Следующий день", tint = LavenderDark)
        }
    }
}

@Composable
private fun SpendingPie(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.amount }
    Canvas(modifier = modifier) {
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.amount / total * 360.0).toFloat()
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun TransactionRow(item: Transaction, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).background(if (item.isIncome) Color(0xFFE7F2EC) else LavenderSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (item.isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (item.isIncome) IncomeColor else Lavender
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(item.category, color = Muted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (item.isIncome) "+" else "−") + formatRubles(item.amount),
                    color = if (item.isIncome) IncomeColor else ExpenseColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить операцию", tint = Muted, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun AddTransactionDialog(
    incomeMode: Boolean,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(if (incomeMode) "Зарплата" else "Еда") }
    var expanded by remember { mutableStateOf(false) }
    val categories = if (incomeMode)
        listOf("Зарплата", "Подарок", "Возврат", "Другое")
    else listOf("Еда", "Транспорт", "Жильё", "Покупки", "Здоровье", "Развлечения", "Другое")
    val amount = amountText.replace(" ", "").replace(',', '.').toDoubleOrNull()
    val valid = title.isNotBlank() && amount != null && amount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        title = { Text(if (incomeMode) "Новый доход" else "Новая трата", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Дата: ${dateLabel(selectedDate)}", color = Muted, fontSize = 13.sp)
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("На что потрачено") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    label = { Text("Сумма, ₽") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp)
                )
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Категория: $category")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { category = option; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid && amount != null) onSave(title.trim(), amount, category) }, enabled = valid) {
                Text("Сохранить", color = LavenderDark)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Muted) } }
    )
}

private fun periodBounds(date: LocalDate, period: ChartPeriod): Pair<LocalDate, LocalDate> = when (period) {
    ChartPeriod.DAY -> date to date
    ChartPeriod.WEEK -> {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        start to start.plusDays(6)
    }
    ChartPeriod.MONTH -> date.withDayOfMonth(1) to date.withDayOfMonth(date.lengthOfMonth())
    ChartPeriod.YEAR -> date.withDayOfYear(1) to date.withDayOfYear(date.lengthOfYear())
}

private fun periodLabel(date: LocalDate, period: ChartPeriod): String = when (period) {
    ChartPeriod.DAY -> dateLabel(date)
    ChartPeriod.WEEK -> {
        val (start, end) = periodBounds(date, period)
        "${start.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))} — ${end.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))}"
    }
    ChartPeriod.MONTH -> monthName(date)
    ChartPeriod.YEAR -> date.year.toString()
}

private fun monthName(date: LocalDate): String =
    date.month.getDisplayName(TextStyle.FULL, Locale("ru")).replaceFirstChar { it.titlecase(Locale("ru")) } + " " + date.year

private fun dateLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru")))

private fun localDate(timestamp: Long): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatRubles(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("ru", "RU"))
    formatter.minimumFractionDigits = 0
    formatter.maximumFractionDigits = 2
    return "${formatter.format(amount)} ₽"
}

private fun loadTransactions(prefs: android.content.SharedPreferences): List<Transaction> {
    return try {
        val array = JSONArray(prefs.getString("transactions", "[]") ?: "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Transaction(
                id = o.getLong("id"),
                title = o.getString("title"),
                amount = o.getDouble("amount"),
                isIncome = o.getBoolean("isIncome"),
                category = o.getString("category"),
                date = o.getLong("date")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveTransactions(prefs: android.content.SharedPreferences, transactions: List<Transaction>) {
    val array = JSONArray()
    transactions.forEach { item ->
        array.put(JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("amount", item.amount)
            put("isIncome", item.isIncome)
            put("category", item.category)
            put("date", item.date)
        })
    }
    prefs.edit().putString("transactions", array.toString()).apply()
}
