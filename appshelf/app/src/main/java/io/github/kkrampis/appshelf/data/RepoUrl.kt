package io.github.kkrampis.appshelf.data

/** Reads a GitHub repository from whatever the user typed or shared. */
object RepoUrl {
    private val OWNER = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
    private val REPO = Regex("^[A-Za-z0-9._-]{1,100}$")
    private val IN_TEXT = Regex("""(?:https?://)?(?:www\.)?github\.com[/:]([A-Za-z0-9-]+)/([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)

    /**
     * Accepts `owner/repo`, `https://github.com/owner/repo` (with or without `/releases`,
     * `.git` or anything after it), `github.com/owner/repo` and `git@github.com:owner/repo.git`.
     */
    fun parse(input: String): Source? {
        var s = input.trim()
        if (s.isEmpty()) return null
        IN_TEXT.find(s)?.let { return build(it.groupValues[1], it.groupValues[2]) }
        // Plain owner/repo.
        s = s.trim('/')
        val parts = s.split('/')
        if (parts.size != 2) return null
        return build(parts[0], parts[1])
    }

    /** The first GitHub repository link in a piece of shared text, if any. */
    fun findIn(text: String): Source? =
        IN_TEXT.find(text)?.let { build(it.groupValues[1], it.groupValues[2]) }

    private fun build(owner: String, repoRaw: String): Source? {
        val repo = repoRaw.removeSuffix(".git").trimEnd('.')
        if (!OWNER.matches(owner) || !REPO.matches(repo) || repo == "." || repo == "..") return null
        // github.com/orgs/…, /settings and similar are pages, not repositories.
        if (owner.lowercase() in RESERVED) return null
        return Source(owner, repo)
    }

    private val RESERVED = setOf(
        "orgs", "settings", "marketplace", "explore", "topics", "collections", "sponsors",
        "notifications", "login", "join", "about", "features", "pricing", "apps", "search",
    )
}
