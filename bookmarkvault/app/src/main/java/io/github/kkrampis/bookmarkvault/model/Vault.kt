package io.github.kkrampis.bookmarkvault.model

import java.util.UUID

/**
 * The same bookmark tree the BookmarkVault Chrome extension writes:
 *
 * ```json
 * { "version": "1.0", "exportedAt": "...", "device": "...", "totalCount": 12,
 *   "roots": [ { "type": "folder", "title": "Bookmarks bar", "children": [
 *       { "type": "bookmark", "title": "...", "url": "https://..." } ] } ] }
 * ```
 *
 * Each node also gets an [id] that lives only in the app, so a node can be edited or deleted.
 * Ids are never written to the JSON, so files stay identical to what the extension makes.
 */
sealed interface Node {
    val id: String
    val title: String
}

data class Bookmark(
    override val id: String = newId(),
    override val title: String,
    val url: String,
) : Node

data class Folder(
    override val id: String = newId(),
    override val title: String,
    val children: List<Node> = emptyList(),
) : Node

data class Vault(
    val version: String = FORMAT_VERSION,
    val exportedAt: String = "",
    val device: String = "",
    val roots: List<Folder> = emptyList(),
) {
    val totalCount: Int get() = roots.sumOf { countBookmarks(it) }
    val folderCount: Int get() = roots.sumOf { countFolders(it) }

    companion object {
        const val FORMAT_VERSION = "1.0"
        const val ROOT_BAR = "Bookmarks bar"
        const val ROOT_OTHER = "Other bookmarks"
        const val ROOT_MOBILE = "Mobile bookmarks"

        /** A fresh library, with the same three top-level folders Chrome has. */
        fun empty(device: String) = Vault(
            device = device,
            roots = listOf(Folder(title = ROOT_BAR), Folder(title = ROOT_OTHER), Folder(title = ROOT_MOBILE)),
        )
    }
}

fun newId(): String = UUID.randomUUID().toString()

fun countBookmarks(node: Node): Int = when (node) {
    is Bookmark -> 1
    is Folder -> node.children.sumOf { countBookmarks(it) }
}

/** Folders inside [folder], not counting [folder] itself. */
fun countFolders(folder: Folder): Int =
    folder.children.filterIsInstance<Folder>().sumOf { 1 + countFolders(it) }

/** Copies a subtree with new ids, so it can be added next to the original. */
fun Node.withNewIds(): Node = when (this) {
    is Bookmark -> copy(id = newId())
    is Folder -> copy(id = newId(), children = children.map { it.withNewIds() })
}

/** Drops bookmarks that are not web links (bookmarklets, chrome:// pages), as the extension does on import. */
fun Node.onlyWebLinks(): Node? = when (this) {
    is Bookmark -> if (url.startsWith("http://") || url.startsWith("https://")) this else null
    is Folder -> copy(children = children.mapNotNull { it.onlyWebLinks() })
}
