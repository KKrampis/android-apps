package io.github.kkrampis.appshelf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun findsVersions() {
        assertEquals("1.0.4", Versions.find("StockVault 1.0.4"))
        assertEquals("1.0.4", Versions.find("stockvault-v1.0.4"))
        assertEquals("2.3", Versions.find("v2.3"))
        assertEquals("1.2.0-beta1", Versions.find("MyApp 1.2.0-beta1"))
        assertEquals("1.5", Versions.find("App-x86_64-1.5"))
    }

    @Test
    fun comparesNumberByNumber() {
        assertTrue(Versions.compare("1.0.10", "1.0.9") > 0)
        assertTrue(Versions.compare("1.1", "1.0.99") > 0)
        assertEquals(0, Versions.compare("1.0", "1.0.0"))
        assertEquals(0, Versions.compare("v1.2", "1.2"))
        assertTrue(Versions.compare("1.2-beta", "1.2") < 0)
    }

    @Test
    fun newerThanInstalled() {
        assertTrue(Versions.isNewer("1.0.5", "1.0.4"))
        assertFalse(Versions.isNewer("1.0.4", "1.0.4"))
        assertFalse(Versions.isNewer("1.0.3", "1.0.4"))
    }
}
