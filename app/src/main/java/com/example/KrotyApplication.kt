package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.worker.SmartBalanceWorker

class KrotyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SmartBalanceWorker.schedulePeriodic(this)
    }
}
