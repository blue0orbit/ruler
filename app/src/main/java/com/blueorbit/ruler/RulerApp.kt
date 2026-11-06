package com.blueorbit.ruler

import android.app.Application
import com.blueorbit.ruler.core.di.AppContainer

class RulerApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
