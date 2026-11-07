package com.deva.moneymanager

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

val EXPENSE_CATS = listOf("Food", "Rent", "Travel", "Shopping", "Bills", "Entertainment", "Health", "Savings", "Other")
val INCOME_CATS = listOf("Salary", "Refund", "Other income")

// One bank account, wallet or cash pot
@Entity
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val opening: Double
)

// One money movement. type: "e" expense, "i" income, "t" transfer
@Entity
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Double,
    val accountId: Long,
    val toAccountId: Long? = null,
    val category: String = "",
    val note: String = "",
    val date: String // yyyy-MM-dd
)

// Monthly spending limit for one category
@Entity
data class Budget(@PrimaryKey val category: String, val monthlyLimit: Double)

// An entry that repeats every month (rent, salary, SIP...)
@Entity
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Double,
    val accountId: Long,
    val category: String,
    val note: String,
    val day: Int,
    val lastMonth: String // yyyy-MM of the last entry created
)

@Dao
interface MoneyDao {
    @Query("SELECT * FROM Account ORDER BY id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM Txn ORDER BY date DESC, id DESC") fun txns(): Flow<List<Txn>>
    @Query("SELECT * FROM Budget") fun budgets(): Flow<List<Budget>>
    @Query("SELECT * FROM Recurring ORDER BY id") fun recurrings(): Flow<List<Recurring>>
    @Query("SELECT * FROM Recurring") suspend fun recurringNow(): List<Recurring>

    @Insert suspend fun addAccount(a: Account)
    @Query("DELETE FROM Account WHERE id = :id") suspend fun deleteAccount(id: Long)
    @Query("DELETE FROM Recurring WHERE accountId = :id") suspend fun deleteRecurringOfAccount(id: Long)

    @Insert suspend fun addTxn(t: Txn)
    @Query("DELETE FROM Txn WHERE id = :id") suspend fun deleteTxn(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setBudget(b: Budget)
    @Query("DELETE FROM Budget WHERE category = :c") suspend fun clearBudget(c: String)

    @Insert suspend fun addRecurring(r: Recurring)
    @Query("DELETE FROM Recurring WHERE id = :id") suspend fun deleteRecurring(id: Long)
    @Query("UPDATE Recurring SET lastMonth = :m WHERE id = :id") suspend fun setLastMonth(id: Long, m: String)
}

@Database(
    entities = [Account::class, Txn::class, Budget::class, Recurring::class],
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MoneyDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "money.db")
                .fallbackToDestructiveMigration() // fine while testing; real migrations come before real use
                .build().also { inst = it }
        }
    }
}

class MainVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val accounts = dao.accounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val txns = dao.txns().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets = dao.budgets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recs = dao.recurrings().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { viewModelScope.launch { applyRecurring() } }

    // Creates any monthly entries that fell due since the app was last opened
    private suspend fun applyRecurring() {
        val today = LocalDate.now()
        for (r in dao.recurringNow()) {
            var last = YearMonth.parse(r.lastMonth)
            while (true) {
                val next = last.plusMonths(1)
                val d = next.atDay(minOf(r.day, next.lengthOfMonth()))
                if (d.isAfter(today)) break
                dao.addTxn(Txn(type = r.type, amount = r.amount, accountId = r.accountId,
                    category = r.category, note = r.note, date = d.toString()))
                last = next
            }
            if (last.toString() != r.lastMonth) dao.setLastMonth(r.id, last.toString())
        }
    }

    fun addAccount(name: String, balance: Double) {
        viewModelScope.launch { dao.addAccount(Account(name = name, opening = balance)) }
    }
    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            if (txns.value.none { it.accountId == id || it.toAccountId == id }) {
                dao.deleteRecurringOfAccount(id)
                dao.deleteAccount(id)
            }
        }
    }
    fun addTx(t: Txn, repeat: Boolean) {
        viewModelScope.launch {
            dao.addTxn(t)
            if (repeat) {
                val d = LocalDate.parse(t.date)
                dao.addRecurring(Recurring(type = t.type, amount = t.amount, accountId = t.accountId,
                    category = t.category, note = t.note, day = minOf(28, d.dayOfMonth),
                    lastMonth = YearMonth.from(d).toString()))
            }
        }
    }
    fun deleteTxn(id: Long) { viewModelScope.launch { dao.deleteTxn(id) } }
    fun setBudget(c: String, v: Double) { viewModelScope.launch { dao.setBudget(Budget(c, v)) } }
    fun clearBudget(c: String) { viewModelScope.launch { dao.clearBudget(c) } }
    fun deleteRecurring(id: Long) { viewModelScope.launch { dao.deleteRecurring(id) } }
}

// Opening balance + everything that came in - everything that went out
fun balance(a: Account, t: List<Txn>): Double = a.opening + t.sumOf {
    when {
        it.type == "i" && it.accountId == a.id -> it.amount
        it.type == "e" && it.accountId == a.id -> -it.amount
        it.type == "t" && it.accountId == a.id -> -it.amount
        it.type == "t" && it.toAccountId == a.id -> it.amount
        else -> 0.0
    }
}

fun inMonth(t: List<Txn>, m: YearMonth): List<Txn> = t.filter { it.date.startsWith(m.toString()) }

fun catTotals(t: List<Txn>): Map<String, Double> =
    t.filter { it.type == "e" }.groupBy { it.category }.mapValues { e -> e.value.sumOf { x -> x.amount } }

fun inr(v: Double): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(v)

fun csvOf(t: List<Txn>, a: List<Account>): String {
    val n = a.associate { it.id to it.name }
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val rows = t.map { x ->
        listOf(
            x.date,
            when (x.type) { "e" -> "Expense"; "i" -> "Income"; else -> "Transfer" },
            q(n[x.accountId] ?: ""),
            q(x.toAccountId?.let { n[it] } ?: ""),
            q(x.category),
            x.amount.toString(),
            q(x.note)
        ).joinToString(",")
    }
    return (listOf("Date,Type,Account,To account,Category,Amount,Note") + rows).joinToString("\n")
}
