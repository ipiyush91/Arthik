package app.arthik

import android.app.Application
import androidx.work.Configuration
import app.arthik.notify.Notifier
import app.arthik.work.Schedules

class ArthikApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        Schedules.ensureDaily(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()
}

val android.content.Context.container: AppContainer get() = (applicationContext as ArthikApp).container
