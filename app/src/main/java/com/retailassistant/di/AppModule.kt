package com.retailassistant.di

import androidx.room.Room
import androidx.work.WorkManager
import com.retailassistant.core.ImageHandler
import com.retailassistant.data.db.AppDatabase
import com.retailassistant.data.remote.GeminiClient
import com.retailassistant.data.remote.createAppSupabaseClient
import com.retailassistant.data.repository.RetailRepository
import com.retailassistant.data.repository.RetailRepositoryImpl
import com.retailassistant.features.auth.AuthViewModel
import com.retailassistant.features.customers.CustomerDetailViewModel
import com.retailassistant.features.customers.CustomerListViewModel
import com.retailassistant.features.dashboard.DashboardViewModel
import com.retailassistant.features.invoices.creation.InvoiceCreationViewModel
import com.retailassistant.features.invoices.detail.InvoiceDetailViewModel
import com.retailassistant.features.invoices.list.InvoiceListViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // --- SINGLETONS (Data & Core Layers) ---

    // Networking
    single { createAppSupabaseClient() }
    single {
        HttpClient(Android) {
            engine {
                connectTimeout = 60_000
                socketTimeout = 60_000
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
        }
    }
    single { GeminiClient(get()) }

    // Core
    single { ImageHandler(androidApplication()) }
    single { WorkManager.getInstance(androidApplication()) }

    // Database & Repository
    // The repository now takes the whole AppDatabase instance to allow for transactions.
    single<RetailRepository> { RetailRepositoryImpl(get(), get(), Dispatchers.IO) }
    single {
        Room.databaseBuilder(androidApplication(), AppDatabase::class.java, "retailassistant.db")
            .fallbackToDestructiveMigration(false) // Set to true if you want to allow destructive migrations
            .build()
    }

    // DAOs
    single { get<AppDatabase>().customerDao() }
    single { get<AppDatabase>().invoiceDao() }
    single { get<AppDatabase>().interactionLogDao() }

    // --- VIEWMODELS ---
    viewModel { AuthViewModel(get(), get()) }
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { InvoiceListViewModel(get(), get()) }
    viewModel { CustomerListViewModel(get(), get()) }
    viewModel { params ->
        InvoiceCreationViewModel(
            savedStateHandle = params.get(),
            repository = get(),
            geminiClient = get(),
            imageHandler = get(),
            supabase = get()
        )
    }
    viewModel { params ->
        InvoiceDetailViewModel(
            invoiceId = params.get(),
            repository = get(),
            supabase = get()
        )
    }
    viewModel { params ->
        CustomerDetailViewModel(
            customerId = params.get(),
            repository = get(),
            supabase = get()
        )
    }
}
