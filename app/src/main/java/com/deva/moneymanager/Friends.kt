@file:OptIn(ExperimentalMaterial3Api::class)

package com.deva.moneymanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun Friends(
    persons: List<Person>, txns: List<Txn>, accounts: List<Account>,
    onBack: () -> Unit, onAddPerson: (String) -> Unit, onDeletePerson: (Long) -> Unit,
    onSettle: (Txn) -> Unit, onDeleteTxn: (Long) -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(-1L) }
    val p = persons.firstOrNull { it.id == selected }
    if (p != null) PersonDetail(p, txns, accounts, { selected = -1L }, onDeletePerson, onSettle, onDeleteTxn)
    else FriendList(persons, txns, onBack, onAddPerson) { selected = it }
}

@Composable
private fun FriendList(
    persons: List<Person>, txns: List<Txn>, onBack: () -> Unit,
    onAddPerson: (String) -> Unit, onOpen: (Long) -> Unit
) {
    val bal = persons.associate { it.id to owed(it.id, txns) }
    val toYou = bal.values.filter { it > 0.005 }.sum()
    val youOwe = -bal.values.filter { it < -0.005 }.sum()
    var adding by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Ic.Left, "Back") }
                Header("Friends", "Shared money and who owes whom")
            }
        }
        item {
            Panel {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Muted("Owed to you"); Text(inr(toYou), color = Good, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Muted("You owe"); Text(inr(youOwe), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (persons.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🤝", fontSize = 40.sp)
                Muted("No friends yet. Add one here, or choose \"Split with a friend\" when you add an expense.")
            }
        }
        items(persons, key = { "p${it.id}" }) { p ->
            val b = bal[p.id] ?: 0.0
            Panel {
                Row(Modifier.clickable { onOpen(p.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.name.take(1).uppercase(), MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name)
                        Muted(when { b > 0.005 -> "owes you"; b < -0.005 -> "you owe"; else -> "all settled" })
                    }
                    Text(
                        if (abs(b) < 0.005) "—" else inr(abs(b)), fontWeight = FontWeight.SemiBold,
                        color = if (b > 0.005) Good else if (b < -0.005) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("+ Add friend")
            }
        }
    }
    if (adding) NameDialog("Add friend", { adding = false }) { onAddPerson(it); adding = false }
}

@Composable
private fun PersonDetail(
    p: Person, txns: List<Txn>, accounts: List<Account>, onBack: () -> Unit,
    onDeletePerson: (Long) -> Unit, onSettle: (Txn) -> Unit, onDeleteTxn: (Long) -> Unit
) {
    val rows = txns.filter { it.personId == p.id }
    val b = owed(p.id, txns)
    val names = accounts.associate { it.id to it.name }
    var dir by remember { mutableStateOf("") }
    val tone = if (b > 0.005) Good else if (b < -0.005) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Ic.Left, "Back") }
                Header(p.name, "Shared money")
            }
        }
        item {
            Panel {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muted(when { b > 0.005 -> "${p.name} owes you"; b < -0.005 -> "You owe ${p.name}"; else -> "All settled" })
                    Text(inr(abs(b)), style = MaterialTheme.typography.headlineMedium, color = tone)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { dir = "si" }, Modifier.weight(1f)) { Text("They paid me") }
                OutlinedButton(onClick = { dir = "so" }, Modifier.weight(1f)) { Text("I paid them") }
            }
        }
        item { SectionTitle("History") }
        if (rows.isEmpty()) item { Muted("Nothing yet.") }
        items(rows, key = { "f${it.id}" }) { t -> TxnRow(t, names, {}, onDeleteTxn) }
        if (rows.isEmpty()) item {
            TextButton(onClick = { onDeletePerson(p.id); onBack() }) { Text("Remove ${p.name}") }
        }
    }
    if (dir.isNotEmpty()) {
        val suggest = if (dir == "si" && b > 0.005) b else if (dir == "so" && b < -0.005) -b else 0.0
        SettleDialog(p.name, dir, suggest, accounts, { dir = "" }) { t ->
            onSettle(t.copy(personId = p.id))
            dir = ""
        }
    }
}

@Composable
private fun SettleDialog(
    name: String, dir: String, suggest: Double, accounts: List<Account>,
    onDismiss: () -> Unit, onOk: (Txn) -> Unit
) {
    var amt by remember { mutableStateOf(if (suggest > 0) plain(suggest) else "") }
    var acc by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    val a = amt.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dir == "si") "$name paid you" else "You paid $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amt, { amt = it }, Modifier.fillMaxWidth(),
                    label = { Text("Amount (${sym()})") }, singleLine = true, keyboardOptions = Decimal)
                if (accounts.isNotEmpty()) {
                    Pick(if (dir == "si") "Received in" else "Paid from", accounts.map { it.name }, acc,
                        accounts.map { acctEmoji(it.name) }) { acc = it }
                }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(),
                    label = { Text("Note (optional)") }, singleLine = true)
                Muted("This moves your account balance but is not counted as income or spending.")
            }
        },
        confirmButton = {
            TextButton(enabled = a != null && a > 0 && accounts.isNotEmpty(), onClick = {
                onOk(Txn(type = dir, amount = a!!, accountId = accounts[acc].id, note = note.trim(),
                    date = LocalDate.now().toString()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NameDialog(title: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var n by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = n.isNotBlank(), onClick = { onOk(n.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
