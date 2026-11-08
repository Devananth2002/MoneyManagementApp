@file:OptIn(ExperimentalMaterial3Api::class)

package com.deva.moneymanager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currencyCode = getSharedPreferences("mymoney", Context.MODE_PRIVATE).getString("currency", "INR") ?: "INR"
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
    val persons by vm.persons.collectAsStateWithLifecycle()
    val cats by vm.cats.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthStr)
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var startType by remember { mutableStateOf("e") }
    var pending by remember { mutableStateOf<BackupData?>(null) }
    var showFriends by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("mymoney", Context.MODE_PRIVATE) }
    var last by remember { mutableStateOf(prefs.getString("lastBackup", "") ?: "") }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    // Delete straight away, with a chance to undo
    val del: (Long) -> Unit = { id ->
        txns.firstOrNull { it.id == id }?.let { t ->
            vm.deleteTxn(id)
            scope.launch {
                val r = snack.showSnackbar("Entry deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.restoreTx(t)
            }
        }
    }
    val edit: (Txn) -> Unit = { t ->
        if (t.type.startsWith("s")) toast("To change a friend payment, delete it and add it again from Friends.")
        else { editing = t; showAdd = true }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(toJson(BackupData(accounts, txns, budgets, recs, holdings, persons, cats)).toByteArray())
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
    val backUp = { saver.launch("mymoney-backup-${LocalDate.now()}.json") }
    val tabs = listOf("Home" to Ic.Home, "History" to Ic.Ledger, "Invest" to Ic.Trend, "Reports" to Ic.Pie, "More" to Ic.More)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i && !showFriends, onClick = { tab = i; showFriends = false },
                        icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
        },
        floatingActionButton = {
            if (!showFriends && (tab == 0 || tab == 1 || tab == 3)) {
                FloatingActionButton(
                    onClick = { editing = null; startType = "e"; showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Icon(Ic.Add, "Add entry") }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (showFriends) {
                Friends(persons, txns, accounts,
                    onBack = { showFriends = false },
                    onAddPerson = { vm.addPerson(it) },
                    onDeletePerson = { vm.deletePerson(it) },
                    onSettle = { vm.addTx(it, false) },
                    onDeleteTxn = del)
            } else Crossfade(targetState = tab, label = "tab") { t ->
                when (t) {
                    0 -> Home(accounts, txns, budgets, recs, holdings,
                        persons = persons,
                        onOpenFriends = { showFriends = true },
                        lastBackup = last,
                        onBackup = backUp,
                        onQuick = { k -> editing = null; startType = k; showAdd = true },
                        onSeeAll = { tab = 1 },
                        onEditTxn = edit,
                        onEditAccount = { id, n, o -> vm.editAccount(id, n, o) },
                        onAddAccount = { n, b -> vm.addAccount(n, b) },
                        onDeleteAccount = { vm.deleteAccount(it) },
                        onDeleteTxn = del,
                        onDeleteRec = { vm.deleteRecurring(it) })
                    1 -> History(month, { monthStr = it.toString() }, accounts, txns,
                        onEdit = edit,
                        onDelete = del,
                        onExport = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "My Money export")
                                putExtra(Intent.EXTRA_TEXT, csvOf(txns, accounts, persons))
                            }
                            ctx.startActivity(Intent.createChooser(send, "Export CSV"))
                        })
                    2 -> Invest(holdings,
                        onAdd = { vm.addHolding(it) },
                        onTopUp = { id, x -> vm.topUp(id, x) },
                        onEdit = { id, n, i, c -> vm.editHolding(id, n, i, c) },
                        onDelete = { vm.deleteHolding(it) })
                    3 -> Report(month, { monthStr = it.toString() }, txns, budgets,
                        onSetBudget = { c, v -> vm.setBudget(c, v) },
                        onClearBudget = { vm.clearBudget(it) })
                    else -> Settings(
                        lastBackup = last,
                        onBackup = backUp,
                        onRestore = { opener.launch(arrayOf("*/*")) },
                        onCurrency = { c ->
                            currencyCode = c
                            prefs.edit().putString("currency", c).apply()
                        },
                        cats = cats,
                        onAddCat = { n, inc, e -> vm.addCat(n, inc, e) },
                        onDeleteCat = { vm.deleteCat(it) })
                }
            }
        }
    }

    BackHandler(enabled = showFriends) { showFriends = false }

    if (showAdd) {
        AddSheet(accounts, persons, editing, startType, onDismiss = { showAdd = false; editing = null }) { t, rep, newFriend ->
            if (editing != null) vm.updateTx(t, newFriend) else vm.addTx(t, rep, newFriend)
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
