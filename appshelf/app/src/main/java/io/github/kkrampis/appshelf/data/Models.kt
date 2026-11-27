package io.github.kkrampis.appshelf.data

/** A GitHub repository that AppShelf reads releases from. */
data class Source(val owner: String, val repo: String) {
    val key: String get() = "$owner/$repo"
    val url: String get() = "https://github.com/$owner/$repo"
    val releasesUrl: String get() = "$url/releases"

    override fun toString() = key

    companion object {
        /** The collection this app lives in, listed by default. */
        val DEFAULT = Source("KKrampis", "android-apps")
    }
}

data class Asset(
    val name: String,
    /** Public download link (github.com/…/releases/download/…). */
    val downloadUrl: String,
    /** API link, which downloads the file with a token (needed for private repositories). */
    val apiUrl: String,
    val size: Long,
    val downloads: Int,
)

data class Release(
    val tag: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val htmlUrl: String,
    val assets: List<Asset>,
)

/** One installable version of an app: a release and the APK in it that fits this phone best. */
data class AppVersion(
    val version: String,
    val release: Release,
    val apk: Asset,
)

/** An app found in a repository's releases, with every version, newest first. */
data class CatalogApp(
    val source: Source,
    /** Lower-case letters and digits, unique inside the source (for example `stockvault`). */
    val key: String,
    val name: String,
    val versions: List<AppVersion>,
) {
    val latest: AppVersion get() = versions.first()
    val id: String get() = "${source.key}/$key"
}
