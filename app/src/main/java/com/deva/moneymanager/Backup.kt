package com.deva.moneymanager

import org.json.JSONArray
import org.json.JSONObject

// Everything the app stores, in one bundle
data class BackupData(
    val accounts: List<Account>,
    val txns: List<Txn>,
    val budgets: List<Budget>,
    val recs: List<Recurring>,
    val holdings: List<Holding>,
    val persons: List<Person> = emptyList(),
    val cats: List<Cat> = emptyList()
)

fun toJson(d: BackupData): String {
    val o = JSONObject()
    o.put("app", "MyMoney")
    o.put("version", 2)
    o.put("accounts", JSONArray().apply {
        d.accounts.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("opening", it.opening)) }
    })
    o.put("txns", JSONArray().apply {
        d.txns.forEach {
            put(JSONObject().put("id", it.id).put("type", it.type).put("amount", it.amount)
                .put("accountId", it.accountId).put("toAccountId", it.toAccountId ?: JSONObject.NULL)
                .put("category", it.category).put("note", it.note).put("date", it.date)
                .put("personId", it.personId ?: JSONObject.NULL).put("share", it.share))
        }
    })
    o.put("budgets", JSONArray().apply {
        d.budgets.forEach { put(JSONObject().put("category", it.category).put("monthlyLimit", it.monthlyLimit)) }
    })
    o.put("recs", JSONArray().apply {
        d.recs.forEach {
            put(JSONObject().put("id", it.id).put("type", it.type).put("amount", it.amount)
                .put("accountId", it.accountId).put("category", it.category).put("note", it.note)
                .put("day", it.day).put("lastMonth", it.lastMonth))
        }
    })
    o.put("holdings", JSONArray().apply {
        d.holdings.forEach {
            put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind)
                .put("invested", it.invested).put("currentValue", it.currentValue).put("startDate", it.startDate))
        }
    })
    o.put("persons", JSONArray().apply {
        d.persons.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
    })
    o.put("cats", JSONArray().apply {
        d.cats.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("income", it.income).put("emoji", it.emoji)) }
    })
    return o.toString(2)
}

// Throws if the text is not a My Money backup. Older backups (without friends or categories) still load.
fun fromJson(s: String): BackupData {
    val o = JSONObject(s)
    require(o.optString("app") == "MyMoney")
    fun <T> list(key: String, f: (JSONObject) -> T): List<T> {
        val a = o.optJSONArray(key) ?: JSONArray()
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }
    return BackupData(
        list("accounts") { Account(it.getLong("id"), it.getString("name"), it.getDouble("opening")) },
        list("txns") {
            Txn(it.getLong("id"), it.getString("type"), it.getDouble("amount"), it.getLong("accountId"),
                if (it.isNull("toAccountId")) null else it.getLong("toAccountId"),
                it.getString("category"), it.getString("note"), it.getString("date"),
                if (!it.has("personId") || it.isNull("personId")) null else it.getLong("personId"),
                it.optDouble("share", 0.0))
        },
        list("budgets") { Budget(it.getString("category"), it.getDouble("monthlyLimit")) },
        list("recs") {
            Recurring(it.getLong("id"), it.getString("type"), it.getDouble("amount"), it.getLong("accountId"),
                it.getString("category"), it.getString("note"), it.getInt("day"), it.getString("lastMonth"))
        },
        list("holdings") {
            Holding(it.getLong("id"), it.getString("name"), it.getString("kind"), it.getDouble("invested"),
                it.getDouble("currentValue"), it.getString("startDate"))
        },
        list("persons") { Person(it.getLong("id"), it.getString("name")) },
        list("cats") { Cat(it.getLong("id"), it.getString("name"), it.getBoolean("income"), it.getString("emoji")) }
    )
}
