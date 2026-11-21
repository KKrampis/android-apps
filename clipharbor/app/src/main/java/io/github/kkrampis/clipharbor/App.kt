package io.github.kkrampis.clipharbor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.kkrampis.clipharbor.data.AppDatabase
import io.github.kkrampis.clipharbor.data.LibraryRepository
import io.github.kkrampis.clipharbor.download.DownloadRepository
import io.github.kkrampis.clipharbor.sync.SyncService
import io.github.kkrampis.clipharbor.sync.SyncSettings
import io.github.kkrampis.clipharbor.sync.SyncWorker
import io.github.kkrampis.clipharbor.yt.YouTube
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        YouTube.init(container.http)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                DOWNLOAD_CHANNEL,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val DOWNLOAD_CHANNEL = "downloads"
    }
}

/** Simple hand-written dependency container shared by the whole app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val db: AppDatabase = AppDatabase.build(appContext)

    val syncSettings = SyncSettings(appContext)

    val library = LibraryRepository(db) {
        val cfg = syncSettings.load()
        if (cfg.autoSync && (cfg.githubReady || cfg.jsonbinReady)) {
            SyncWorker.schedule(appContext)
        }
    }

    val sync = SyncService(http, syncSettings, library)

    val downloads = DownloadRepository(appContext, db)
}

val Context.container: AppContainer
    get() = (applicationContext as App).container
