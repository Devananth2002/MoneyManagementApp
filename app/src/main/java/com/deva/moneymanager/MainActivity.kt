@file:OptIn(ExperimentalMaterial3Api::class)

package com.deva.moneymanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { App(viewModel()) }
            }
        }
    }
}

@Composable
fun App(vm: MainVm) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val recs by vm.recs.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthStr)
    var showAdd by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("🏠" to "Home", "📋" to "History", "📊" to "Report").forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Text(icon) }, label = { Text(label) })
                }
            }
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("+") } }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> Home(accounts, txns, budgets, recs,
                    onAddAccount = { n, b -> vm.addAccount(n, b) },
                    onDeleteAccount = { vm.deleteAccount(it) },
                    onDeleteTxn = { vm.deleteTxn(it) },
                    onDeleteRec = { vm.deleteRecurring(it) })
                1 -> History(month, { monthStr = it.toString() }, accounts, txns,
                    onDelete = { vm.deleteTxn(it) },
                    onExport = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "My Money export")
                            putExtra(Intent.EXTRA_TEXT, csvOf(txns, accounts))
                        }
                        ctx.startActivity(Intent.createChooser(send, "Export CSV"))
                    })
                else -> Report(month, { monthStr = it.toString() }, txns, budgets,
                    onSetBudget = { c, v -> vm.setBudget(c, v) },
                    onClearBudget = { vm.clearBudget(it) })
            }
        }
    }

    if (showAdd) {
        AddDialog(accounts, onDismiss = { showAdd = false }) { t, rep ->
            vm.addTx(t, rep)
            monthStr = YearMonth.from(LocalDate.parse(t.date)).toString()
            showAdd = false
        }
    }
}
