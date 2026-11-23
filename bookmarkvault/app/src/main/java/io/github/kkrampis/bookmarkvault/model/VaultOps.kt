package io.github.kkrampis.bookmarkvault.model

/** Tree edits. Every function returns a new [Vault] and leaves the old one untouched. */

fun Vault.findFolder(id: String): Folder? {
    fun search(f: Folder): Folder? {
        if (f.id == id) return f
        for (c in f.children) if (c is Folder) search(c)?.let { return it }
        return null
    }
    for (r in roots) search(r)?.let { return it }
    return null
}

/** The chain of folders from a root down to the folder [id], or empty when it is not found. */
fun Vault.pathTo(id: String): List<Folder> {
    fun search(f: Folder, trail: List<Folder>): List<Folder>? {
        val here = trail + f
        if (f.id == id) return here
        for (c in f.children) if (c is Folder) search(c, here)?.let { return it }
        return null
    }
    for (r in roots) search(r, emptyList())?.let { return it }
    return emptyList()
}

/** Every folder with its depth, in tree order, for a folder picker. */
fun Vault.allFolders(): List<Pair<Folder, Int>> {
    val out = mutableListOf<Pair<Folder, Int>>()
    fun walk(f: Folder, depth: Int) {
        out += f to depth
        f.children.filterIsInstance<Folder>().forEach { walk(it, depth + 1) }
    }
    roots.forEach { walk(it, 0) }
    return out
}

/** Every bookmark with the titles of the folders it sits in. */
fun Vault.allBookmarks(): List<Pair<Bookmark, List<String>>> {
    val out = mutableListOf<Pair<Bookmark, List<String>>>()
    fun walk(f: Folder, trail: List<String>) {
        val here = trail + f.title
        for (c in f.children) when (c) {
            is Bookmark -> out += c to here
            is Folder -> walk(c, here)
        }
    }
    roots.forEach { walk(it, emptyList()) }
    return out
}

private fun Folder.mapTree(transform: (Node) -> Node?): Folder =
    copy(children = children.mapNotNull { child ->
        val t = transform(child) ?: return@mapNotNull null
        if (t is Folder) t.mapTree(transform) else t
    })

private fun Vault.mapTree(transform: (Node) -> Node?): Vault =
    copy(roots = roots.map { root ->
        val t = transform(root)
        // Top-level folders can be renamed but never removed or turned into bookmarks.
        if (t is Folder) t.mapTree(transform) else root.mapTree(transform)
    })

fun Vault.addToFolder(folderId: String, node: Node, atTop: Boolean = false): Vault = mapTree {
    if (it is Folder && it.id == folderId) {
        it.copy(children = if (atTop) listOf(node) + it.children else it.children + node)
    } else it
}

fun Vault.replaceNode(node: Node): Vault = mapTree {
    when {
        it.id != node.id -> it
        it is Folder && node is Folder -> it.copy(title = node.title) // keep children
        else -> node
    }
}

fun Vault.removeNode(id: String): Vault = mapTree { if (it.id == id) null else it }

/** Moves [nodeId] into [targetFolderId]. Refuses to move a folder into itself or its own subfolder. */
fun Vault.moveNode(nodeId: String, targetFolderId: String): Vault {
    if (roots.any { it.id == nodeId }) return this
    var moving: Node? = null
    fun find(f: Folder) {
        for (c in f.children) {
            if (c.id == nodeId) moving = c
            if (c is Folder) find(c)
        }
    }
    roots.forEach { find(it) }
    val node = moving ?: return this
    if (node is Folder && (node.id == targetFolderId || findIn(node, targetFolderId))) return this
    return removeNode(nodeId).addToFolder(targetFolderId, node)
}

private fun findIn(folder: Folder, id: String): Boolean =
    folder.children.any { it.id == id || (it is Folder && findIn(it, id)) }

/** The root to put imports and shared links in: Other bookmarks, else Mobile bookmarks, else the last one. */
fun Vault.otherRoot(): Folder? =
    roots.firstOrNull { it.title.equals(Vault.ROOT_OTHER, ignoreCase = true) }
        ?: roots.firstOrNull { it.title.equals(Vault.ROOT_MOBILE, ignoreCase = true) }
        ?: roots.lastOrNull()

fun Vault.mobileRoot(): Folder? =
    roots.firstOrNull { it.title.equals(Vault.ROOT_MOBILE, ignoreCase = true) } ?: otherRoot()

/**
 * Choices for importing a pulled or opened file, keyed exactly like the extension's folder
 * picker: `"ri-ci"` is folder `ci` inside root `ri`, and `"ri--1"` is the loose bookmarks
 * that sit directly in root `ri`.
 */
data class ImportChoice(val key: String, val rootTitle: String, val title: String, val count: Int, val isLoose: Boolean)

fun Vault.importChoices(): List<ImportChoice> {
    val out = mutableListOf<ImportChoice>()
    roots.forEachIndexed { ri, root ->
        val loose = root.children.filterIsInstance<Bookmark>()
        if (loose.isNotEmpty()) out += ImportChoice("$ri--1", root.title, "Loose bookmarks", loose.size, true)
        root.children.forEachIndexed { ci, child ->
            if (child is Folder) out += ImportChoice("$ri-$ci", root.title, child.title, countBookmarks(child), false)
        }
    }
    return out
}

/**
 * Adds the selected parts of [incoming] into this vault under one dated folder in Other bookmarks,
 * the same way the extension imports into Chrome. [selected] null means everything.
 * Returns the new vault and how many bookmarks were added.
 */
fun Vault.importInto(incoming: Vault, selected: Set<String>?, folderTitle: String): Pair<Vault, Int> {
    val added = mutableListOf<Node>()
    incoming.roots.forEachIndexed { ri, root ->
        if (selected == null || "$ri--1" in selected) {
            root.children.filterIsInstance<Bookmark>().forEach { b -> b.onlyWebLinks()?.let { added += it.withNewIds() } }
        }
        root.children.forEachIndexed { ci, child ->
            if (child is Folder && (selected == null || "$ri-$ci" in selected)) {
                child.onlyWebLinks()?.let { added += it.withNewIds() }
            }
        }
    }
    val count = added.sumOf { countBookmarks(it) }
    val base = if (roots.isEmpty()) copy(roots = listOf(Folder(title = Vault.ROOT_OTHER))) else this
    val target = base.otherRoot()!!
    return base.addToFolder(target.id, Folder(title = folderTitle, children = added)) to count
}

/** Makes this phone's library an exact copy of [incoming] (keeping only web links). */
fun Vault.replaceWith(incoming: Vault): Vault = copy(
    roots = incoming.roots.map { (it.onlyWebLinks() as Folder).withNewIds() as Folder },
)
