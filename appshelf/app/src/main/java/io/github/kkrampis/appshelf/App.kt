package io.github.kkrampis.appshelf

import android.app.Application
import io.github.kkrampis.appshelf.data.Settings
import io.github.kkrampis.appshelf.work.UpdateWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateWorker.createChannel(this)
        UpdateWorker.schedule(this, Settings(this).backgroundChecks)
    }
}
