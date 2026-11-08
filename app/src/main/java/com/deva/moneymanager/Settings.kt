@file:OptIn(ExperimentalMaterial3Api::class)

package com.deva.moneymanager

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Currency
import java.util.Locale

private val CURRENCIES = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY")
private val EMOJIS = listOf("🛒", "⛽", "🏋️", "📚", "🎓", "🐶", "✈️", "🎁", "💄", "🧾", "🔧", "🍺", "☕", "🚌", "📱", "🏥")

@Composable
fun Settings(
    lastBackup: String, onBackup: () -> Unit, onRestore: () -> Unit, onCurrency: (String) -> Unit,
    cats: List<Cat>, onAddCat: (String, Boolean, String) -> Unit, onDeleteCat: (Long) -> Unit
) {
    var addingCat by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Header("More", "Settings and backup") }
        item { SectionTitle("My categories") }
        if (cats.isEmpty()) item { Muted("Add your own, like Petrol, Gym or Pocket money. The built-in ones can't be removed.") }
        items(cats, key = { "c${it.id}" }) { c ->
            Panel {
                Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.emoji, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name)
                        Muted(if (c.income) "Income" else "Expense")
                    }
                    IconButton(onClick = { onDeleteCat(c.id) }) {
                        Icon(Ic.Delete, "Remove category", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { addingCat = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("+ Add category")
            }
        }
        item { SectionTitle("Currency") }
        item {
            Panel {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pick(
                        "Currency",
                        CURRENCIES.map { "$it · ${Currency.getInstance(it).displayName}" },
                        CURRENCIES.indexOf(currencyCode).coerceAtLeast(0),
                        CURRENCIES.map { Currency.getInstance(it).getSymbol(Locale.US) }
                    ) { onCurrency(CURRENCIES[it]) }
                    Muted("This changes how amounts are shown. Existing numbers are not converted.")
                }
            }
        }
        item { SectionTitle("Backup & restore") }
        item {
            Panel {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Muted(if (lastBackup.isBlank()) "No backup yet. Save one somewhere safe, like Google Drive."
                    else "Last backup: $lastBackup")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBackup, Modifier.weight(1f)) {
                            Icon(Ic.Cloud, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Back up now")
                        }
                        OutlinedButton(onClick = onRestore, Modifier.weight(1f)) { Text("Restore") }
                    }
                }
            }
        }
        item { SectionTitle("Privacy") }
        item {
            Panel {
                Column(Modifier.padding(14.dp)) {
                    Muted("Your data is stored on this phone and never sent to us. It leaves your phone only if you back it up or share it yourself, or if you have Android's own phone backup turned on.")
                }
            }
        }
        item { SectionTitle("About") }
        item {
            Panel {
                Column(Modifier.padding(14.dp)) {
                    Text("My Money")
                    Muted("Version 0.6")
                }
            }
        }
    }
    if (addingCat) AddCategoryDialog(cats, { addingCat = false }) { n, inc, e -> onAddCat(n, inc, e); addingCat = false }
}

@Composable
private fun AddCategoryDialog(existing: List<Cat>, onDismiss: () -> Unit, onOk: (String, Boolean, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var emoji by remember { mutableStateOf(EMOJIS[0]) }
    val taken = (expenseCats() + incomeCats()).any { it.equals(name.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true,
                    isError = taken, supportingText = { if (taken) Text("You already have this category") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !income, onClick = { income = false }, label = { Text("Expense") })
                    FilterChip(selected = income, onClick = { income = true }, label = { Text("Income") })
                }
                Muted("Pick an icon")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EMOJIS.forEach { e ->
                        FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e, fontSize = 18.sp) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !taken, onClick = { onOk(name.trim(), income, emoji) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
