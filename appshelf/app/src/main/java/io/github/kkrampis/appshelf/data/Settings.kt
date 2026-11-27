package io.github.kkrampis.appshelf.data

import android.content.Context

/** Plain settings live in shared preferences; the GitHub token goes through [SecureStore]. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

    /** Repositories to list apps from, in the order they were added. */
    var sources: List<Source>
        get() {
            val raw = prefs.getString("sources", null) ?: return listOf(Source.DEFAULT)
            return raw.lines().mapNotNull { RepoUrl.parse(it) }.distinctBy { it.key.lowercase() }
        }
        set(v) = prefs.edit().putString("sources", v.joinToString("\n") { it.key }).apply()

    var token: String
        get() = secure.get("githubToken").orEmpty()
        set(v) = secure.put("githubToken", v.trim())

    var includePrereleases: Boolean
        get() = prefs.getBoolean("includePrereleases", false)
        set(v) = prefs.edit().putBoolean("includePrereleases", v).apply()

    /** Check for updates every few hours and post a notification when there are some. */
    var backgroundChecks: Boolean
        get() = prefs.getBoolean("backgroundChecks", true)
        set(v) = prefs.edit().putBoolean("backgroundChecks", v).apply()

    var lastCheckedAt: Long
        get() = prefs.getLong("lastCheckedAt", 0L)
        set(v) = prefs.edit().putLong("lastCheckedAt", v).apply()

    /**
     * The Android package of each app, learned from the APK the first time it is downloaded
     * (release metadata doesn't say), so the installed version can be found afterwards.
     */
    fun packageFor(appId: String): String? = prefs.getString("pkg:$appId", null)

    fun rememberPackage(appId: String, packageName: String) =
        prefs.edit().putString("pkg:$appId", packageName).apply()

    /** Updates already announced in a notification, as `appId@version`, so each is announced once. */
    var notified: Set<String>
        get() = prefs.getStringSet("notified", emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet("notified", v).apply()
}
