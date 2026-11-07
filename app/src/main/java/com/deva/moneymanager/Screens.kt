@file:OptIn(ExperimentalMaterial3Api::class)

package com.deva.moneymanager

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val Green = Color(0xFF15803D)
private val Decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

fun monthTitle(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.year

@Composable
fun MonthBar(m: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(m.minusMonths(1)) }) { Text("‹") }
        Text(monthTitle(m), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { onChange(m.plusMonths(1)) }) { Text("›") }
    }
}

@Composable
fun TxnRow(t: Txn, names: Map<Long, String>, onDelete: (Long) -> Unit) {
    val sign = when (t.type) { "e" -> "−"; "i" -> "+"; else -> "" }
    val color = when (t.type) {
        "e" -> MaterialTheme.colorScheme.error
        "i" -> Green
        else -> MaterialTheme.colorScheme.onSurface
    }
    val to = t.toAccountId?.let { names[it] } ?: ""
    val where = if (t.type == "t") "${names[t.accountId] ?: ""} → $to" else (names[t.accountId] ?: "")
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (t.type == "t") "Transfer" else t.category, fontWeight = FontWeight.Medium)
            Text(listOf(t.note, where, t.date).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall)
        }
        Text(sign + inr(t.amount), color = color, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onDelete(t.id) }) { Text("✕") }
    }
}

@Composable
fun Home(
    accounts: List<Account>, txns: List<Txn>, budgets: List<Budget>, recs: List<Recurring>,
    onAddAccount: (String, Double) -> Unit, onDeleteAccount: (Long) -> Unit,
    onDeleteTxn: (Long) -> Unit, onDeleteRec: (Long) -> Unit
) {
    val now = YearMonth.now()
    val mt = inMonth(txns, now)
    val inc = mt.filter { it.type == "i" }.sumOf { it.amount }
    val exp = mt.filter { it.type == "e" }.sumOf { it.amount }
    val spent = catTotals(mt)
    val names = accounts.associate { it.id to it.name }
    val alerts = budgets.filter { (spent[it.category] ?: 0.0) >= it.monthlyLimit * 0.8 }
    var dialog by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("Total money you have", style = MaterialTheme.typography.bodyMedium)
                Text(inr(accounts.sumOf { balance(it, txns) }), style = MaterialTheme.typography.headlineLarge)
            }
        }
        if (alerts.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Budget alerts · " + monthTitle(now), fontWeight = FontWeight.Bold)
                    alerts.forEach { b ->
                        val s = spent[b.category] ?: 0.0
                        Text(if (s >= b.monthlyLimit) "🔴 ${b.category}: over by ${inr(s - b.monthlyLimit)}"
                        else "🟠 ${b.category}: ${(s / b.monthlyLimit * 100).toInt()}% used")
                    }
                }
            }
        }
        if (accounts.isEmpty()) item { Text("No accounts yet. Add your first bank account below.") }
        items(accounts, key = { "a${it.id}" }) { a ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name)
                        Text(inr(balance(a, txns)), style = MaterialTheme.typography.titleLarge)
                    }
                    if (accounts.size > 1 && txns.none { it.accountId == a.id || it.toAccountId == a.id }) {
                        TextButton(onClick = { onDeleteAccount(a.id) }) { Text("Remove") }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { dialog = true }, Modifier.fillMaxWidth()) {
                Text("+ Add bank account / wallet / cash")
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(monthTitle(now), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Column { Text("Money in"); Text(inr(inc), color = Green, fontWeight = FontWeight.Bold) }
                        Column { Text("Money out"); Text(inr(exp), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (recs.isNotEmpty()) {
            item { Text("Repeats every month", style = MaterialTheme.typography.titleMedium) }
            items(recs, key = { "r${it.id}" }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${r.category} · ${inr(r.amount)}")
                            Text("${names[r.accountId] ?: ""} · day ${r.day}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onDeleteRec(r.id) }) { Text("Stop") }
                    }
                }
            }
        }
        item { Text("Recent", style = MaterialTheme.typography.titleMedium) }
        val recent = txns.take(6)
        if (recent.isEmpty()) item { Text("No entries yet. Tap + to add one.") }
        else items(recent, key = { "t${it.id}" }) { TxnRow(it, names, onDeleteTxn) }
    }

    if (dialog) {
        var name by remember { mutableStateOf("") }
        var bal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Add account") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. HDFC Savings)") }, singleLine = true)
                    OutlinedTextField(bal, { bal = it }, label = { Text("Current balance ₹") },
                        singleLine = true, keyboardOptions = Decimal)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onAddAccount(name.trim(), bal.toDoubleOrNull() ?: 0.0)
                        dialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun History(
    month: YearMonth, onMonth: (YearMonth) -> Unit, accounts: List<Account>, txns: List<Txn>,
    onDelete: (Long) -> Unit, onExport: () -> Unit
) {
    var q by rememberSaveable { mutableStateOf("") }
    var acc by rememberSaveable { mutableStateOf(-1L) }
    var type by rememberSaveable { mutableStateOf("") }
    val names = accounts.associate { it.id to it.name }
    val list = inMonth(txns, month).filter {
        (acc < 0 || it.accountId == acc || it.toAccountId == acc) &&
            (type.isEmpty() || it.type == type) &&
            (q.isBlank() || (it.category + " " + it.note).contains(q.trim(), ignoreCase = true))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MonthBar(month, onMonth)
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(),
            label = { Text("Search note or category") }, singleLine = true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = acc < 0, onClick = { acc = -1L }, label = { Text("All accounts") })
            accounts.forEach { a -> FilterChip(selected = acc == a.id, onClick = { acc = a.id }, label = { Text(a.name) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("" to "All types", "e" to "Expenses", "i" to "Income", "t" to "Transfers").forEach { (k, l) ->
                FilterChip(selected = type == k, onClick = { type = k }, label = { Text(l) })
            }
        }
        Text("${list.size} entries", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { TxnRow(it, names, onDelete) }
        }
        TextButton(onClick = onExport) { Text("Export all as CSV (share)") }
    }
}

@Composable
fun Report(
    month: YearMonth, onMonth: (YearMonth) -> Unit, txns: List<Txn>, budgets: List<Budget>,
    onSetBudget: (String, Double) -> Unit, onClearBudget: (String) -> Unit
) {
    val mt = inMonth(txns, month)
    val inc = mt.filter { it.type == "i" }.sumOf { it.amount }
    val exp = mt.filter { it.type == "e" }.sumOf { it.amount }
    val net = inc - exp
    val spent = catTotals(mt)
    val days = if (month == YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth()
    val saved = if (inc > 0) maxOf(0, ((net / inc) * 100).toInt()) else 0
    val cats = spent.entries.sortedByDescending { it.value }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { MonthBar(month, onMonth) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    Column { Text("In"); Text(inr(inc), color = Green, fontWeight = FontWeight.Bold) }
                    Column { Text("Out"); Text(inr(exp), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    Column { Text("Left"); Text(inr(net), fontWeight = FontWeight.Bold,
                        color = if (net < 0) MaterialTheme.colorScheme.error else Green) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Average per day", style = MaterialTheme.typography.bodySmall)
                        Text(inr(exp / days), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Saved (of income)", style = MaterialTheme.typography.bodySmall)
                        Text("$saved%", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item { Text("Where it went", style = MaterialTheme.typography.titleMedium) }
        if (cats.isEmpty()) item { Text("No spending this month.") }
        else items(cats, key = { "c" + it.key }) { e ->
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(e.key.ifBlank { "Other" })
                    Text("${inr(e.value)} · ${(e.value / exp * 100).toInt()}%")
                }
                LinearProgressIndicator(progress = { (e.value / cats[0].value).toFloat() }, Modifier.fillMaxWidth())
            }
        }
        item {
            Column {
                Text("Monthly budgets", style = MaterialTheme.typography.titleMedium)
                Text("Type 0 and tap Set to remove a limit", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(EXPENSE_CATS, key = { "b$it" }) { c ->
            BudgetRow(c, budgets.find { it.category == c }, spent[c] ?: 0.0, onSetBudget, onClearBudget)
        }
    }
}

@Composable
fun BudgetRow(c: String, b: Budget?, spent: Double, onSet: (String, Double) -> Unit, onClear: (String) -> Unit) {
    var txt by remember(c) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(c)
            Text(if (b != null) "${inr(spent)} of ${inr(b.monthlyLimit)}" else "No limit",
                style = MaterialTheme.typography.bodySmall)
        }
        if (b != null) {
            val p = (spent / b.monthlyLimit).toFloat()
            LinearProgressIndicator(
                progress = { p.coerceIn(0f, 1f) },
                color = if (p >= 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(txt, { txt = it }, Modifier.weight(1f),
                label = { Text("Monthly limit ₹") }, singleLine = true, keyboardOptions = Decimal)
            TextButton(onClick = {
                val v = txt.toDoubleOrNull()
                if (v != null) {
                    if (v > 0) onSet(c, v) else onClear(c)
                    txt = ""
                }
            }) { Text("Set") }
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
fun AddDialog(accounts: List<Account>, onDismiss: () -> Unit, onSave: (Txn, Boolean) -> Unit) {
    if (accounts.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add an account first") },
            text = { Text("Go to Home and add a bank account, wallet or cash.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
    } else AddForm(accounts, onDismiss, onSave)
}

@Composable
fun AddForm(accounts: List<Account>, onDismiss: () -> Unit, onSave: (Txn, Boolean) -> Unit) {
    var type by remember { mutableStateOf("e") }
    var amt by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(0) }
    var to by remember { mutableStateOf(if (accounts.size > 1) 1 else 0) }
    var cat by remember(type) { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var rep by remember { mutableStateOf(false) }
    val cats = if (type == "i") INCOME_CATS else EXPENSE_CATS
    val names = accounts.map { it.name }
    val amount = amt.toDoubleOrNull()
    val d = runCatching { LocalDate.parse(date) }.getOrNull()
    val ok = amount != null && amount > 0 && d != null && (type != "t" || from != to)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("e" to "Expense", "i" to "Income", "t" to "Transfer").forEach { (k, l) ->
                        FilterChip(selected = type == k, onClick = { type = k }, label = { Text(l) })
                    }
                }
                OutlinedTextField(amt, { amt = it }, Modifier.fillMaxWidth(),
                    label = { Text("Amount ₹") }, singleLine = true, keyboardOptions = Decimal)
                Pick(if (type == "t") "From" else "Account", names, from) { from = it }
                if (type == "t") Pick("To", names, to) { to = it } else Pick("Category", cats, cat) { cat = it }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(),
                    label = { Text("Note (e.g. Swiggy)") }, singleLine = true)
                OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(),
                    label = { Text("Date (yyyy-mm-dd)") }, singleLine = true, isError = d == null)
                if (type != "t") Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rep, onCheckedChange = { rep = it })
                    Text("Repeat every month")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onSave(
                    Txn(
                        type = type, amount = amount!!, accountId = accounts[from].id,
                        toAccountId = if (type == "t") accounts[to].id else null,
                        category = if (type == "t") "" else cats[cat],
                        note = note.trim(), date = d!!.toString()
                    ),
                    rep && type != "t"
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
