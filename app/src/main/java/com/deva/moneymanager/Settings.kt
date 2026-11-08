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
import androidx.fragment.app.FragmentActivity
import java.util.Currency
import java.util.Locale

private val CURRENCIES = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY")
private val EMOJIS = listOf("🛒", "⛽", "🏋️", "📚", "🎓", "🐶", "✈️", "🎁", "💄", "🧾", "🔧", "🍺", "☕", "🚌", "📱", "🏥")

@Composable
fun Settings(
    lastBackup: String, onBackup: () -> Unit, onRestore: () -> Unit, onCurrency: (String) -> Unit,
    cats: List<Cat>, onAddCat: (String, Boolean, String) -> Unit, onDeleteCat: (Long) -> Unit,
    sec: Security, activity: FragmentActivity, canPhone: Boolean, canBio: Boolean
) {
    var addingCat by remember { mutableStateOf(false) }
    var delCat by remember { mutableStateOf<Cat?>(null) }
    var chooseLock by remember { mutableStateOf(false) }
    var pinMode by remember { mutableStateOf(0) } // 0 none, 1 set a new PIN, 2 change PIN
    var turningOff by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Header("More", "Settings and backup") }

        item { SectionTitle("Security") }
        item {
            Panel {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("App lock")
                            Muted(
                                if (!sec.enabled) "Ask for a PIN or fingerprint when you open the app"
                                else if (sec.method == "pin") "Unlocks with your PIN"
                                else "Unlocks with your fingerprint or phone screen lock"
                            )
                        }
                        Switch(checked = sec.enabled, onCheckedChange = { if (it) chooseLock = true else turningOff = true })
                    }
                    if (sec.enabled && sec.method == "pin") {
                        OutlinedButton(onClick = { pinMode = 2 }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Text("Change PIN")
                        }
                        if (canBio) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Also unlock with fingerprint", Modifier.weight(1f))
                            Switch(checked = sec.bio, onCheckedChange = { sec.setBio(it) })
                        }
                    }
                    if (sec.enabled) Muted("While the lock is on, the app also hides its screen in recent apps and blocks screenshots.")
                }
            }
        }

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
                    IconButton(onClick = { delCat = c }) {
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
                        Button(onClick = onBackup, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                            Icon(Ic.Cloud, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Back up now")
                        }
                        OutlinedButton(onClick = onRestore, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Restore") }
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
                    Muted("Version 0.7")
                }
            }
        }
    }

    if (addingCat) AddCategoryDialog({ addingCat = false }) { n, inc, e -> onAddCat(n, inc, e); addingCat = false }
    delCat?.let { c ->
        ConfirmDialog("Remove ${c.name}?", "Entries that already use it keep the name.", "Remove", c.emoji,
            { delCat = null }) { onDeleteCat(c.id); delCat = null }
    }
    if (chooseLock) {
        AppDialog("Choose how to lock", { chooseLock = false }, {}, emoji = "🔒", confirm = "") {
            LockChoice("👆", "Fingerprint or phone lock", "Uses your phone's own fingerprint, face or screen lock", canPhone) {
                showBiometric(activity, PHONE_AUTH, null, { sec.enablePhone(); chooseLock = false })
            }
            LockChoice("🔢", "My own PIN", "A 4 to 6 digit PIN just for this app", true) {
                chooseLock = false; pinMode = 1
            }
            if (!canPhone) Muted("To use the phone lock, first set a screen lock or fingerprint in your phone's settings.")
        }
    }
    if (pinMode != 0) {
        SetPinDialog(if (pinMode == 1) "Set a PIN" else "Change PIN", { pinMode = 0 }) { pin ->
            if (pinMode == 1) sec.enablePin(pin, canBio) else sec.changePin(pin)
            pinMode = 0
        }
    }
    if (turningOff) {
        ConfirmDialog("Turn off app lock?", "Anyone who opens the app on this phone will be able to see your money.", "Turn off", "🔓",
            { turningOff = false }) { sec.disable(); turningOff = false }
    }
}

@Composable
private fun LockChoice(emoji: String, title: String, text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Muted(text)
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onOk: (String, Boolean, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var emoji by remember { mutableStateOf(EMOJIS[0]) }
    val taken = (expenseCats() + incomeCats()).any { it.equals(name.trim(), ignoreCase = true) }
    AppDialog(
        "New category", onDismiss, { onOk(name.trim(), income, emoji) }, emoji = emoji, confirm = "Add",
        confirmEnabled = name.isNotBlank() && !taken
    ) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            label = { Text("Name") }, singleLine = true, isError = taken,
            supportingText = { if (taken) Text("You already have this category") })
        Pick("Type", listOf("Expense", "Income"), if (income) 1 else 0, listOf("💸", "💰")) { income = it == 1 }
        Muted("Pick an icon")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EMOJIS.forEach { e ->
                FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e, fontSize = 18.sp) })
            }
        }
    }
}
