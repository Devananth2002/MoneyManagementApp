package com.deva.moneymanager

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

val EXPENSE_CATS = listOf("Food", "Rent", "Travel", "Shopping", "Bills", "Entertainment", "Health", "Savings", "Other")
val INCOME_CATS = listOf("Salary", "Refund", "Gift", "Interest", "Business", "Rental", "Other income")
val INVEST_KINDS = listOf("Mutual fund", "Stock", "Fixed deposit", "PPF / EPF", "Gold", "Crypto", "Other")

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
    val date: String, // yyyy-MM-dd
    val personId: Long? = null, // the friend this entry is shared with or settled with
    @ColumnInfo(defaultValue = "0") val share: Double = 0.0, // the friend's part of an expense
    @ColumnInfo(defaultValue = "") val splitBy: String = "" // JSON map of friendId -> share, used for multiple friends
)

// A friend you share money with
@Entity
data class Person(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

// A category you made yourself
@Entity
data class Cat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val income: Boolean,
    val emoji: String
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

// One investment: money put in vs what it is worth now
@Entity
data class Holding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val invested: Double,
    val currentValue: Double,
    val startDate: String,
    val symbol: String = "", // fund code or stock symbol used to look up the price
    val source: String = "", // "mf" or "yahoo" when the price is fetched automatically, empty when typed by hand
    val units: Double = 0.0, // units or shares held (automatic pricing only)
    val priceDate: String = "" // date of the last automatic price
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

    @Query("SELECT * FROM Holding ORDER BY id") fun holdings(): Flow<List<Holding>>
    @Insert suspend fun addHolding(h: Holding)
    @Query("DELETE FROM Holding WHERE id = :id") suspend fun deleteHolding(id: Long)
    @Query("UPDATE Holding SET invested = invested + :x, currentValue = currentValue + :x WHERE id = :id") suspend fun topUp(id: Long, x: Double)
    @Query("UPDATE Holding SET currentValue = :v WHERE id = :id") suspend fun setValue(id: Long, v: Double)

    @Update suspend fun updateTxn(t: Txn)
    @Query("UPDATE Account SET name = :n, opening = :o WHERE id = :id") suspend fun editAccount(id: Long, n: String, o: Double)
    @Update suspend fun updateHolding(h: Holding)

    @Query("DELETE FROM Account") suspend fun wipeAccounts()
    @Query("DELETE FROM Txn") suspend fun wipeTxns()
    @Query("DELETE FROM Budget") suspend fun wipeBudgets()
    @Query("DELETE FROM Recurring") suspend fun wipeRecurring()
    @Query("DELETE FROM Holding") suspend fun wipeHoldings()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAccounts(l: List<Account>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTxns(l: List<Txn>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudgets(l: List<Budget>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecurring(l: List<Recurring>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHoldings(l: List<Holding>)

    @Query("SELECT * FROM Person ORDER BY name COLLATE NOCASE") fun persons(): Flow<List<Person>>
    @Insert suspend fun addPerson(p: Person): Long
    @Query("DELETE FROM Person WHERE id = :id") suspend fun deletePerson(id: Long)
    @Query("SELECT * FROM Cat ORDER BY id") fun cats(): Flow<List<Cat>>
    @Insert suspend fun addCat(c: Cat)
    @Query("DELETE FROM Cat WHERE id = :id") suspend fun deleteCat(id: Long)
    @Query("DELETE FROM Person") suspend fun wipePersons()
    @Query("DELETE FROM Cat") suspend fun wipeCats()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPersons(l: List<Person>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCats(l: List<Cat>)
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Txn` ADD COLUMN `splitBy` TEXT NOT NULL DEFAULT ''")
    }
}

// Keeps your existing data when investments can be priced automatically
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Holding` ADD COLUMN `symbol` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `Holding` ADD COLUMN `source` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `Holding` ADD COLUMN `units` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `Holding` ADD COLUMN `priceDate` TEXT NOT NULL DEFAULT ''")
    }
}

// Keeps your existing data when the app gains Friends and your own categories
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Txn` ADD COLUMN `personId` INTEGER")
        db.execSQL("ALTER TABLE `Txn` ADD COLUMN `share` REAL NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `Person` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `Cat` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `income` INTEGER NOT NULL, `emoji` TEXT NOT NULL)")
    }
}

// Keeps your existing data when the app gains the Investments table
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `Holding` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `invested` REAL NOT NULL, `currentValue` REAL NOT NULL, `startDate` TEXT NOT NULL)")
    }
}

@Database(
    entities = [Account::class, Txn::class, Budget::class, Recurring::class, Holding::class, Person::class, Cat::class],
    version = 6,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MoneyDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "money.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build().also { inst = it }
        }
    }
}

class MainVm(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val dao = db.dao()
    val accounts = dao.accounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val txns = dao.txns().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets = dao.budgets().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recs = dao.recurringNow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val holdings = dao.holdings().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val persons = dao.persons().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cats = dao.cats().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { applyRecurring() }
        viewModelScope.launch { dao.cats().collect { customCats = it } }
        viewModelScope.launch { dao.persons().collect { list -> peopleMap = list.associate { p -> p.id to p.name } } }
    }

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
    private suspend fun personIdFor(name: String): Long =
        persons.value.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id ?: dao.addPerson(Person(name = name))

    fun addTx(t0: Txn, repeat: Boolean, newFriend: String? = null) {
        viewModelScope.launch {
            val t = if (newFriend != null) t0.copy(personId = personIdFor(newFriend), splitBy = "") else t0
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
    fun addHolding(h: Holding) { viewModelScope.launch { dao.addHolding(h) } }
    fun topUp(id: Long, x: Double) { viewModelScope.launch { dao.topUp(id, x) } }
    fun setValue(id: Long, v: Double) { viewModelScope.launch { dao.setValue(id, v) } }
    fun deleteHolding(id: Long) { viewModelScope.launch { dao.deleteHolding(id) } }
    fun updateTx(t: Txn, newFriend: String? = null) {
        viewModelScope.launch { dao.updateTxn(if (newFriend != null) t.copy(personId = personIdFor(newFriend), splitBy = "") else t) }
    }
    fun restoreTx(t: Txn) { viewModelScope.launch { dao.putTxns(listOf(t)) } }
    fun addPerson(name: String) { viewModelScope.launch { personIdFor(name) } }
    fun deletePerson(id: Long) { viewModelScope.launch { dao.deletePerson(id) } }
    fun addCat(name: String, income: Boolean, emoji: String) { viewModelScope.launch { dao.addCat(Cat(name = name, income = income, emoji = emoji)) } }
    fun deleteCat(id: Long) { viewModelScope.launch { dao.deleteCat(id) } }
    fun editAccount(id: Long, name: String, opening: Double) { viewModelScope.launch { dao.editAccount(id, name, opening) } }
    fun saveHolding(h: Holding) { viewModelScope.launch { dao.updateHolding(h) } }

    private var lastRefresh = 0L

    // Fetches the latest price for every investment that is linked to a fund or stock
    fun refreshPrices(force: Boolean = false, onDone: (Int, Int) -> Unit = { _, _ -> }) {
        if (refreshingPrices) return
        if (!force && System.currentTimeMillis() - lastRefresh < 10 * 60_000) return
        val live = holdings.value.filter { it.source.isNotEmpty() && it.units > 0 }
        if (live.isEmpty()) return
        viewModelScope.launch {
            refreshingPrices = true
            var ok = 0
            var bad = 0
            for (h in live) {
                val q = Prices.latest(h.source, h.symbol)
                if (q != null && (q.currency == null || q.currency == currencyCode)) {
                    dao.updateHolding(h.copy(currentValue = h.units * q.price, priceDate = q.date))
                    ok++
                } else bad++
            }
            lastRefresh = System.currentTimeMillis()
            refreshingPrices = false
            onDone(ok, bad)
        }
    }

    // Replaces everything in the app with the contents of a backup, all or nothing
    fun restore(d: BackupData, onDone: () -> Unit) {
        viewModelScope.launch {
            db.withTransaction {
                dao.wipeTxns(); dao.wipeRecurring(); dao.wipeBudgets(); dao.wipeHoldings(); dao.wipeAccounts()
                dao.wipePersons(); dao.wipeCats()
                dao.putAccounts(d.accounts); dao.putTxns(d.txns); dao.putBudgets(d.budgets)
                dao.putRecurring(d.recs); dao.putHoldings(d.holdings)
                dao.putPersons(d.persons); dao.putCats(d.cats)
            }
            onDone()
        }
    }
}

private fun parseSplitMap(raw: String): Map<Long, Double> {
    if (raw.isBlank()) return emptyMap()
    return runCatching {
        val obj = JSONObject(raw)
        obj.keys().asSequence().associate { key -> key.toLong() to obj.getDouble(key) }
    }.getOrDefault(emptyMap())
}

fun txnSplitShare(txn: Txn, personId: Long): Double = parseSplitMap(txn.splitBy)[personId] ?: 0.0
fun txnSplitTotal(txn: Txn): Double = parseSplitMap(txn.splitBy).values.sum()

// Opening balance + everything that came in - everything that went out
fun balance(a: Account, t: List<Txn>): Double = a.opening + t.sumOf {
    when {
        it.type == "i" && it.accountId == a.id -> it.amount
        it.type == "e" && it.accountId == a.id -> -it.amount
        it.type == "t" && it.accountId == a.id -> -it.amount
        it.type == "t" && it.toAccountId == a.id -> it.amount
        it.type == "si" && it.accountId == a.id -> it.amount
        it.type == "so" && it.accountId == a.id -> -it.amount
        else -> 0.0
    }
}

// What a friend owes you (positive) or what you owe them (negative)
fun owed(personId: Long, t: List<Txn>): Double = t.sumOf {
    when {
        it.type == "e" && it.personId == personId -> it.share
        it.type == "e" && it.splitBy.isNotBlank() -> txnSplitShare(it, personId)
        it.type == "so" -> it.amount
        it.type == "si" -> -it.amount
        else -> 0.0
    }
}

fun inMonth(t: List<Txn>, m: YearMonth): List<Txn> = t.filter { it.date.startsWith(m.toString()) }

fun catTotals(t: List<Txn>): Map<String, Double> =
    t.filter { it.type == "e" }.groupBy { it.category }.mapValues { e ->
        e.value.sumOf { x -> x.amount - if (x.splitBy.isNotBlank()) txnSplitTotal(x) else x.share }
    }

fun csvOf(t: List<Txn>, a: List<Account>, p: List<Person>): String {
    val n = a.associate { it.id to it.name }
    val pn = p.associate { it.id to it.name }
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val rows = t.map { x ->
        val splitNames = if (x.splitBy.isNotBlank()) parseSplitMap(x.splitBy).keys.mapNotNull { pn[it] }.joinToString("; ") else x.personId?.let { pn[it] } ?: ""
        val splitAmount = if (x.splitBy.isNotBlank()) txnSplitTotal(x) else x.share
        listOf(
            x.date,
            when (x.type) { "e" -> "Expense"; "i" -> "Income"; "si" -> "Friend paid you"; "so" -> "You paid friend"; else -> "Transfer" },
            q(n[x.accountId] ?: ""),
            q(x.toAccountId?.let { n[it] } ?: ""),
            q(x.category),
            x.amount.toString(),
            q(x.note),
            q(splitNames),
            splitAmount.toString()
        ).joinToString(",")
    }
    return (listOf("Date,Type,Account,To account,Category,Amount,Note,Friend,Friend share") + rows).joinToString("\n")
}

val money = 1
