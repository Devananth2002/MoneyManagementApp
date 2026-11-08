@file:OptIn(ExperimentalMaterial3Api::class)

package com.deva.moneymanager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(viewModel()) }
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
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthStr)
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var pending by remember { mutableStateOf<BackupData?>(null) }
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("mymoney", Context.MODE_PRIVATE) }
    var last by remember { mutableStateOf(prefs.getString("lastBackup", "") ?: "") }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(toJson(BackupData(accounts, txns, budgets, recs, holdings)).toByteArray())
                }
            }.isSuccess
            if (ok) {
                last = LocalDate.now().toString()
                prefs.edit().putString("lastBackup", last).apply()
                toast("Backup saved")
            } else toast("Couldn't save the backup. Try another location.")
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                fromJson(ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            }.onSuccess { pending = it }.onFailure { toast("That file isn't a My Money backup.") }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf("🏠" to "Home", "📋" to "History", "📈" to "Invest", "📊" to "Report")
                    .forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i },
                            icon = { Text(icon) }, label = { Text(label) })
                    }
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                FloatingActionButton(
                    onClick = { editing = null; showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Text("+", fontSize = 26.sp) }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> Home(accounts, txns, budgets, recs, holdings,
                    lastBackup = last,
                    onBackup = { saver.launch("mymoney-backup-${LocalDate.now()}.json") },
                    onRestore = { opener.launch(arrayOf("*/*")) },
                    onEditTxn = { editing = it; showAdd = true },
                    onEditAccount = { id, n, o -> vm.editAccount(id, n, o) },
                    onAddAccount = { n, b -> vm.addAccount(n, b) },
                    onDeleteAccount = { vm.deleteAccount(it) },
                    onDeleteTxn = { vm.deleteTxn(it) },
                    onDeleteRec = { vm.deleteRecurring(it) })
                1 -> History(month, { monthStr = it.toString() }, accounts, txns,
                    onEdit = { editing = it; showAdd = true },
                    onDelete = { vm.deleteTxn(it) },
                    onExport = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "My Money export")
                            putExtra(Intent.EXTRA_TEXT, csvOf(txns, accounts))
                        }
                        ctx.startActivity(Intent.createChooser(send, "Export CSV"))
                    })
                2 -> Invest(holdings,
                    onAdd = { vm.addHolding(it) },
                    onTopUp = { id, x -> vm.topUp(id, x) },
                    onEdit = { id, n, i, c -> vm.editHolding(id, n, i, c) },
                    onDelete = { vm.deleteHolding(it) })
                else -> Report(month, { monthStr = it.toString() }, txns, budgets,
                    onSetBudget = { c, v -> vm.setBudget(c, v) },
                    onClearBudget = { vm.clearBudget(it) })
            }
        }
    }

    if (showAdd) {
        AddDialog(accounts, editing, onDismiss = { showAdd = false; editing = null }) { t, rep ->
            if (editing != null) vm.updateTx(t) else vm.addTx(t, rep)
            monthStr = YearMonth.from(LocalDate.parse(t.date)).toString()
            showAdd = false
            editing = null
        }
    }

    pending?.let { d ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Restore this backup?") },
            text = {
                Text("This replaces everything in the app with the backup (${d.accounts.size} accounts, " +
                    "${d.txns.size} entries, ${d.holdings.size} investments). Your current data will be lost.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.restore(d) { toast("Backup restored") }
                    pending = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}
