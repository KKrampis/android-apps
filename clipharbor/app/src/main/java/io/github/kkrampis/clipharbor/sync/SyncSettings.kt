package io.github.kkrampis.clipharbor.sync

import android.content.Context

data class SyncConfig(
    /** "owner/name" of the GitHub repository that receives the playlists file. */
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val githubPath: String = "clipharbor/playlists.json",
    val githubToken: String = "",
    val jsonbinKey: String = "",
    val jsonbinBinId: String = "",
    val autoSync: Boolean = false,
    val lastSyncMessage: String = "",
) {
    val githubReady: Boolean
        get() = githubRepo.contains('/') && githubToken.isNotBlank() && githubPath.isNotBlank()
    val jsonbinReady: Boolean get() = jsonbinKey.isNotBlank()
}

/** Sync settings. Plain options go in normal preferences; the token and key go in [SecureStore]. */
class SyncSettings(context: Context) {
    private val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

    fun load(): SyncConfig = SyncConfig(
        githubRepo = prefs.getString("github_repo", "") ?: "",
        githubBranch = prefs.getString("github_branch", "main") ?: "main",
        githubPath = prefs.getString("github_path", "clipharbor/playlists.json") ?: "clipharbor/playlists.json",
        githubToken = secure.get("github_token") ?: "",
        jsonbinKey = secure.get("jsonbin_key") ?: "",
        jsonbinBinId = prefs.getString("jsonbin_bin", "") ?: "",
        autoSync = prefs.getBoolean("auto_sync", false),
        lastSyncMessage = prefs.getString("last_sync", "") ?: "",
    )

    fun save(cfg: SyncConfig) {
        prefs.edit()
            .putString("github_repo", cfg.githubRepo.trim().removePrefix("https://github.com/").trim('/'))
            .putString("github_branch", cfg.githubBranch.trim().ifBlank { "main" })
            .putString("github_path", cfg.githubPath.trim().trim('/').ifBlank { "clipharbor/playlists.json" })
            .putString("jsonbin_bin", cfg.jsonbinBinId.trim())
            .putBoolean("auto_sync", cfg.autoSync)
            .apply()
        secure.put("github_token", cfg.githubToken.trim())
        secure.put("jsonbin_key", cfg.jsonbinKey.trim())
    }

    fun setJsonbinBinId(id: String) {
        prefs.edit().putString("jsonbin_bin", id).apply()
    }

    fun setLastSync(message: String) {
        prefs.edit().putString("last_sync", message).apply()
    }
}
