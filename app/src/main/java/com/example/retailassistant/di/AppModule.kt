package com.example.retailassistant.di

import androidx.room.Room
import com.example.retailassistant.core.ImageHandler
import com.example.retailassistant.data.db.AppDatabase
import com.example.retailassistant.data.remote.GeminiClient
import com.example.retailassistant.data.remote.createSupabaseClient
import com.example.retailassistant.data.repository.RetailRepository
import com.example.retailassistant.data.repository.RetailRepositoryImpl
import com.example.retailassistant.features.auth.AuthViewModel
import com.example.retailassistant.features.customers.CustomerDetailViewModel
import com.example.retailassistant.features.customers.CustomerListViewModel
import com.example.retailassistant.features.dashboard.DashboardViewModel
import com.example.retailassistant.features.invoices.InvoiceCreationViewModel
import com.example.retailassistant.features.invoices.InvoiceDetailViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin dependency injection module. This is the central hub for providing
 * instances of databases, repositories, clients, and ViewModels.
 */
val appModule = module {

    // --- SINGLETONS (Data & Core Layers) ---
    // Provides a single, application-wide instance of the Supabase client.
    single { createSupabaseClient() }

    // Provides single instances of our now-injectable utility classes.
    single { GeminiClient() }
    single { ImageHandler() }

    // Provides a single instance of the Room database.
    single {
        Room.databaseBuilder(androidApplication(), AppDatabase::class.java, "retail_assistant.db")
            // In a real production app, migrations should be handled properly.
            .fallbackToDestructiveMigration(false)
            .build()
    }

    // Provides DAOs from the AppDatabase instance.
    single { get<AppDatabase>().customerDao() }
    single { get<AppDatabase>().invoiceDao() }
    single { get<AppDatabase>().interactionLogDao() }

    // Provides the central repository. The repository pattern abstracts data sources.
    single<RetailRepository> {
        RetailRepositoryImpl(get(), get(), get(), get(), Dispatchers.IO)
    }

    // --- VIEWMODELS ---
    // ViewModels are provided with their dependencies injected by Koin.
    // The `viewModel` factory ensures they are tied to the lifecycle of the Composable.
    viewModel { AuthViewModel(get(), get()) }
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { CustomerListViewModel(get(), get()) }
    viewModel { InvoiceCreationViewModel(get(), get(), get(), get(), androidApplication()) }

    // ViewModels that require runtime parameters (like an ID) use Koin's parameter injection.
    viewModel { params -> InvoiceDetailViewModel(invoiceId = params.get(), repository = get(), supabase = get()) }
    viewModel { params -> CustomerDetailViewModel(customerId = params.get(), repository = get(), supabase = get()) }
}
