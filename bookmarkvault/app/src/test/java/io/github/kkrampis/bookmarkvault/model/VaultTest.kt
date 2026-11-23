package io.github.kkrampis.bookmarkvault.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultTest {
    // Shaped exactly like the extension's collectBookmarks() output.
    private val chromeExport = """
        {
          "version": "1.0",
          "exportedAt": "2026-11-20T10:00:00.000Z",
          "device": "Work laptop",
          "totalCount": 4,
          "roots": [
            { "type": "folder", "title": "Bookmarks bar", "children": [
              { "type": "bookmark", "title": "News", "url": "https://news.example.com" },
              { "type": "folder", "title": "Dev", "children": [
                { "type": "bookmark", "title": "Docs", "url": "https://docs.example.com" },
                { "type": "bookmark", "title": "Bookmarklet", "url": "javascript:alert(1)" }
              ] }
            ] },
            { "type": "folder", "title": "Other bookmarks", "children": [
              { "type": "folder", "title": "Recipes", "children": [
                { "type": "bookmark", "title": "Soup", "url": "https://soup.example.com" }
              ] }
            ] }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesAllShapes() {
        val plain = VaultJson.parse(chromeExport)
        val pushed = VaultJson.parse("""{"bookmarkvault": $chromeExport}""")
        val withMeta = VaultJson.parse("""{"record": {"bookmarkvault": $chromeExport}, "metadata": {"id": "x"}}""")
        for (v in listOf(plain, pushed, withMeta)) {
            assertEquals("Work laptop", v.device)
            assertEquals(2, v.roots.size)
            assertEquals(4, v.totalCount)
            assertEquals(2, v.folderCount)
        }
    }

    @Test
    fun roundTripsWithoutIds() {
        val v = VaultJson.parse(chromeExport)
        val json = VaultJson.toJsonString(v)
        assertTrue(!json.contains("\"id\""))
        assertEquals(v.totalCount, VaultJson.parse(json).totalCount)
        assertEquals(4, VaultJson.toJson(v).getInt("totalCount"))
    }

    @Test
    fun importChoicesUseExtensionKeys() {
        val keys = VaultJson.parse(chromeExport).importChoices().map { it.key }
        assertEquals(listOf("0--1", "0-1", "1-0"), keys)
    }

    @Test
    fun importSelectedGoesIntoOtherBookmarksAndDropsNonWebLinks() {
        val incoming = VaultJson.parse(chromeExport)
        val (after, count) = Vault.empty("Phone").importInto(incoming, setOf("0-1", "1-0"), "Imported")
        assertEquals(2, count) // Docs + Soup; the bookmarklet is skipped
        val other = after.otherRoot()!!
        val folder = other.children.single() as Folder
        assertEquals("Imported", folder.title)
        assertEquals(listOf("Dev", "Recipes"), folder.children.map { it.title })
    }

    @Test
    fun importAll() {
        val (_, count) = Vault.empty("Phone").importInto(VaultJson.parse(chromeExport), null, "All")
        assertEquals(3, count)
    }

    @Test
    fun editsAndMoves() {
        var v = Vault.empty("Phone")
        val mobile = v.mobileRoot()!!
        val f = Folder(title = "Reading")
        val b = Bookmark(title = "A", url = "https://a.example")
        v = v.addToFolder(mobile.id, f).addToFolder(mobile.id, b)
        assertEquals(1, v.totalCount)
        v = v.moveNode(b.id, f.id)
        assertEquals(listOf(b.id), v.findFolder(f.id)!!.children.map { it.id })
        // A folder cannot be moved into itself.
        assertEquals(v, v.moveNode(f.id, f.id))
        // Top-level folders stay.
        assertEquals(v, v.moveNode(mobile.id, f.id))
        v = v.replaceNode(b.copy(title = "B"))
        assertEquals("B", v.allBookmarks().single().first.title)
        v = v.replaceNode(f.copy(title = "Later", children = emptyList()))
        assertEquals(1, v.findFolder(f.id)!!.children.size) // rename keeps children
        v = v.removeNode(f.id)
        assertEquals(0, v.totalCount)
        assertEquals(3, v.roots.size)
    }

    @Test
    fun replaceMirrorsIncoming() {
        val v = Vault.empty("Phone").replaceWith(VaultJson.parse(chromeExport))
        assertEquals(listOf("Bookmarks bar", "Other bookmarks"), v.roots.map { it.title })
        assertEquals(3, v.totalCount)
    }

    @Test
    fun netscapeHtml() {
        val html = VaultJson.toNetscapeHtml(VaultJson.parse(chromeExport))
        assertTrue(html.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(html.contains("<DT><A HREF=\"https://soup.example.com\">Soup</A>"))
        assertTrue(html.contains("<DT><H3>Recipes</H3>"))
    }
}
