package com.deva.moneymanager

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    val date: String
)

@Dao
interface MoneyDao {
    @Query("SELECT * FROM Account ORDER BY id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM Txn ORDER BY date DESC, id DESC") fun txns(): Flow<List<Txn>>
    @Insert suspend fun addAccount(a: Account)
    @Insert suspend fun addTxn(t: Txn)
}

@Database(entities = [Account::class, Txn::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MoneyDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "money.db")
                .build().also { inst = it }
        }
    }
}

class MainVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val accounts = dao.accounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val txns = dao.txns().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun addAccount(name: String, balance: Double) {
        viewModelScope.launch { dao.addAccount(Account(name = name, opening = balance)) }
    }
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
