package com.deva.moneymanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { Home(viewModel()) }
            }
        }
    }
}

fun inr(v: Double): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(v)

@Composable
fun Home(vm: MainVm) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val total = accounts.sumOf { balance(it, txns) }

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { showDialog = true }) { Text("+") }
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Total money you have")
                Text(inr(total), style = MaterialTheme.typography.headlineLarge)
            }
            if (accounts.isEmpty()) item { Text("No accounts yet. Tap + to add your first bank account.") }
            items(accounts, key = { it.id }) { a ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(a.name)
                        Text(inr(balance(a, txns)), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }

    if (showDialog) {
        var name by remember { mutableStateOf("") }
        var bal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add account") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. HDFC Savings)") })
                    OutlinedTextField(
                        bal, { bal = it }, label = { Text("Current balance") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        vm.addAccount(name.trim(), bal.toDoubleOrNull() ?: 0.0)
                        showDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}
