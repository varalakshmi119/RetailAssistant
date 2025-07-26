package com.example.retailassistant

import android.app.Application
import com.example.retailassistant.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class RetailAssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger() // Use androidLogger for Koin logs in debug builds
            androidContext(this@RetailAssistantApplication)
            modules(appModule)
        }
    }
}