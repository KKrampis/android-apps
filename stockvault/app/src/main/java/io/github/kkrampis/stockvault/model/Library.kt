package io.github.kkrampis.stockvault.model

import java.time.Instant
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
fun nowIso(): String = Instant.now().toString()

/** A folder for saved stocks. Folders can sit inside other folders ([parentId] null = top level). */
data class Folder(
    val id: String = newId(),
    val name: String,
    val parentId: String? = null,
    val createdAt: String = nowIso(),
    val updatedAt: String = createdAt,
)

/** A stock saved for later, in one folder, with the price it had when saved. */
data class SavedStock(
    val id: String = newId(),
    val symbol: String,
    val name: String,
    val folderId: String?,
    val note: String = "",
    val priceAtSave: Double? = null,
    val currency: String = "",
    val addedAt: String = nowIso(),
    val updatedAt: String = addedAt,
)

/** One position of a model portfolio, as a share of the money ([weight] in percent). */
data class Holding(val symbol: String, val name: String = "", val weight: Double)

enum class Rebalance(val id: String, val label: String, val months: Int) {
    None("none", "Never (buy and hold)", 0),
    Monthly("monthly", "Every month", 1),
    Quarterly("quarterly", "Every quarter", 3),
    Yearly("yearly", "Every year", 12),
    ;

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: None
    }
}

/** Default settings for a portfolio's backtest, saved with it so a re-run uses the same numbers. */
data class BacktestPlan(
    val amount: Double = 10_000.0,
    val monthly: Double = 0.0,
    /** ISO date (yyyy-MM-dd); null = as far back as all holdings have data. */
    val start: String? = null,
    /** ISO date; null = today. */
    val end: String? = null,
    val rebalance: Rebalance = Rebalance.None,
    val benchmark: String = "SPY",
)

data class Portfolio(
    val id: String = newId(),
    val name: String,
    val holdings: List<Holding> = emptyList(),
    val note: String = "",
    val plan: BacktestPlan = BacktestPlan(),
    val createdAt: String = nowIso(),
    val updatedAt: String = createdAt,
) {
    val totalWeight: Double get() = holdings.sumOf { it.weight }
}

data class Tombstone(val id: String, val at: String)

/** Everything the user keeps: folders, saved stocks and portfolios. Synced as one JSON document. */
data class Library(
    val folders: List<Folder> = emptyList(),
    val saved: List<SavedStock> = emptyList(),
    val portfolios: List<Portfolio> = emptyList(),
    val deleted: List<Tombstone> = emptyList(),
    val device: String = "",
    val exportedAt: String? = null,
) {
    fun childrenOf(folderId: String?) = folders.filter { it.parentId == folderId }.sortedBy { it.name.lowercase() }
    fun stocksIn(folderId: String?) = saved.filter { it.folderId == folderId }.sortedBy { it.symbol }

    /** The folder and every folder inside it, at any depth. */
    fun subtree(folderId: String): Set<String> {
        val out = linkedSetOf(folderId)
        var frontier = listOf(folderId)
        while (frontier.isNotEmpty()) {
            frontier = folders.filter { it.parentId in frontier && it.id !in out }.map { it.id }
            out += frontier
        }
        return out
    }

    /** "Tech / Chips" for a folder, from the top. */
    fun pathOf(folderId: String?): String {
        if (folderId == null) return "Saved"
        val names = mutableListOf<String>()
        var id: String? = folderId
        var guard = 0
        while (id != null && guard++ < 50) {
            val f = folders.firstOrNull { it.id == id } ?: break
            names += f.name
            id = f.parentId
        }
        return names.reversed().joinToString(" / ")
    }

    fun foldersHolding(symbol: String) = saved.filter { it.symbol.equals(symbol, true) }.map { it.folderId }
}

object LibraryOps {
    fun addFolder(lib: Library, name: String, parentId: String?): Library =
        lib.copy(folders = lib.folders + Folder(name = name.trim().ifBlank { "New folder" }, parentId = parentId))

    fun renameFolder(lib: Library, id: String, name: String): Library = lib.copy(
        folders = lib.folders.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }, updatedAt = nowIso()) else it },
    )

    /** Moves a folder under another one, refusing moves that would put a folder inside itself. */
    fun moveFolder(lib: Library, id: String, parentId: String?): Library {
        if (parentId != null && parentId in lib.subtree(id)) return lib
        return lib.copy(folders = lib.folders.map { if (it.id == id) it.copy(parentId = parentId, updatedAt = nowIso()) else it })
    }

    /** Deletes a folder, its subfolders and the stocks saved in them. */
    fun deleteFolder(lib: Library, id: String): Library {
        val gone = lib.subtree(id)
        val stocks = lib.saved.filter { it.folderId in gone }.map { it.id }
        val now = nowIso()
        return lib.copy(
            folders = lib.folders.filter { it.id !in gone },
            saved = lib.saved.filter { it.id !in stocks },
            deleted = lib.deleted + (gone + stocks).map { Tombstone(it, now) },
        )
    }

    /** Saves a stock in a folder. A stock already in that folder is left as it is. */
    fun saveStock(
        lib: Library,
        symbol: String,
        name: String,
        folderId: String?,
        price: Double?,
        currency: String,
        note: String = "",
    ): Library {
        val sym = symbol.trim().uppercase()
        if (sym.isBlank() || lib.saved.any { it.folderId == folderId && it.symbol == sym }) return lib
        return lib.copy(
            saved = lib.saved + SavedStock(
                symbol = sym, name = name, folderId = folderId, priceAtSave = price, currency = currency, note = note,
            ),
        )
    }

    fun updateStock(lib: Library, id: String, change: (SavedStock) -> SavedStock): Library = lib.copy(
        saved = lib.saved.map { if (it.id == id) change(it).copy(updatedAt = nowIso()) else it },
    )

    fun removeStock(lib: Library, id: String): Library = lib.copy(
        saved = lib.saved.filter { it.id != id },
        deleted = lib.deleted + Tombstone(id, nowIso()),
    )

    fun upsertPortfolio(lib: Library, p: Portfolio): Library {
        val updated = p.copy(updatedAt = nowIso())
        return if (lib.portfolios.any { it.id == p.id }) {
            lib.copy(portfolios = lib.portfolios.map { if (it.id == p.id) updated else it })
        } else {
            lib.copy(portfolios = lib.portfolios + updated)
        }
    }

    fun deletePortfolio(lib: Library, id: String): Library = lib.copy(
        portfolios = lib.portfolios.filter { it.id != id },
        deleted = lib.deleted + Tombstone(id, nowIso()),
    )

    /** Spreads 100 % evenly over the holdings. */
    fun equalWeights(holdings: List<Holding>): List<Holding> {
        if (holdings.isEmpty()) return holdings
        val w = 100.0 / holdings.size
        return holdings.map { it.copy(weight = Math.round(w * 100) / 100.0) }
    }

    /**
     * Merges two libraries the way the other apps' syncs do: every item from both sides, the newer
     * edit wins when both have it, and an item deleted on either side after its last edit stays deleted.
     */
    fun merge(a: Library, b: Library): Library {
        val tomb = (a.deleted + b.deleted).groupBy { it.id }.mapValues { (_, v) -> v.maxOf { it.at } }
        fun alive(id: String, updated: String) = tomb[id]?.let { it < updated } ?: true
        fun <T> pick(x: List<T>, y: List<T>, id: (T) -> String, at: (T) -> String): List<T> =
            (x + y).groupBy(id).values.map { group -> group.maxBy(at) }.filter { alive(id(it), at(it)) }

        return Library(
            folders = pick(a.folders, b.folders, { it.id }, { it.updatedAt }),
            saved = pick(a.saved, b.saved, { it.id }, { it.updatedAt }),
            portfolios = pick(a.portfolios, b.portfolios, { it.id }, { it.updatedAt }),
            // Old tombstones are dropped after a year to keep the document small.
            deleted = tomb.filter { (_, at) -> at > Instant.now().minusSeconds(365L * 86_400).toString() }
                .map { (id, at) -> Tombstone(id, at) },
            device = a.device,
        ).let { lib ->
            // A stock whose folder is gone moves to the top level rather than vanishing.
            val ids = lib.folders.map { it.id }.toSet()
            lib.copy(
                saved = lib.saved.map { if (it.folderId != null && it.folderId !in ids) it.copy(folderId = null) else it },
                folders = lib.folders.map { if (it.parentId != null && it.parentId !in ids) it.copy(parentId = null) else it },
            )
        }
    }
}
