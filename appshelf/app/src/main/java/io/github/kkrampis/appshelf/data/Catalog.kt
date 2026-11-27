package io.github.kkrampis.appshelf.data

/**
 * Turns a repository's releases into apps.
 *
 * A repository can hold one app (every release is a new version of it) or many, like
 * KKrampis/android-apps, where each app publishes releases named `StockVault 1.0.4` with tags
 * like `stockvault-v1.0.4`. Releases are grouped into apps by the name before the version,
 * read from the release name, then the tag, then the APK file name.
 */
object Catalog {
    /** Words in APK names that describe a build variant, not the app. */
    private val VARIANT_WORDS = setOf(
        "release", "debug", "signed", "unsigned", "universal", "aligned", "app", "apk", "android",
        "arm64", "v8a", "armeabi", "v7a", "x86", "64", "x86_64", "all", "fdroid", "github", "foss",
    )
    private val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun build(
        source: Source,
        releases: List<Release>,
        includePrereleases: Boolean,
        deviceAbis: List<String> = emptyList(),
    ): List<CatalogApp> {
        val byKey = LinkedHashMap<String, MutableList<Pair<String, AppVersion>>>()
        for (release in releases) {
            if (release.draft || (release.prerelease && !includePrereleases)) continue
            val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            if (apks.isEmpty()) continue

            val groups = apks.groupBy { baseName(it.name) }
            for ((base, assets) in groups) {
                val apk = pickForDevice(assets, deviceAbis)
                val name = when {
                    // One app in this release: its release name or tag names it best.
                    groups.size == 1 -> appName(release) ?: base.ifEmpty { null } ?: source.repo
                    else -> base.ifEmpty { source.repo }
                }
                val key = keyOf(name).ifEmpty { keyOf(source.repo) }
                val version = Versions.find(release.name)
                    ?: Versions.find(release.tag)
                    ?: Versions.find(apk.name.removeSuffix(".apk"))
                    ?: release.tag.ifEmpty { release.publishedAt.take(10) }
                byKey.getOrPut(key) { mutableListOf() }.add(name to AppVersion(version, release, apk))
            }
        }

        return byKey.map { (key, entries) ->
            val versions = entries.map { it.second }
                .sortedWith(
                    Comparator<AppVersion> { a, b -> Versions.compare(b.version, a.version) }
                        .thenByDescending { it.release.publishedAt },
                )
                .distinctBy { it.version }
            // Use the name as written in the newest release.
            val newest = versions.first()
            val name = entries.first { it.second === newest }.first
            CatalogApp(source, key, name, versions)
        }.sortedBy { it.name.lowercase() }
    }

    /** The app name in a release, from its name (`StockVault 1.0.4`) or tag (`stockvault-v1.0.4`). */
    fun appName(release: Release): String? {
        stripVersion(release.name)?.let { return it }
        return stripVersion(release.tag)
    }

    /**
     * `StockVault 1.0.4` → `StockVault`; `stockvault-v1.0.4` → `stockvault`. Null when there is no
     * name before the version (`v1.2`) or no version at all (a release titled `Summer update`
     * says nothing about which app it is).
     */
    fun stripVersion(text: String): String? {
        val m = VERSIONED_NAME.find(text.trim()) ?: return null
        val before = m.groupValues[1].trim().trim('-', '_', ' ', ':')
        if (before.isEmpty() || before.none(Char::isLetter)) return null
        if (keyOf(before) in GENERIC_NAMES) return null
        return before
    }

    private val VERSIONED_NAME =
        Regex("""^(.*?)(?:^|[\s_-]+)(?:v|version\s*)?\d+(?:\.\d+)*\b.*$""", RegexOption.IGNORE_CASE)
    private val GENERIC_NAMES = setOf("release", "version", "build", "app", "apk", "update")

    /** `StockVault-1.0.4.apk` → `StockVault`; `app-arm64-v8a-release.apk` → `` (only variant words). */
    fun baseName(fileName: String): String {
        val noExt = fileName.removeSuffix(".apk").removeSuffix(".APK")
        val words = noExt.split('-', '_', ' ', '.').filter { it.isNotEmpty() }
        val kept = words.takeWhile { w -> !w.first().isDigit() && !(w.length > 1 && w[0].lowercaseChar() == 'v' && w[1].isDigit()) }
            .filter { it.lowercase() !in VARIANT_WORDS }
        return kept.joinToString(" ")
    }

    fun keyOf(name: String): String = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    /** Prefers an APK built for this phone's processor, then a universal one, then the first. */
    fun pickForDevice(assets: List<Asset>, deviceAbis: List<String>): Asset {
        if (assets.size == 1) return assets.first()
        for (abi in deviceAbis) {
            assets.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }
        return assets.firstOrNull { a -> ABIS.none { a.name.contains(it, ignoreCase = true) } }
            ?: assets.first()
    }
}
