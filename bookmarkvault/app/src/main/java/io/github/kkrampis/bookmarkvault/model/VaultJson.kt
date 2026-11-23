package io.github.kkrampis.bookmarkvault.model

import org.json.JSONArray
import org.json.JSONObject

/** Reads and writes the BookmarkVault JSON format, and the Netscape HTML that every browser imports. */
object VaultJson {

    class FormatException(message: String) : Exception(message)

    fun toJson(vault: Vault): JSONObject = JSONObject().apply {
        put("version", vault.version)
        put("exportedAt", vault.exportedAt)
        put("device", vault.device)
        put("totalCount", vault.totalCount)
        put("roots", JSONArray().apply { vault.roots.forEach { put(nodeToJson(it)) } })
    }

    fun toJsonString(vault: Vault): String = toJson(vault).toString(2)

    private fun nodeToJson(node: Node): JSONObject = when (node) {
        is Bookmark -> JSONObject().apply {
            put("type", "bookmark")
            put("title", node.title)
            put("url", node.url)
        }
        is Folder -> JSONObject().apply {
            put("type", "folder")
            put("title", node.title)
            put("children", JSONArray().apply { node.children.forEach { put(nodeToJson(it)) } })
        }
    }

    /**
     * Parses any of the shapes the extension produces or JSONBin returns:
     * the plain export, `{ "bookmarkvault": {...} }` as pushed, or `{ "record": {...} }` with bin metadata.
     */
    fun parse(text: String): Vault {
        val obj = try {
            JSONObject(text.trim())
        } catch (e: Exception) {
            throw FormatException("This is not valid JSON.")
        }
        return parse(obj)
    }

    fun parse(obj: JSONObject): Vault {
        var o = obj
        o.optJSONObject("record")?.let { o = it }
        o.optJSONObject("bookmarkvault")?.let { o = it }
        val roots = o.optJSONArray("roots")
            ?: throw FormatException("No bookmark data found. Is this a BookmarkVault file or bin?")
        val folders = mutableListOf<Folder>()
        val stray = mutableListOf<Node>()
        for (i in 0 until roots.length()) {
            val n = roots.optJSONObject(i)?.let { parseNode(it) } ?: continue
            if (n is Folder) folders += n else stray += n
        }
        if (stray.isNotEmpty()) folders += Folder(title = Vault.ROOT_OTHER, children = stray)
        return Vault(
            version = o.optString("version", Vault.FORMAT_VERSION),
            exportedAt = o.optString("exportedAt", ""),
            device = o.optString("device", ""),
            roots = folders,
        )
    }

    private fun parseNode(o: JSONObject): Node {
        val url = o.optString("url", "")
        val isBookmark = o.optString("type") == "bookmark" || (url.isNotEmpty() && !o.has("children"))
        return if (isBookmark) {
            Bookmark(title = o.optString("title", ""), url = url)
        } else {
            val arr = o.optJSONArray("children") ?: JSONArray()
            Folder(
                title = o.optString("title", "").ifEmpty { "Untitled" },
                children = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseNode(it) } },
            )
        }
    }

    // ── Netscape HTML, same output as the extension ─────────────────
    private fun escAttr(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;")
    private fun escHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun nodeToNetscape(node: Node, depth: Int, out: StringBuilder) {
        val pad = "    ".repeat(depth)
        when (node) {
            is Bookmark -> out.append(pad).append("<DT><A HREF=\"").append(escAttr(node.url)).append("\">")
                .append(escHtml(node.title)).append("</A>\n")
            is Folder -> {
                out.append(pad).append("<DT><H3>").append(escHtml(node.title)).append("</H3>\n")
                out.append(pad).append("<DL><p>\n")
                node.children.forEach { nodeToNetscape(it, depth + 1, out) }
                out.append(pad).append("</DL><p>\n")
            }
        }
    }

    fun toNetscapeHtml(vault: Vault): String {
        val out = StringBuilder()
        out.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        out.append("<!-- BookmarkVault Export — ${vault.exportedAt} — Device: ${vault.device} -->\n")
        out.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        out.append("<TITLE>Bookmarks</TITLE>\n")
        out.append("<H1>Bookmarks</H1>\n")
        out.append("<DL><p>\n")
        vault.roots.forEach { nodeToNetscape(it, 1, out) }
        out.append("</DL><p>")
        return out.toString()
    }
}
