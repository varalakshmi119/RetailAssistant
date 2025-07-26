package com.example.retailassistant.di

import androidx.room.Room
import com.example.retailassistant.data.AppDatabase
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.ui.viewmodel.AuthViewModel
import com.example.retailassistant.ui.viewmodel.DashboardViewModel
import com.example.retailassistant.ui.viewmodel.CustomerViewModel
import com.example.retailassistant.ui.viewmodel.InvoiceViewModel
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Supabase Singleton
    single {
        createSupabaseClient(
            supabaseUrl = "https://zpyenxdclzrpmmlyxnyl.supabase.co",
            supabaseKey = "sb_publishable_-8BFZCc_FSd5YOuDJX3bRQ_vb0oripl"
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }

    // Room Database Singleton
    single {
        Room.databaseBuilder(
            get(), // Koin provides the ApplicationContext
            AppDatabase::class.java,
            "retail-assistant-db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    // DAOs (provided from the AppDatabase)
    single { get<AppDatabase>().invoiceDao() }
    single { get<AppDatabase>().customerDao() }

    // Repository Singleton
    single { InvoiceRepository(get(), get(), get()) }

    // ViewModels
    viewModel { AuthViewModel(get(), get()) }
    viewModel { DashboardViewModel(get()) }
    viewModel { CustomerViewModel(get()) }
    viewModel { InvoiceViewModel(get()) }
}