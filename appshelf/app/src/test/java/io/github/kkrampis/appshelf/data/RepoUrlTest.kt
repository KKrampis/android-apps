package io.github.kkrampis.appshelf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepoUrlTest {
    private val expected = Source("KKrampis", "android-apps")

    @Test
    fun readsEveryCommonForm() {
        listOf(
            "KKrampis/android-apps",
            "https://github.com/KKrampis/android-apps",
            "https://github.com/KKrampis/android-apps/",
            "https://github.com/KKrampis/android-apps/releases",
            "https://github.com/KKrampis/android-apps/releases/tag/stockvault-v1.0.4",
            "http://www.github.com/KKrampis/android-apps.git",
            "github.com/KKrampis/android-apps",
            "git@github.com:KKrampis/android-apps.git",
            "  https://github.com/KKrampis/android-apps?tab=readme  ",
        ).forEach { assertEquals(it, expected, RepoUrl.parse(it)) }
    }

    @Test
    fun rejectsThingsThatAreNotRepositories() {
        listOf("", "android-apps", "https://example.com/a/b", "https://github.com/orgs/foo", "a/b/c", "bad owner/repo")
            .forEach { assertNull(it, RepoUrl.parse(it)) }
    }

    @Test
    fun findsLinkInSharedText() {
        assertEquals(expected, RepoUrl.findIn("Look at this: https://github.com/KKrampis/android-apps cool"))
    }
}
