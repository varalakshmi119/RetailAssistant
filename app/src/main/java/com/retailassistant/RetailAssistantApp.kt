package com.retailassistant

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.retailassistant.data.image.SupabaseUrlKeyer
import com.retailassistant.data.settings.SettingsRepository
import com.retailassistant.di.appModule
import com.retailassistant.workers.NotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.util.concurrent.TimeUnit

class RetailAssistantApp : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@RetailAssistantApp)
            modules(appModule)
        }
        settingsRepository = SettingsRepository(this)
        observeSettings()
    }

    private fun observeSettings() {
        applicationScope.launch {
            // Observe notifications setting
            settingsRepository.notificationsEnabled.collect { isEnabled ->
                if (isEnabled) {
                    setupPeriodicWork()
                } else {
                    WorkManager.getInstance(this@RetailAssistantApp)
                        .cancelUniqueWork(NotificationWorker.WORK_NAME)
                }
            }
        }
    }

    private fun setupPeriodicWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodicWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(8, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWorkRequest
        )
    }

    /**
     * Provides a custom Coil ImageLoader to configure disk cache based on user settings.
     * The SupabaseUrlKeyer is crucial for caching Supabase's signed image URLs effectively.
     * Note: Changes to permanent storage setting require app restart to take effect.
     */
    override fun newImageLoader(): ImageLoader {
        val isPermanentStorageEnabled = runBlocking { settingsRepository.permanentStorageEnabled.first() }
        
        return ImageLoader.Builder(this)
            .components {
                add(SupabaseUrlKeyer())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Use 25% of app's available memory
                    .build()
            }
            .diskCache {
                if (isPermanentStorageEnabled) {
                    DiskCache.Builder()
                        .directory(this.filesDir.resolve("permanent_image_cache"))
                        .maxSizeBytes(500L * 1024 * 1024) // 500 MB disk cache for permanent storage
                        .build()
                } else {
                    DiskCache.Builder()
                        .directory(this.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(100L * 1024 * 1024) // 100 MB disk cache for temporary storage
                        .build()
                }
            }
            .respectCacheHeaders(false) // Allows caching of temporary URLs by using our keyer
            .build()
    }
}