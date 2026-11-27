package io.github.kkrampis.appshelf.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogTest {
    private val repo = Source("KKrampis", "android-apps")

    private fun release(name: String, tag: String, vararg assets: String, date: String = "2026-11-27T00:00:00Z", pre: Boolean = false) =
        Release(
            tag = tag, name = name, body = "", publishedAt = date, prerelease = pre, draft = false, htmlUrl = "",
            assets = assets.map { Asset(it, "https://x/$it", "https://api/$it", 1000, 0) },
        )

    @Test
    fun groupsMonorepoReleasesIntoApps() {
        val releases = listOf(
            release("StockVault 1.0.4", "stockvault-v1.0.4", "StockVault-1.0.4.apk", date = "2026-11-27T14:00:00Z"),
            release("ClipHarbor 1.0.8", "clipharbor-v1.0.8", "ClipHarbor-1.0.8.apk", date = "2026-11-25T00:00:00Z"),
            release("ClipHarbor 1.0.10", "clipharbor-v1.0.10", "ClipHarbor-1.0.10.apk", date = "2026-11-26T00:00:00Z"),
            release("ClipHarbor 1.0.5", "clipharbor-v1.0.5", "ClipHarbor-1.0.5.apk", date = "2026-11-21T00:00:00Z"),
            release("Notes only", "docs", "README.md"),
        )
        val apps = Catalog.build(repo, releases, includePrereleases = false)
        assertEquals(listOf("ClipHarbor", "StockVault"), apps.map { it.name })
        val clip = apps.first()
        assertEquals("clipharbor", clip.key)
        assertEquals(listOf("1.0.10", "1.0.8", "1.0.5"), clip.versions.map { it.version })
        assertEquals("ClipHarbor-1.0.10.apk", clip.latest.apk.name)
        assertEquals("KKrampis/android-apps/stockvault", apps[1].id)
    }

    @Test
    fun singleAppRepoWithPlainTags() {
        val r = Source("someone", "CoolApp")
        val releases = listOf(
            release("v2.0", "v2.0", "app-release.apk"),
            release("Big summer update", "v1.9", "app-release.apk", date = "2026-06-01T00:00:00Z"),
        )
        val apps = Catalog.build(r, releases, includePrereleases = false)
        assertEquals(1, apps.size)
        assertEquals("CoolApp", apps[0].name)
        assertEquals(listOf("2.0", "1.9"), apps[0].versions.map { it.version })
    }

    @Test
    fun picksApkForThisPhone() {
        val r = Source("someone", "Player")
        val releases = listOf(
            release(
                "Player 3.1", "v3.1",
                "Player-3.1-armeabi-v7a.apk", "Player-3.1-arm64-v8a.apk", "Player-3.1-universal.apk",
            ),
        )
        val arm64 = Catalog.build(r, releases, false, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals(1, arm64.size)
        assertEquals("Player-3.1-arm64-v8a.apk", arm64[0].latest.apk.name)
        val other = Catalog.build(r, releases, false, listOf("riscv64"))
        assertEquals("Player-3.1-universal.apk", other[0].latest.apk.name)
    }

    @Test
    fun skipsPrereleasesUnlessAsked() {
        val releases = listOf(
            release("Tool 2.0-beta", "v2.0-beta", "Tool-2.0-beta.apk", pre = true),
            release("Tool 1.9", "v1.9", "Tool-1.9.apk"),
        )
        assertEquals("1.9", Catalog.build(repo, releases, false)[0].latest.version)
        assertEquals("2.0-beta", Catalog.build(repo, releases, true)[0].latest.version)
    }

    @Test
    fun namesFromReleaseAndFiles() {
        assertEquals("StockVault", Catalog.stripVersion("StockVault 1.0.4"))
        assertEquals("stockvault", Catalog.stripVersion("stockvault-v1.0.4"))
        assertEquals("App2", Catalog.stripVersion("App2 1.0"))
        assertEquals(null, Catalog.stripVersion("v1.2"))
        assertEquals(null, Catalog.stripVersion("Summer update"))
        assertEquals(null, Catalog.stripVersion("Release 1.0"))
        assertEquals("StockVault", Catalog.baseName("StockVault-1.0.4.apk"))
        assertEquals("", Catalog.baseName("app-arm64-v8a-release.apk"))
    }
}
