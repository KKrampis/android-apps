package io.github.kkrampis.radiorecorder

import android.app.Application
import io.github.kkrampis.radiorecorder.service.RecorderService
import io.github.kkrampis.radiorecorder.work.CleanupWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RecorderService.createChannel(this)
        CleanupWorker.schedule(this)
    }
}
