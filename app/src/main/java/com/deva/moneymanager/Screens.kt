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
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

fun monthTitle(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.year

@Composable
fun MonthBar(m: YearMonth, onChange: (YearMonth) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(m.minusMonths(1)) }) { Icon(Ic.Left, "Previous month") }
        TextButton(onClick = { open = true }) {
            Text(monthTitle(m), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(2.dp))
            Icon(Ic.Down, "Choose month")
        }
        IconButton(onClick = { onChange(m.plusMonths(1)) }) { Icon(Ic.Right, "Next month") }
    }
    if (open) MonthPicker(m, { open = false }) { onChange(it); open = false }
}

@Composable
fun MonthPicker(m: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    var year by remember { mutableStateOf(m.year) }
    AppDialog("Choose month", onDismiss, { onPick(YearMonth.now()) }, emoji = "📅", confirm = "This month", dismiss = "Close") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { year -= 1 }) { Icon(Ic.Left, "Previous year") }
            Text(year.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { year += 1 }) { Icon(Ic.Right, "Next year") }
        }
        (0 until 4).forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until 3).forEach { c ->
                    val mo = r * 3 + c + 1
                    val ym = YearMonth.of(year, mo)
                    FilterChip(
                        selected = ym == m, onClick = { onPick(ym) }, modifier = Modifier.weight(1f),
                        label = {
                            Text(Month.of(mo).getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TxnRow(t: Txn, names: Map<Long, String>, onEdit: (Txn) -> Unit, onDelete: (Long) -> Unit) {
    val isT = t.type == "t"
    val isS = t.type.startsWith("s")
    val splitPeople = if (t.splitBy.isNotBlank()) parseSplitMap(t.splitBy).keys.mapNotNull { peopleMap[it] } else emptyList()
    val who = if (splitPeople.isNotEmpty()) splitPeople.joinToString(", ") else t.personId?.let { peopleMap[it] } ?: ""
    val sign = when (t.type) { "e", "so" -> "−"; "i", "si" -> "+"; else -> "" }
    val color = when (t.type) {
        "e", "so" -> MaterialTheme.colorScheme.error
        "i", "si" -> Good
        else -> MaterialTheme.colorScheme.onSurface
    }
    val to = t.toAccountId?.let { names[it] } ?: ""
    val acct = names[t.accountId] ?: ""
    val where = if (isT) "$acct → $to" else acct
    val extra = when {
        t.type == "e" && t.splitBy.isNotBlank() && splitPeople.isNotEmpty() ->
            "with ${splitPeople.joinToString(", ")} · shared ${inr0(txnSplitTotal(t))}"
        t.type == "e" && t.share > 0 && who.isNotBlank() -> "with $who · their share ${inr0(t.share)}"
        else -> ""
    }
    val title = when {
        t.type == "si" -> "$who paid you"
        isS -> "You paid $who"
        isT -> "Transfer"
        else -> t.category
    }
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.clickable { onEdit(t) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(if (isS) "🤝" else if (isT) "🔁" else catEmoji(t.category),
                if (isS || isT) MaterialTheme.colorScheme.secondary else catColor(t.category))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Muted(listOf(t.note, where, extra).filter { it.isNotBlank() }.joinToString(" · "))
            }
            Text(sign + inr(t.amount), color = color, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { onDelete(t.id) }) {
                Icon(Ic.Delete, "Delete entry", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
    persons: List<Person>, onOpenFriends: () -> Unit,
    onQuick: (String) -> Unit, onSeeAll: () -> Unit,
    onEditTxn: (Txn) -> Unit, onEditAccount: (Long, String, Double) -> Unit,
    onAddAccount: (String, Double) -> Unit, onDeleteAccount: (Long) -> Unit,
    onDeleteTxn: (Long) -> Unit, onDeleteRec: (Long) -> Unit
) {
    val now = YearMonth.now()
    val mt = inMonth(txns, now)
    val inc = mt.filter { it.type == "i" }.sumOf { it.amount }
    val exp = mt.filter { it.type == "e" }.sumOf { it.amount - if (it.splitBy.isNotBlank()) txnSplitTotal(it) else it.share }
    val spent = catTotals(mt)
    val names = accounts.associate { it.id to it.name }
    val bank = accounts.sumOf { balance(it, txns) }
    val invVal = holdings.sumOf { it.currentValue }
    val owedList = persons.map { owed(it.id, txns) }
    val owedToYou = owedList.filter { it > 0.005 }.sum()
    val youOwe = -owedList.filter { it < -0.005 }.sum()
    val alerts = budgets.filter { (spent[it.category] ?: 0.0) >= it.monthlyLimit * 0.8 }
    val hour = LocalTime.now().hour
    val greet = if (hour < 12) "Good morning" else if (hour < 17) "Good afternoon" else "Good evening"
    var dialog by remember { mutableStateOf(false) }
    var editAcc by remember { mutableStateOf<Account?>(null) }
    var removeAcc by remember { mutableStateOf<Account?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Header(greet, "Here is your money at a glance") }
        if (accounts.isEmpty()) item {
            Panel {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("👋", fontSize = 44.sp)
                    Text("Welcome to My Money", style = MaterialTheme.typography.titleLarge)
                    Text("Add your bank account, wallet or cash to start tracking where your money goes.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { dialog = true }) { Text("Add your first account") }
                }
            }
        } else {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF1E3A8A)))).padding(20.dp)
                ) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Net worth", color = Color.White.copy(alpha = 0.8f))
                            IconButton(onClick = { showAmounts = !showAmounts }) {
                                Icon(if (showAmounts) Ic.EyeOff else Ic.Eye, if (showAmounts) "Hide amounts" else "Show amounts", tint = Color.White)
                            }
                        }
                        Text(hide(inr(bank + invVal + owedToYou - youOwe)), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            HeroStat("Bank & wallets", hide(inr0(bank)))
                            HeroStat("Investments", hide(inr0(invVal)))
                            HeroStat("Left this month", hide(inr0(inc - exp)))
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Triple("e", "Expense", "💸"), Triple("i", "Income", "💰"), Triple("t", "Transfer", "🔁")).forEach { (k, l, e) ->
                        Surface(
                            onClick = { onQuick(k) }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(e, fontSize = 22.sp)
                                Text(l, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    onClick = onOpenFriends, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar("🤝", MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Friends & shared money")
                            Muted(if (persons.isEmpty()) "Track what friends owe you, or you owe them"
                            else "Owed to you ${inr0(owedToYou)} · You owe ${inr0(youOwe)}")
                        }
                        Icon(Ic.Right, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (alerts.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ic.Alert, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(6.dp))
                            Text("Budget alerts · " + monthTitle(now), fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        alerts.forEach { b ->
                            val s = spent[b.category] ?: 0.0
                            Text(if (s >= b.monthlyLimit) "🔴 ${b.category}: over by ${inr(s - b.monthlyLimit)}"
                            else "🟠 ${b.category}: ${(s / b.monthlyLimit * 100).toInt()}% used",
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Accounts")
                    TextButton(onClick = { dialog = true }) {
                        Icon(Ic.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add")
                    }
                }
            }
            items(accounts, key = { "a${it.id}" }) { a ->
                Panel {
                    Row(Modifier.clickable { editAcc = a }.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AvatarIcon(acctIcon(a.name), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name)
                            Text(hide(inr(balance(a, txns))), style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(Ic.Edit, "Edit account", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (accounts.size > 1 && txns.none { it.accountId == a.id || it.toAccountId == a.id }) {
                            IconButton(onClick = { removeAcc = a }) {
                                Icon(Ic.Delete, "Remove account", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else Spacer(Modifier.width(8.dp))
                    }
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
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Recent")
                    TextButton(onClick = onSeeAll) { Text("See all") }
                }
            }
            val recent = txns.take(6)
            if (recent.isEmpty()) item { Muted("No entries yet. Tap + to add one.") }
            else items(recent, key = { "t${it.id}" }) { TxnRow(it, names, onEditTxn, onDeleteTxn) }
        }
    }

    if (dialog) {
        var name by remember { mutableStateOf("") }
        var bal by remember { mutableStateOf("") }
        AppDialog(
            "Add account", { dialog = false },
            { if (name.isNotBlank()) { onAddAccount(name.trim(), bal.toDoubleOrNull() ?: 0.0); dialog = false } },
            emoji = "🏦", confirm = "Add", confirmEnabled = name.isNotBlank()
        ) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                label = { Text("Name (e.g. HDFC Savings)") }, singleLine = true)
            OutlinedTextField(bal, { bal = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                label = { Text("Current balance (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
        }
    }
    editAcc?.let { a ->
        EditAccountDialog(a, balance(a, txns), { editAcc = null }) { n, actual ->
            onEditAccount(a.id, n, a.opening + (actual - balance(a, txns)))
            editAcc = null
        }
    }
    removeAcc?.let { a ->
        ConfirmDialog("Remove ${a.name}?", "This account has no entries. It will be removed from your accounts.", "Remove", "🏦",
            { removeAcc = null }) { onDeleteAccount(a.id); removeAcc = null }
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
            (type.isEmpty() || it.type.startsWith(type)) &&
            (q.isBlank() || (it.category + " " + it.note).contains(q.trim(), ignoreCase = true))
    }
    val clearIcon: (@Composable () -> Unit)? =
        if (q.isNotBlank()) { { IconButton(onClick = { q = "" }) { Icon(Ic.Close, "Clear search") } } } else null
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Header("History", "${list.size} entries this month")
            IconButton(onClick = onExport) { Icon(Ic.Share, "Share all entries as CSV") }
        }
        MonthBar(month, onMonth)
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            label = { Text("Search note or category") }, singleLine = true,
            leadingIcon = { Icon(Ic.Search, null) }, trailingIcon = clearIcon)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pick("Account", listOf("All accounts") + accounts.map { it.name },
                if (acc < 0) 0 else accounts.indexOfFirst { it.id == acc } + 1,
                listOf("🏦") + accounts.map { acctEmoji(it.name) }, Modifier.weight(1f)) {
                acc = if (it == 0) -1L else accounts[it - 1].id
            }
            val typeKeys = listOf("", "e", "i", "t", "s")
            Pick("Type", listOf("All types", "Expenses", "Income", "Transfers", "Friends"),
                typeKeys.indexOf(type).coerceAtLeast(0), listOf("📋", "💸", "💰", "🔁", "🤝"), Modifier.weight(1f)) {
                type = typeKeys[it]
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 96.dp)) {
            list.groupBy { it.date }.forEach { (d, rows) ->
                item(key = "h$d") {
                    val day = rows.filter { it.type == "e" }.sumOf { it.amount - if (it.splitBy.isNotBlank()) txnSplitTotal(it) else it.share }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dayLabel(d), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (day > 0) Muted("Spent " + inr0(day))
                    }
                }
                items(rows, key = { "t${it.id}" }) { TxnRow(it, names, onEdit, onDelete) }
            }
            if (list.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔎", fontSize = 36.sp)
                    Muted("Nothing found for this month.")
                }
            }
        }
    }
}

@Composable
fun Trend(month: YearMonth, txns: List<Txn>) {
    val ms = (5 downTo 0).map { month.minusMonths(it.toLong()) }
    val data = ms.map { m ->
        val t = inMonth(txns, m)
        t.filter { it.type == "i" }.sumOf { x -> x.amount } to t.filter { it.type == "e" }.sumOf { x -> x.amount - if (x.splitBy.isNotBlank()) txnSplitTotal(x) else x.share }
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
    val exp = mt.filter { it.type == "e" }.sumOf { it.amount - if (it.splitBy.isNotBlank()) txnSplitTotal(it) else it.share }
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
        items(expenseCats(), key = { "b$it" }) { c ->
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
                label = { Text("Monthly limit (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
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
fun AddSheet(
    accounts: List<Account>, persons: List<Person>, initial: Txn?, startType: String,
    onDismiss: () -> Unit, onSave: (Txn, Boolean, String?) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        if (accounts.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add an account first", style = MaterialTheme.typography.titleLarge)
                Muted("Go to Home and add a bank account, wallet or cash.")
                Button(onClick = onDismiss) { Text("OK") }
            }
        } else AddForm(accounts, persons, initial, startType, onSave)
    }
}

@Composable
fun AddForm(accounts: List<Account>, persons: List<Person>, initial: Txn?, startType: String, onSave: (Txn, Boolean, String?) -> Unit) {
    val editing = initial != null
    val types = listOf("e" to "Expense", "i" to "Income", "t" to "Transfer")
    var type by remember { mutableStateOf(initial?.type ?: startType) }
    var amt by remember { mutableStateOf(initial?.let { plain(it.amount) } ?: "") }
    var from by remember { mutableStateOf(maxOf(0, accounts.indexOfFirst { it.id == initial?.accountId })) }
    var to by remember {
        mutableStateOf(initial?.toAccountId?.let { id -> maxOf(0, accounts.indexOfFirst { it.id == id }) }
            ?: if (accounts.size > 1) 1 else 0)
    }
    val cats = if (type == "i") incomeCats() else expenseCats()
    var cat by remember(type) {
        mutableStateOf(if (initial != null && initial.type == type) maxOf(0, cats.indexOf(initial.category)) else 0)
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember {
        mutableStateOf(initial?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())
    }
    var rep by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(initial?.personId != null || !initial?.splitBy.isNullOrBlank()) }
    val defaultSplitIds = remember(initial?.id, initial?.personId, initial?.splitBy) {
        if (!initial?.splitBy.isNullOrBlank()) parseSplitMap(initial!!.splitBy).keys.toSet()
        else initial?.personId?.let { setOf(it) } ?: emptySet()
    }
    var selectedSplitIds by remember(initial?.id, initial?.personId, initial?.splitBy) { mutableStateOf(defaultSplitIds) }
    var equalSplit by remember { mutableStateOf(true) }
    val customSplit = remember { mutableStateMapOf<Long, String>() }
    val today = LocalDate.now()
    val amount = amt.toDoubleOrNull()
    val selectedSplitList = selectedSplitIds.toList()
    val splitMap: Map<Long, Double> = if (!split || type != "e" || selectedSplitList.isEmpty()) emptyMap() else selectedSplitList.associateWith { id ->
        if (equalSplit) (amount ?: 0.0) / selectedSplitList.size else (customSplit[id]?.toDoubleOrNull() ?: 0.0)
    }.filterValues { it > 0.0 }
    val totalSplit = splitMap.values.sum()
    val splitOn = split && type == "e"
    val splitOk = !splitOn || (selectedSplitList.isNotEmpty() && amount != null && amount > 0 && totalSplit > 0.0 && totalSplit <= amount && splitMap.values.all { it > 0.0 })
    val ok = amount != null && amount > 0 && (type != "t" || from != to) && splitOk
    val tint = when (type) { "e" -> MaterialTheme.colorScheme.error; "i" -> Good; else -> MaterialTheme.colorScheme.primary }
    val names = accounts.map { it.name }
    val acctIcons = accounts.map { acctEmoji(it.name) }

    LaunchedEffect(amount, equalSplit, selectedSplitList.size) {
        if (splitOn && !equalSplit && amount != null && selectedSplitList.isNotEmpty()) {
            val perFriend = amount / selectedSplitList.size
            selectedSplitList.forEach { id ->
                if (customSplit[id].isNullOrBlank()) customSplit[id] = plain(perFriend)
            }
        }
        if (splitOn && equalSplit) {
            selectedSplitList.forEach { id -> customSplit.remove(id) }
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()
            .padding(horizontal = 20.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (editing) "Edit entry" else "New entry", style = MaterialTheme.typography.titleLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            types.forEachIndexed { i, (k, l) ->
                SegmentedButton(selected = type == k, onClick = { type = k },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = types.size)) { Text(l) }
            }
        }
        OutlinedTextField(
            amt, { amt = it }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = Decimal,
            textStyle = MaterialTheme.typography.headlineMedium.copy(color = tint),
            prefix = { Text(sym(), style = MaterialTheme.typography.headlineMedium, color = tint) },
            placeholder = { Text("0", style = MaterialTheme.typography.headlineMedium) },
            shape = RoundedCornerShape(16.dp)
        )
        Pick(if (type == "t") "From" else "Account", names, from, acctIcons) { from = it }
        if (type == "t") Pick("To", names, to, acctIcons) { to = it }
        else Pick("Category", cats, cat, cats.map { catEmoji(it) }) { cat = it }
        if (type == "e") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = split, onCheckedChange = { split = it })
                Spacer(Modifier.width(10.dp))
                Text("Split with a friend")
            }
            if (split) {
                if (persons.isEmpty()) {
                    Muted("Add a few friends first in Friends, then choose them here.")
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        persons.forEach { p ->
                            FilterChip(
                                selected = selectedSplitIds.contains(p.id),
                                onClick = {
                                    selectedSplitIds = if (selectedSplitIds.contains(p.id)) selectedSplitIds - p.id else selectedSplitIds + p.id
                                },
                                label = { Text(p.name) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = equalSplit, onClick = { equalSplit = true }, label = { Text("Equal split") })
                        FilterChip(selected = !equalSplit, onClick = { equalSplit = false }, label = { Text("Custom amounts") })
                    }
                    if (!equalSplit && selectedSplitList.isNotEmpty()) {
                        selectedSplitList.forEach { id ->
                            val friend = persons.firstOrNull { it.id == id } ?: return@forEach
                            OutlinedTextField(
                                customSplit[id] ?: "",
                                { customSplit[id] = it },
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                label = { Text("${friend.name} share (${sym()})") },
                                singleLine = true,
                                keyboardOptions = Decimal
                            )
                        }
                    }
                }
            }
        }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            label = { Text("Note (e.g. Swiggy)") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = date == today, onClick = { date = today }, label = { Text("Today") })
            FilterChip(selected = date == today.minusDays(1), onClick = { date = today.minusDays(1) }, label = { Text("Yesterday") })
            val other = date != today && date != today.minusDays(1)
            FilterChip(selected = other, onClick = { picker = true },
                label = { Text(if (other) dayLabel(date.toString()) else "Pick date") })
        }
        if (type != "t" && !editing && !splitOn) Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = rep, onCheckedChange = { rep = it })
            Spacer(Modifier.width(10.dp))
            Text("Repeat every month")
        }
        Button(
            enabled = ok, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            onClick = {
                val splitData = if (!splitOn || selectedSplitList.isEmpty()) emptyMap() else selectedSplitList.associateWith { id ->
                    if (equalSplit) (amount ?: 0.0) / selectedSplitList.size else (customSplit[id]?.toDoubleOrNull() ?: 0.0)
                }.filterValues { it > 0.0 }
                val primaryFriend = if (splitData.size == 1) splitData.keys.first() else null
                val primaryShare = if (primaryFriend != null) splitData[primaryFriend] ?: 0.0 else 0.0
                onSave(
                    Txn(
                        id = initial?.id ?: 0,
                        type = type, amount = amount!!, accountId = accounts[from].id,
                        toAccountId = if (type == "t") accounts[to].id else null,
                        category = if (type == "t") "" else cats[cat],
                        note = note.trim(), date = date.toString(),
                        personId = if (splitOn && primaryFriend != null) primaryFriend else null,
                        share = if (splitOn && primaryFriend != null) primaryShare else 0.0,
                        splitBy = if (splitOn && splitData.isNotEmpty()) org.json.JSONObject(splitData).toString() else ""
                    ),
                    rep && type != "t" && !editing && !splitOn,
                    null
                )
            }
        ) { Text(if (editing) "Save changes" else "Save") }
    }

    if (picker) {
        val ps = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    ps.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } }
        ) { DatePicker(state = ps) }
    }
}

@Composable
fun EditAccountDialog(a: Account, current: Double, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf(a.name) }
    var bal by remember { mutableStateOf(plain(current)) }
    AppDialog(
        "Edit account", onDismiss, { onSave(name.trim(), bal.toDouble()) }, emoji = "✏️",
        confirmEnabled = name.isNotBlank() && bal.toDoubleOrNull() != null
    ) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            label = { Text("Name") }, singleLine = true)
        OutlinedTextField(bal, { bal = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            label = { Text("Balance now (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
        Muted("Set this to what your bank app shows. Your entries stay as they are.")
    }
}

