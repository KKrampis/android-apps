package io.github.kkrampis.stockvault.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The StockVault JSON format, used for the file on the phone, backups and JSONBin.
 * On JSONBin the document sits under a `"stockvault"` key, like the other apps in this repo.
 */
object LibraryJson {
    const val ROOT_KEY = "stockvault"
    const val VERSION = 1

    class FormatException(message: String) : Exception(message)

    fun toJson(lib: Library): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("app", "StockVault")
        .put("device", lib.device)
        .put("exportedAt", nowIso())
        .put("folders", JSONArray(lib.folders.map { f ->
            JSONObject().put("id", f.id).put("name", f.name).put("parentId", f.parentId ?: JSONObject.NULL)
                .put("createdAt", f.createdAt).put("updatedAt", f.updatedAt)
        }))
        .put("saved", JSONArray(lib.saved.map { s ->
            JSONObject().put("id", s.id).put("symbol", s.symbol).put("name", s.name)
                .put("folderId", s.folderId ?: JSONObject.NULL).put("note", s.note)
                .put("priceAtSave", s.priceAtSave ?: JSONObject.NULL).put("currency", s.currency)
                .put("addedAt", s.addedAt).put("updatedAt", s.updatedAt)
        }))
        .put("portfolios", JSONArray(lib.portfolios.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("note", p.note)
                .put("createdAt", p.createdAt).put("updatedAt", p.updatedAt)
                .put("holdings", JSONArray(p.holdings.map { h ->
                    JSONObject().put("symbol", h.symbol).put("name", h.name).put("weight", h.weight)
                }))
                .put("plan", JSONObject().put("amount", p.plan.amount).put("monthly", p.plan.monthly)
                    .put("start", p.plan.start ?: JSONObject.NULL).put("end", p.plan.end ?: JSONObject.NULL)
                    .put("rebalance", p.plan.rebalance.id).put("benchmark", p.plan.benchmark))
        }))
        .put("deleted", JSONArray(lib.deleted.map { JSONObject().put("id", it.id).put("at", it.at) }))

    fun toJsonString(lib: Library): String = toJson(lib).toString(2)

    /** Reads a library from a backup file or a JSONBin record (with or without the wrapper key). */
    fun parse(text: String): Library {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw FormatException("Not a JSON document")
        }
        val o = root.optJSONObject(ROOT_KEY) ?: root.optJSONObject("record")?.optJSONObject(ROOT_KEY) ?: root
        if (!o.has("folders") && !o.has("saved") && !o.has("portfolios")) throw FormatException("No StockVault data")
        return fromJson(o)
    }

    fun fromJson(o: JSONObject): Library = Library(
        folders = o.optJSONArray("folders").objects().map { f ->
            val created = f.optString("createdAt").ifBlank { nowIso() }
            Folder(
                id = f.optString("id").ifBlank { newId() },
                name = f.optString("name"),
                parentId = f.optNullableString("parentId"),
                createdAt = created,
                updatedAt = f.optString("updatedAt").ifBlank { created },
            )
        },
        saved = o.optJSONArray("saved").objects().mapNotNull { s ->
            val symbol = s.optString("symbol").trim()
            if (symbol.isEmpty()) return@mapNotNull null
            val added = s.optString("addedAt").ifBlank { nowIso() }
            SavedStock(
                id = s.optString("id").ifBlank { newId() },
                symbol = symbol,
                name = s.optString("name"),
                folderId = s.optNullableString("folderId"),
                note = s.optString("note"),
                priceAtSave = s.optNullableDouble("priceAtSave"),
                currency = s.optString("currency"),
                addedAt = added,
                updatedAt = s.optString("updatedAt").ifBlank { added },
            )
        },
        portfolios = o.optJSONArray("portfolios").objects().map { p ->
            val created = p.optString("createdAt").ifBlank { nowIso() }
            val plan = p.optJSONObject("plan")
            Portfolio(
                id = p.optString("id").ifBlank { newId() },
                name = p.optString("name"),
                note = p.optString("note"),
                holdings = p.optJSONArray("holdings").objects().mapNotNull { h ->
                    val sym = h.optString("symbol").trim()
                    if (sym.isEmpty()) null else Holding(sym, h.optString("name"), h.optDouble("weight", 0.0))
                },
                plan = if (plan == null) BacktestPlan() else BacktestPlan(
                    amount = plan.optDouble("amount", 10_000.0),
                    monthly = plan.optDouble("monthly", 0.0),
                    start = plan.optNullableString("start"),
                    end = plan.optNullableString("end"),
                    rebalance = Rebalance.from(plan.optString("rebalance")),
                    benchmark = plan.optString("benchmark").ifBlank { "SPY" },
                ),
                createdAt = created,
                updatedAt = p.optString("updatedAt").ifBlank { created },
            )
        },
        deleted = o.optJSONArray("deleted").objects().mapNotNull { t ->
            val id = t.optString("id")
            if (id.isBlank()) null else Tombstone(id, t.optString("at"))
        },
        device = o.optString("device"),
        exportedAt = o.optNullableString("exportedAt"),
    )

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
}
