package com.pinakes.app

import android.app.Application
import com.pinakes.app.di.ServiceLocator

/** Application entry point; owns the single [ServiceLocator]. */
class PinakesApplication : Application() {

    lateinit var services: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        services = ServiceLocator(this)
    }
}
