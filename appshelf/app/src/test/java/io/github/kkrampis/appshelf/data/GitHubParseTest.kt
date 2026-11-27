package io.github.kkrampis.appshelf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GitHubParseTest {
    @Test
    fun readsReleasesAndAssets() {
        val json = """
            [{"tag_name":"stockvault-v1.0.4","name":"StockVault 1.0.4","body":null,"draft":false,"prerelease":false,
              "published_at":"2026-11-27T14:26:30Z","html_url":"https://github.com/KKrampis/android-apps/releases/tag/stockvault-v1.0.4",
              "assets":[{"name":"StockVault-1.0.4.apk","size":12345678,"download_count":3,
                "url":"https://api.github.com/repos/KKrampis/android-apps/releases/assets/1",
                "browser_download_url":"https://github.com/KKrampis/android-apps/releases/download/stockvault-v1.0.4/StockVault-1.0.4.apk"}]}]
        """.trimIndent()
        val r = GitHubParse.releases(json).single()
        assertEquals("StockVault 1.0.4", r.name)
        assertEquals("", r.body)
        assertFalse(r.prerelease)
        assertEquals(12345678L, r.assets.single().size)
        assertEquals("https://api.github.com/repos/KKrampis/android-apps/releases/assets/1", r.assets.single().apiUrl)
    }
}
