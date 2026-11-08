package com.deva.moneymanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

val Decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

fun inr0(v: Double): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }.format(v)
fun plain(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.ENGLISH, "%.2f", v)
fun pct(v: Double): String = String.format(Locale.ENGLISH, "%.1f%%", v)
fun signed(v: Double): String = (if (v >= 0) "+" else "−") + inr(abs(v))
fun dayLabel(d: String): String = runCatching {
    LocalDate.parse(d).format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))
}.getOrDefault(d)

fun catEmoji(c: String): String = when (c) {
    "Food" -> "🍔"; "Rent" -> "🏠"; "Travel" -> "🚗"; "Shopping" -> "🛍️"; "Bills" -> "💡"
    "Entertainment" -> "🎬"; "Health" -> "💊"; "Savings" -> "🐖"; "Salary" -> "💼"; "Refund" -> "↩️"
    else -> "💸"
}
fun acctEmoji(n: String): String = when {
    n.contains("wallet", true) || n.contains("paytm", true) || n.contains("phonepe", true) -> "👛"
    n.contains("cash", true) -> "💵"
    else -> "🏦"
}
fun kindEmoji(k: String): String = when (k) {
    "Mutual fund" -> "📈"; "Stock" -> "🏢"; "Fixed deposit" -> "🏦"; "PPF / EPF" -> "🛡️"
    "Gold" -> "🥇"; "Crypto" -> "🪙"; else -> "💼"
}
fun catColor(c: String): Color {
    val i = EXPENSE_CATS.indexOf(c)
    return PALETTE[(if (i >= 0) i else EXPENSE_CATS.size) % PALETTE.size]
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

@Composable
fun Avatar(text: String, color: Color) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 18.sp) }
}

@Composable
fun Header(title: String, sub: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun Muted(t: String) {
    Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Donut(parts: List<Pair<Double, Color>>, dia: Dp = 150.dp, center: @Composable () -> Unit = {}) {
    val total = parts.sumOf { it.first }
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 26.dp.toPx()
            var start = -90f
            if (total > 0) parts.forEach { (v, c) ->
                val sweep = (v / total).toFloat() * 360f
                drawArc(c, start, maxOf(sweep - 1.5f, 0.5f), false, Offset(sw / 2, sw / 2),
                    Size(size.width - sw, size.height - sw), style = Stroke(sw))
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun Legend(rows: List<Triple<String, Double, Color>>) {
    val tot = rows.sumOf { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (n, v, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(8.dp))
                Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(inr0(v), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Text(pct(if (tot > 0) v / tot * 100 else 0.0), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Pick(label: String, items: List<String>, sel: Int, onSel: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
            Text("$label: ${items.getOrElse(sel) { "" }}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { i, s ->
                DropdownMenuItem(text = { Text(s) }, onClick = { onSel(i); open = false })
            }
        }
    }
}

@Composable
fun AmountDialog(title: String, label: String, onDismiss: () -> Unit, onOk: (Double) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, label = { Text(label) }, singleLine = true, keyboardOptions = Decimal) },
        confirmButton = {
            TextButton(enabled = (t.toDoubleOrNull() ?: 0.0) > 0, onClick = { onOk(t.toDouble()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmDialog(title: String, onDismiss: () -> Unit, onYes: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onYes) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
