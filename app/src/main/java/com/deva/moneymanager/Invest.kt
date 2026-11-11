@file:OptIn(ExperimentalMaterial3Api::class)

package com.deva.moneymanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

private fun units3(u: Double): String = String.format(Locale.ENGLISH, "%.3f", u).trimEnd('0').trimEnd('.')

@Composable
fun Invest(
    holdings: List<Holding>,
    onAdd: (Holding) -> Unit, onTopUp: (Long, Double) -> Unit,
    onSave: (Holding) -> Unit, onDelete: (Long) -> Unit, onRefresh: () -> Unit
) {
    val inv = holdings.sumOf { it.invested }
    val cur = holdings.sumOf { it.currentValue }
    val pl = cur - inv
    val ret = if (inv > 0) pl / inv * 100 else 0.0
    val plColor = if (pl >= 0) Good else MaterialTheme.colorScheme.error
    var adding by remember { mutableStateOf(false) }
    var topFor by remember { mutableStateOf<Holding?>(null) }
    var buyFor by remember { mutableStateOf<Holding?>(null) }
    var editFor by remember { mutableStateOf<Holding?>(null) }
    var delFor by remember { mutableStateOf<Holding?>(null) }
    val byKind = holdings.groupBy { it.kind }.map { (k, v) -> k to v.sumOf { h -> h.currentValue } }
        .sortedByDescending { it.second }
    val updated = holdings.map { it.priceDate }.filter { it.isNotBlank() }.maxOrNull()
    val anyLive = holdings.any { it.source.isNotEmpty() && it.units > 0 }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Header(
                    "Investments",
                    if (refreshingPrices) "Updating prices…" else if (updated != null) "Prices as of $updated" else "Watch your money grow"
                )
                Row {
                    if (anyLive) IconButton(onClick = onRefresh) { Icon(Ic.Sync, "Refresh prices") }
                    IconButton(onClick = { showAmounts = !showAmounts }) {
                        Icon(if (showAmounts) Ic.EyeOff else Ic.Eye, if (showAmounts) "Hide amounts" else "Show amounts")
                    }
                }
            }
        }
        item {
            Panel {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muted("Current value")
                    Text(hide(inr(cur)), style = MaterialTheme.typography.headlineMedium)
                    Text(hide("${signed(pl)}  (${pct(ret)})"), color = plColor, fontWeight = FontWeight.SemiBold)
                    Muted("Total invested " + hide(inr(inv)))
                }
            }
        }
        if (byKind.isNotEmpty()) item {
            Panel {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Where it is invested", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(byKind.mapIndexed { i, p -> p.second to PALETTE[i % PALETTE.size] }, 120.dp)
                        Spacer(Modifier.width(16.dp))
                        Box(Modifier.weight(1f)) {
                            Legend(byKind.mapIndexed { i, p -> Triple(p.first, p.second, PALETTE[i % PALETTE.size]) }, mask = true)
                        }
                    }
                }
            }
        }
        if (holdings.isEmpty()) item { Muted("No investments yet. Add your mutual funds, stocks, FDs, gold and more.") }
        items(holdings, key = { "h${it.id}" }) { h ->
            val p = h.currentValue - h.invested
            val r = if (h.invested > 0) p / h.invested * 100 else 0.0
            val c = if (p >= 0) Good else MaterialTheme.colorScheme.error
            val live = h.source.isNotEmpty() && h.units > 0
            Panel {
                Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(kindEmoji(h.kind), MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Muted(
                                if (live) "${h.kind} · ${units3(h.units)} units · " + hide(inr(h.currentValue / h.units)) + " each"
                                else "${h.kind} · since ${h.startDate}"
                            )
                            Muted("Invested " + hide(inr(h.invested)))
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                            Text(hide(inr(h.currentValue)), fontWeight = FontWeight.Bold)
                            Text(hide("${signed(p)} (${pct(r)})"), style = MaterialTheme.typography.bodySmall, color = c)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (live) TextButton(onClick = { buyFor = h }) { Text("Buy more") }
                        else TextButton(onClick = { topFor = h }) { Text("Add money") }
                        TextButton(onClick = { editFor = h }) { Text("Edit") }
                        TextButton(onClick = { delFor = h }) { Text("Remove") }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("+ Add investment")
            }
        }
    }

    if (adding) AddHoldingDialog({ adding = false }) { onAdd(it); adding = false }
    topFor?.let { h ->
        AmountDialog("Add money to ${h.name}", "Amount invested (${sym()})", { topFor = null }) { v -> onTopUp(h.id, v); topFor = null }
    }
    buyFor?.let { h -> BuyMoreDialog(h, { buyFor = null }) { onSave(it); buyFor = null } }
    editFor?.let { h -> EditHoldingDialog(h, { editFor = null }) { onSave(it); editFor = null } }
    delFor?.let { h ->
        ConfirmDialog("Remove ${h.name}?", "This investment and its record will be deleted.", "Remove", "📈",
            { delFor = null }) { onDelete(h.id); delFor = null }
    }
}

@Composable
fun AddHoldingDialog(onDismiss: () -> Unit, onSave: (Holding) -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(0) }
    val kindName = INVEST_KINDS[kind]
    val source = when (kindName) { "Mutual fund" -> "mf"; "Stock" -> "yahoo"; else -> "" }
    var manual by remember { mutableStateOf(false) }
    val live = source.isNotEmpty() && !manual
    var query by remember { mutableStateOf("") }
    var hits by remember(kind) { mutableStateOf<List<Hit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var msg by remember(kind) { mutableStateOf("") }
    var picked by remember(kind) { mutableStateOf<Hit?>(null) }
    var quote by remember(kind) { mutableStateOf<Quote?>(null) }
    var name by remember { mutableStateOf("") }
    var inv by remember { mutableStateOf("") }
    var cur by remember { mutableStateOf("") }
    var unitsTxt by remember { mutableStateOf("") }
    val i = inv.toDoubleOrNull()
    val u = unitsTxt.toDoubleOrNull()
    val q = quote

    fun runSearch() {
        if (query.trim().length < 2) return
        searching = true
        msg = ""
        hits = emptyList()
        scope.launch {
            val r = if (source == "mf") Prices.searchFunds(query.trim()) else Prices.searchStocks(query.trim())
            searching = false
            if (r == null) msg = "Could not reach the price service. Check your internet, or add it by hand."
            else if (r.isEmpty()) msg = "Nothing found. Try another word, or add it by hand."
            else hits = r
        }
    }

    fun pick(h: Hit) {
        picked = h
        hits = emptyList()
        quote = null
        msg = ""
        scope.launch {
            val r = Prices.latest(source, h.symbol)
            if (r == null) msg = "Could not get the latest price. Try again, or add it by hand."
            else if (r.currency != null && r.currency != currencyCode) {
                msg = "This is priced in ${r.currency} but your app currency is $currencyCode. Change the currency in More, or add it by hand."
                picked = null
            } else quote = r
        }
    }

    AppDialog(
        "Add investment", onDismiss,
        {
            if (live) {
                onSave(Holding(name = picked!!.name, kind = kindName, invested = i!!, currentValue = u!! * q!!.price,
                    startDate = LocalDate.now().toString(), symbol = picked!!.symbol, source = source,
                    units = u, priceDate = q.date))
            } else {
                onSave(Holding(name = name.trim(), kind = kindName, invested = i!!, currentValue = cur.toDoubleOrNull() ?: i,
                    startDate = LocalDate.now().toString()))
            }
        },
        emoji = "📈", confirm = "Add",
        confirmEnabled = if (live) picked != null && q != null && u != null && u > 0 && i != null && i > 0
        else name.isNotBlank() && i != null && i > 0
    ) {
        Pick("Type", INVEST_KINDS, kind, INVEST_KINDS.map { kindEmoji(it) }) { kind = it }
        if (live) {
            if (picked == null) {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true,
                    label = { Text(if (source == "mf") "Search fund (e.g. HDFC Flexi Cap)" else "Search company or symbol") },
                    trailingIcon = { IconButton(onClick = { runSearch() }) { Icon(Ic.Search, "Search") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() })
                )
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (hits.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hits.take(8).forEach { h ->
                        Surface(onClick = { pick(h) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(10.dp)) {
                                Text(h.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Muted(h.extra)
                            }
                        }
                    }
                }
                if (source == "yahoo") Muted("Stock prices come from a free public feed. They can be a few minutes late.")
            } else {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(picked!!.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                if (q != null) "Latest ${inr(q.price)} on ${q.date}" else "Getting the latest price…",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(onClick = { picked = null; quote = null; msg = "" }) { Text("Change") }
                    }
                }
                if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(unitsTxt, { unitsTxt = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    label = { Text(if (source == "mf") "Units you hold" else "Shares you hold") },
                    singleLine = true, keyboardOptions = Decimal)
                OutlinedTextField(inv, { inv = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    label = { Text("Amount invested (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
                if (q != null && u != null && u > 0) Muted("Worth now: " + inr(u * q.price))
            }
            TextButton(onClick = { manual = true }) { Text("Can't find it? Add by hand") }
        } else {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                label = { Text("Name (e.g. Nifty 50 index fund)") }, singleLine = true)
            OutlinedTextField(inv, { inv = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                label = { Text("Amount invested (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
            OutlinedTextField(cur, { cur = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                label = { Text("Current value (${sym()}) (blank = same)") }, singleLine = true, keyboardOptions = Decimal)
            if (source.isNotEmpty()) TextButton(onClick = { manual = false }) { Text("Search the real list instead") }
        }
    }
}

@Composable
fun EditHoldingDialog(h: Holding, onDismiss: () -> Unit, onSave: (Holding) -> Unit) {
    val live = h.source.isNotEmpty() && h.units > 0
    val price = if (live) h.currentValue / h.units else 0.0
    var name by remember { mutableStateOf(h.name) }
    var kind by remember { mutableStateOf(maxOf(0, INVEST_KINDS.indexOf(h.kind))) }
    var inv by remember { mutableStateOf(plain(h.invested)) }
    var cur by remember { mutableStateOf(plain(h.currentValue)) }
    var unitsTxt by remember { mutableStateOf(units3(h.units)) }
    val i = inv.toDoubleOrNull()
    val c = cur.toDoubleOrNull()
    val u = unitsTxt.toDoubleOrNull()
    val newKind = INVEST_KINDS[kind]
    val detach = live && newKind != h.kind
    AppDialog(
        "Edit investment", onDismiss,
        {
            val units = if (live) u!! else h.units
            val value = if (live) units * price else c!!
            if (detach) onSave(h.copy(name = name.trim(), kind = newKind, invested = i!!, currentValue = value, symbol = "", source = "", units = 0.0))
            else onSave(h.copy(name = name.trim(), kind = newKind, invested = i!!, currentValue = value, units = units))
        },
        emoji = "✏️",
        confirmEnabled = name.isNotBlank() && i != null && (if (live) u != null && u > 0 else c != null)
    ) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            label = { Text("Name") }, singleLine = true)
        Pick("Type", INVEST_KINDS, kind, INVEST_KINDS.map { kindEmoji(it) }) { kind = it }
        if (detach) Muted("Changing the type of a live-priced investment turns off its automatic price. You can add it again from the real list.")
        OutlinedTextField(inv, { inv = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            label = { Text("Amount invested (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
        if (live) {
            OutlinedTextField(unitsTxt, { unitsTxt = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                label = { Text("Units you hold") }, singleLine = true, keyboardOptions = Decimal)
            Muted("The current value updates by itself from the latest price.")
        } else {
            OutlinedTextField(cur, { cur = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                label = { Text("Current value (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
        }
    }
}

@Composable
fun BuyMoreDialog(h: Holding, onDismiss: () -> Unit, onOk: (Holding) -> Unit) {
    var u by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }
    val uu = u.toDoubleOrNull()
    val aa = amt.toDoubleOrNull()
    val price = h.currentValue / h.units
    AppDialog(
        "Buy more", onDismiss,
        { onOk(h.copy(invested = h.invested + aa!!, units = h.units + uu!!, currentValue = (h.units + uu) * price)) },
        emoji = "➕", confirm = "Add", confirmEnabled = uu != null && uu > 0 && aa != null && aa > 0
    ) {
        Text(h.name, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(u, { u = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            label = { Text("Units bought") }, singleLine = true, keyboardOptions = Decimal)
        OutlinedTextField(amt, { amt = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            label = { Text("Amount paid (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
    }
}
