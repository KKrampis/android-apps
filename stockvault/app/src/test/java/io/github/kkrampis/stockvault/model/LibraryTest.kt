package io.github.kkrampis.stockvault.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    @Test
    fun foldersNestAndDeleteWithTheirStocks() {
        var lib = LibraryOps.addFolder(Library(), "Tech", null)
        val tech = lib.folders.single()
        lib = LibraryOps.addFolder(lib, "Chips", tech.id)
        val chips = lib.folders.first { it.name == "Chips" }
        lib = LibraryOps.saveStock(lib, "nvda", "NVIDIA", chips.id, 100.0, "USD")
        lib = LibraryOps.saveStock(lib, "NVDA", "NVIDIA", chips.id, 100.0, "USD") // duplicate ignored
        assertEquals(1, lib.saved.size)
        assertEquals("Tech / Chips", lib.pathOf(chips.id))
        assertEquals(lib, LibraryOps.moveFolder(lib, tech.id, chips.id)) // no cycles
        lib = LibraryOps.deleteFolder(lib, tech.id)
        assertTrue(lib.folders.isEmpty())
        assertTrue(lib.saved.isEmpty())
        assertEquals(3, lib.deleted.size)
    }

    @Test
    fun jsonRoundTrip() {
        var lib = LibraryOps.addFolder(Library(device = "Pixel"), "Ideas", null)
        lib = LibraryOps.saveStock(lib, "AAPL", "Apple", lib.folders[0].id, 230.5, "USD", "note")
        lib = LibraryOps.upsertPortfolio(
            lib,
            Portfolio(name = "Core", holdings = listOf(Holding("VTI", "Total market", 60.0), Holding("BND", weight = 40.0)),
                plan = BacktestPlan(amount = 5000.0, monthly = 200.0, start = "2015-01-01", rebalance = Rebalance.Yearly)),
        )
        val back = LibraryJson.parse(JSONWrap.wrap(LibraryJson.toJsonString(lib)))
        assertEquals(lib.folders, back.folders)
        assertEquals(lib.saved, back.saved)
        assertEquals(lib.portfolios, back.portfolios)
        assertNull(back.saved[0].folderId.takeIf { false })
    }

    @Test
    fun mergeKeepsNewestAndHonoursDeletes() {
        val a = Library(saved = listOf(SavedStock(id = "1", symbol = "A", name = "", folderId = null, updatedAt = "2026-01-02T00:00:00Z")))
        val b = Library(
            saved = listOf(
                SavedStock(id = "1", symbol = "A", name = "", folderId = null, note = "newer", updatedAt = "2026-01-03T00:00:00Z"),
                SavedStock(id = "2", symbol = "B", name = "", folderId = null, updatedAt = "2026-01-01T00:00:00Z"),
            ),
            deleted = listOf(Tombstone("2", "2099-01-01T00:00:00Z")),
        )
        val m = LibraryOps.merge(a, b)
        assertEquals(listOf("newer"), m.saved.map { it.note })
    }

    @Test
    fun formatsMoney() {
        assertEquals("$1,234.50", Format.price(1234.5, "USD"))
        assertEquals("1,234.50p", Format.price(1234.5, "GBp"))
        assertEquals("-$1,000", Format.money(-1000.0, "USD"))
        assertEquals("2.50T", Format.big(2.5e12))
    }
}

object JSONWrap {
    fun wrap(s: String) = "{\"stockvault\": $s}"
}
