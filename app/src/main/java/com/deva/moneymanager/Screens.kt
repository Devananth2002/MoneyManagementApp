@file:OptIn(ExperimentalMaterial3Api::class)

package com.deva.moneymanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

fun monthTitle(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.year

@Composable
fun MonthBar(m: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { onChange(m.minusMonths(1)) }) { Text("‹") }
        Text(monthTitle(m), style = MaterialTheme.typography.titleMedium)
        FilledTonalButton(onClick = { onChange(m.plusMonths(1)) }) { Text("›") }
    }
}

@Composable
fun TxnRow(t: Txn, names: Map<Long, String>, onEdit: (Txn) -> Unit, onDelete: (Long) -> Unit) {
    var ask by remember { mutableStateOf(false) }
    val isT = t.type == "t"
    val sign = when (t.type) { "e" -> "−"; "i" -> "+"; else -> "" }
    val color = when (t.type) {
        "e" -> MaterialTheme.colorScheme.error
        "i" -> Good
        else -> MaterialTheme.colorScheme.onSurface
    }
    val to = t.toAccountId?.let { names[it] } ?: ""
    val where = if (isT) "${names[t.accountId] ?: ""} → $to" else (names[t.accountId] ?: "")
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.clickable { onEdit(t) }.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(if (isT) "🔁" else catEmoji(t.category), if (isT) MaterialTheme.colorScheme.secondary else catColor(t.category))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isT) "Transfer" else t.category, style = MaterialTheme.typography.titleSmall)
                Muted(listOf(t.note, where).filter { it.isNotBlank() }.joinToString(" · "))
            }
            Text(sign + inr(t.amount), color = color, fontWeight = FontWeight.SemiBold)
            Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { ask = true }.padding(12.dp))
        }
    }
    if (ask) ConfirmDialog("Delete this entry?", { ask = false }) { onDelete(t.id); ask = false }
}

@Composable
fun HeroStat(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Home(
    accounts: List<Account>, txns: List<Txn>, budgets: List<Budget>, recs: List<Recurring>, holdings: List<Holding>,
    lastBackup: String, onBackup: () -> Unit, onRestore: () -> Unit,
    onEditTxn: (Txn) -> Unit, onEditAccount: (Long, String, Double) -> Unit,
    onAddAccount: (String, Double) -> Unit, onDeleteAccount: (Long) -> Unit,
    onDeleteTxn: (Long) -> Unit, onDeleteRec: (Long) -> Unit
) {
    val now = YearMonth.now()
    val mt = inMonth(txns, now)
    val inc = mt.filter { it.type == "i" }.sumOf { it.amount }
    val exp = mt.filter { it.type == "e" }.sumOf { it.amount }
    val spent = catTotals(mt)
    val names = accounts.associate { it.id to it.name }
    val bank = accounts.sumOf { balance(it, txns) }
    val invVal = holdings.sumOf { it.currentValue }
    val alerts = budgets.filter { (spent[it.category] ?: 0.0) >= it.monthlyLimit * 0.8 }
    var dialog by remember { mutableStateOf(false) }
    var editAcc by remember { mutableStateOf<Account?>(null) }
    val needBackup = accounts.isNotEmpty() && (lastBackup.isBlank() ||
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(lastBackup), LocalDate.now()) > 7 }.getOrDefault(true))

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Header("My Money", "Everything you own, in one place") }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF1E3A8A)))).padding(20.dp)
            ) {
                Column {
                    Text("Net worth", color = Color.White.copy(alpha = 0.8f))
                    Text(inr(bank + invVal), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        HeroStat("Bank & wallets", inr0(bank))
                        HeroStat("Investments", inr0(invVal))
                        HeroStat("Left this month", inr0(inc - exp))
                    }
                }
            }
        }
        if (needBackup) item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your data lives only on this phone. Save a backup.", Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    TextButton(onClick = onBackup) { Text("Back up") }
                }
            }
        }
        if (alerts.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Budget alerts · " + monthTitle(now), fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    alerts.forEach { b ->
                        val s = spent[b.category] ?: 0.0
                        Text(if (s >= b.monthlyLimit) "🔴 ${b.category}: over by ${inr(s - b.monthlyLimit)}"
                        else "🟠 ${b.category}: ${(s / b.monthlyLimit * 100).toInt()}% used",
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        item { SectionTitle("Accounts") }
        if (accounts.isEmpty()) item { Muted("No accounts yet. Add your first bank account below.") }
        items(accounts, key = { "a${it.id}" }) { a ->
            Panel {
                Row(Modifier.clickable { editAcc = a }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(acctEmoji(a.name), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name)
                        Text(inr(balance(a, txns)), style = MaterialTheme.typography.titleMedium)
                    }
                    if (accounts.size > 1 && txns.none { it.accountId == a.id || it.toAccountId == a.id }) {
                        TextButton(onClick = { onDeleteAccount(a.id) }) { Text("Remove") }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { dialog = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("+ Add bank account / wallet / cash")
            }
        }
        item { SectionTitle("This month · " + monthTitle(now)) }
        item {
            Panel {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Muted("Money in"); Text(inr(inc), color = Good, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Muted("Money out"); Text(inr(exp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (recs.isNotEmpty()) {
            item { SectionTitle("Repeats every month") }
            items(recs, key = { "r${it.id}" }) { r ->
                Panel {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar("🔁", MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${r.category} · ${inr(r.amount)}")
                            Muted("${names[r.accountId] ?: ""} · day ${r.day}")
                        }
                        TextButton(onClick = { onDeleteRec(r.id) }) { Text("Stop") }
                    }
                }
            }
        }
        item { SectionTitle("Recent") }
        val recent = txns.take(6)
        if (recent.isEmpty()) item { Muted("No entries yet. Tap + to add one.") }
        else items(recent, key = { "t${it.id}" }) { TxnRow(it, names, onEditTxn, onDeleteTxn) }
        item { SectionTitle("Backup & restore") }
        item {
            Panel {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Muted(if (lastBackup.isBlank()) "No backup yet. Save one somewhere safe, like Google Drive."
                    else "Last backup: $lastBackup")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBackup, Modifier.weight(1f)) { Text("Back up now") }
                        OutlinedButton(onClick = onRestore, Modifier.weight(1f)) { Text("Restore") }
                    }
                }
            }
        }
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
    editAcc?.let { a ->
        EditAccountDialog(a, balance(a, txns), { editAcc = null }) { n, actual ->
            onEditAccount(a.id, n, a.opening + (actual - balance(a, txns)))
            editAcc = null
        }
    }
}

@Composable
fun History(
    month: YearMonth, onMonth: (YearMonth) -> Unit, accounts: List<Account>, txns: List<Txn>,
    onEdit: (Txn) -> Unit, onDelete: (Long) -> Unit, onExport: () -> Unit
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(4.dp))
        Header("History", "${list.size} entries this month")
        MonthBar(month, onMonth)
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
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
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)) {
            list.groupBy { it.date }.forEach { (d, rows) ->
                item(key = "h$d") {
                    Text(dayLabel(d), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                items(rows, key = { "t${it.id}" }) { TxnRow(it, names, onEdit, onDelete) }
            }
            if (list.isEmpty()) item { Muted("Nothing found for this month.") }
        }
        TextButton(onClick = onExport) { Text("Export all as CSV (share)") }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun Trend(month: YearMonth, txns: List<Txn>) {
    val ms = (5 downTo 0).map { month.minusMonths(it.toLong()) }
    val data = ms.map { m ->
        val t = inMonth(txns, m)
        t.filter { it.type == "i" }.sumOf { x -> x.amount } to t.filter { it.type == "e" }.sumOf { x -> x.amount }
    }
    val mx = maxOf(1.0, data.maxOf { maxOf(it.first, it.second) })
    val inC = Good
    val outC = MaterialTheme.colorScheme.error
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / ms.size
        val bw = slot * 0.28f
        data.forEachIndexed { i, (a, b) ->
            val ha = (a / mx).toFloat() * size.height
            val hb = (b / mx).toFloat() * size.height
            drawRect(inC, Offset(i * slot + slot * 0.5f - bw - 2f, size.height - ha), Size(bw, ha))
            drawRect(outC, Offset(i * slot + slot * 0.5f + 2f, size.height - hb), Size(bw, hb))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        ms.forEach {
            Text(it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), Modifier.weight(1f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Header("Reports", "How your month went") }
        item { MonthBar(month, onMonth) }
        item {
            Panel {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Muted("In"); Text(inr(inc), color = Good, fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Muted("Out"); Text(inr(exp), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Muted("Left")
                        Text(inr(net), fontWeight = FontWeight.Bold, color = if (net < 0) MaterialTheme.colorScheme.error else Good)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) { Muted("Average per day"); Text(inr(exp / days), style = MaterialTheme.typography.titleMedium) }
                }
                Panel(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) { Muted("Saved (of income)"); Text("$saved%", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
        item {
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Last 6 months", style = MaterialTheme.typography.titleMedium)
                    Row {
                        Text("■ ", color = Good); Muted("Money in   ")
                        Text("■ ", color = MaterialTheme.colorScheme.error); Muted("Money out")
                    }
                    Trend(month, txns)
                }
            }
        }
        item {
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Where it went", style = MaterialTheme.typography.titleMedium)
                    if (cats.isEmpty()) Muted("No spending this month.")
                    else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Donut(cats.map { it.value to catColor(it.key) }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Muted("Spent"); Text(inr0(exp), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                        Legend(cats.map { Triple(catEmoji(it.key) + " " + it.key.ifBlank { "Other" }, it.value, catColor(it.key)) })
                    }
                }
            }
        }
        item {
            Column {
                Text("Monthly budgets", style = MaterialTheme.typography.titleMedium)
                Muted("Type 0 and tap Set to remove a limit")
            }
        }
        items(EXPENSE_CATS, key = { "b$it" }) { c ->
            Panel { BudgetRow(c, budgets.find { it.category == c }, spent[c] ?: 0.0, onSetBudget, onClearBudget) }
        }
    }
}

@Composable
fun BudgetRow(c: String, b: Budget?, spent: Double, onSet: (String, Double) -> Unit, onClear: (String) -> Unit) {
    var txt by remember(c) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(catEmoji(c) + " " + c)
            Muted(if (b != null) "${inr(spent)} of ${inr(b.monthlyLimit)}" else "No limit")
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
            OutlinedTextField(txt, { txt = it }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
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
fun AddDialog(accounts: List<Account>, initial: Txn?, onDismiss: () -> Unit, onSave: (Txn, Boolean) -> Unit) {
    if (accounts.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add an account first") },
            text = { Text("Go to Home and add a bank account, wallet or cash.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
    } else AddForm(accounts, initial, onDismiss, onSave)
}

@Composable
fun AddForm(accounts: List<Account>, initial: Txn?, onDismiss: () -> Unit, onSave: (Txn, Boolean) -> Unit) {
    val editing = initial != null
    var type by remember { mutableStateOf(initial?.type ?: "e") }
    var amt by remember { mutableStateOf(initial?.let { plain(it.amount) } ?: "") }
    var from by remember { mutableStateOf(maxOf(0, accounts.indexOfFirst { it.id == initial?.accountId })) }
    var to by remember {
        mutableStateOf(initial?.toAccountId?.let { id -> maxOf(0, accounts.indexOfFirst { it.id == id }) }
            ?: if (accounts.size > 1) 1 else 0)
    }
    val cats = if (type == "i") INCOME_CATS else EXPENSE_CATS
    var cat by remember(type) {
        mutableStateOf(if (initial != null && initial.type == type) maxOf(0, cats.indexOf(initial.category)) else 0)
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var rep by remember { mutableStateOf(false) }
    val names = accounts.map { it.name }
    val amount = amt.toDoubleOrNull()
    val d = runCatching { LocalDate.parse(date) }.getOrNull()
    val ok = amount != null && amount > 0 && d != null && (type != "t" || from != to)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "Edit entry" else "Add entry") },
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
                if (type != "t" && !editing) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rep, onCheckedChange = { rep = it })
                    Text("Repeat every month")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onSave(
                    Txn(
                        id = initial?.id ?: 0,
                        type = type, amount = amount!!, accountId = accounts[from].id,
                        toAccountId = if (type == "t") accounts[to].id else null,
                        category = if (type == "t") "" else cats[cat],
                        note = note.trim(), date = d!!.toString()
                    ),
                    rep && type != "t" && !editing
                )
            }) { Text(if (editing) "Save changes" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditAccountDialog(a: Account, current: Double, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf(a.name) }
    var bal by remember { mutableStateOf(plain(current)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(bal, { bal = it }, Modifier.fillMaxWidth(),
                    label = { Text("Balance now ₹") }, singleLine = true, keyboardOptions = Decimal)
                Muted("Set this to what your bank app shows. Your entries stay as they are.")
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && bal.toDoubleOrNull() != null,
                onClick = { onSave(name.trim(), bal.toDouble()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
