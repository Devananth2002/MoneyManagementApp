@file:OptIn(ExperimentalMaterial3Api::class)

package com.deva.moneymanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun Invest(
    holdings: List<Holding>,
    onAdd: (Holding) -> Unit, onTopUp: (Long, Double) -> Unit,
    onEdit: (Long, String, Double, Double) -> Unit, onDelete: (Long) -> Unit
) {
    val inv = holdings.sumOf { it.invested }
    val cur = holdings.sumOf { it.currentValue }
    val pl = cur - inv
    val ret = if (inv > 0) pl / inv * 100 else 0.0
    val plColor = if (pl >= 0) Good else MaterialTheme.colorScheme.error
    var adding by remember { mutableStateOf(false) }
    var topFor by remember { mutableStateOf<Holding?>(null) }
    var editFor by remember { mutableStateOf<Holding?>(null) }
    var delFor by remember { mutableStateOf<Holding?>(null) }
    val byKind = holdings.groupBy { it.kind }.map { (k, v) -> k to v.sumOf { h -> h.currentValue } }
        .sortedByDescending { it.second }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Header("Investments", "Watch your money grow") }
        item {
            Panel {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muted("Current value")
                    Text(inr(cur), style = MaterialTheme.typography.headlineMedium)
                    Text("${signed(pl)}  (${pct(ret)})", color = plColor, fontWeight = FontWeight.SemiBold)
                    Muted("Total invested ${inr(inv)}")
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
                            Legend(byKind.mapIndexed { i, p -> Triple(p.first, p.second, PALETTE[i % PALETTE.size]) })
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
            Panel {
                Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(kindEmoji(h.kind), MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, style = MaterialTheme.typography.titleSmall)
                            Muted("${h.kind} · since ${h.startDate}")
                            Muted("Invested ${inr(h.invested)}")
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                            Text(inr(h.currentValue), fontWeight = FontWeight.Bold)
                            Text("${signed(p)} (${pct(r)})", style = MaterialTheme.typography.bodySmall, color = c)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { topFor = h }) { Text("Add money") }
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
    editFor?.let { h ->
        EditHoldingDialog(h, { editFor = null }) { n, i, c -> onEdit(h.id, n, i, c); editFor = null }
    }
    delFor?.let { h ->
        ConfirmDialog("Remove ${h.name}?", { delFor = null }) { onDelete(h.id); delFor = null }
    }
}

@Composable
fun AddHoldingDialog(onDismiss: () -> Unit, onSave: (Holding) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(0) }
    var inv by remember { mutableStateOf("") }
    var cur by remember { mutableStateOf("") }
    val i = inv.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add investment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text("Name (e.g. Nifty 50 index fund)") }, singleLine = true)
                Pick("Type", INVEST_KINDS, kind, INVEST_KINDS.map { kindEmoji(it) }) { kind = it }
                OutlinedTextField(inv, { inv = it }, Modifier.fillMaxWidth(),
                    label = { Text("Amount invested (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
                OutlinedTextField(cur, { cur = it }, Modifier.fillMaxWidth(),
                    label = { Text("Current value (${sym()}) (blank = same)") }, singleLine = true, keyboardOptions = Decimal)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && i != null && i > 0, onClick = {
                onSave(Holding(name = name.trim(), kind = INVEST_KINDS[kind], invested = i!!,
                    currentValue = cur.toDoubleOrNull() ?: i, startDate = LocalDate.now().toString()))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditHoldingDialog(h: Holding, onDismiss: () -> Unit, onSave: (String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf(h.name) }
    var inv by remember { mutableStateOf(plain(h.invested)) }
    var cur by remember { mutableStateOf(plain(h.currentValue)) }
    val i = inv.toDoubleOrNull()
    val c = cur.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit investment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(inv, { inv = it }, Modifier.fillMaxWidth(),
                    label = { Text("Amount invested (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
                OutlinedTextField(cur, { cur = it }, Modifier.fillMaxWidth(),
                    label = { Text("Current value (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && i != null && c != null, onClick = { onSave(name.trim(), i!!, c!!) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
