package com.retailassistant.di

import androidx.room.Room
import com.retailassistant.core.ImageHandler
import com.retailassistant.data.db.AppDatabase
import com.retailassistant.data.remote.GeminiClient
import com.retailassistant.data.remote.createSupabaseClient
import com.retailassistant.data.repository.RetailRepository
import com.retailassistant.data.repository.RetailRepositoryImpl
import com.retailassistant.features.auth.AuthViewModel
import com.retailassistant.features.customers.CustomerDetailViewModel
import com.retailassistant.features.customers.CustomerListViewModel
import com.retailassistant.features.dashboard.DashboardViewModel
import com.retailassistant.features.invoices.creation.InvoiceCreationViewModel
import com.retailassistant.features.invoices.detail.InvoiceDetailViewModel
import com.retailassistant.features.invoices.list.InvoiceListViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
val appModule = module {
    // --- SINGLETONS (Data & Core Layers) ---
    single { createSupabaseClient() }
    single { GeminiClient() }
    single { ImageHandler(androidApplication()) }
    single {
        Room.databaseBuilder(androidApplication(), AppDatabase::class.java, "retailassistant.db")
            .fallbackToDestructiveMigration(false)
            .build()
    }
    // DAOs
    single { get<AppDatabase>().customerDao() }
    single { get<AppDatabase>().invoiceDao() }
    single { get<AppDatabase>().interactionLogDao() }
    // Repository
    single<RetailRepository> {
        RetailRepositoryImpl(get(), get(), get(), get(), get(), Dispatchers.IO)
    }

    // --- VIEWMODELS ---
    viewModel { AuthViewModel(get(), get()) }
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { InvoiceListViewModel(get(), get()) }
    viewModel { CustomerListViewModel(get(), get()) }
    viewModel { params -> InvoiceCreationViewModel(get(), get(), get(), get(), params.get()) }
    viewModel { params -> InvoiceDetailViewModel(invoiceId = params.get(), repository = get()) }
    viewModel { params -> CustomerDetailViewModel(customerId = params.get(), repository = get()) }
}