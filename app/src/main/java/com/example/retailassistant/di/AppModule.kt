package com.example.retailassistant.di
import androidx.room.Room
import com.example.retailassistant.BuildConfig
import com.example.retailassistant.data.AppDatabase
import com.example.retailassistant.data.InvoiceRepository
import com.example.retailassistant.ui.viewmodel.AuthViewModel
import com.example.retailassistant.ui.viewmodel.CustomerDetailViewModel
import com.example.retailassistant.ui.viewmodel.CustomerViewModel
import com.example.retailassistant.ui.viewmodel.DashboardViewModel
import com.example.retailassistant.ui.viewmodel.InvoiceCreationViewModel
import com.example.retailassistant.ui.viewmodel.InvoiceDetailViewModel
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
val appModule = module {
    // Supabase Singleton
    single {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
    // Room Database Singleton
    single {
        Room.databaseBuilder(
            androidApplication(),
            AppDatabase::class.java,
            "retail-assistant-db"
        ).fallbackToDestructiveMigration(false).build()
    }
    // DAOs (provided from the AppDatabase)
    single { get<AppDatabase>().invoiceDao() }
    single { get<AppDatabase>().customerDao() }
    single { get<AppDatabase>().interactionLogDao() }
    // Repository Singleton
    single { InvoiceRepository(get(), get(), get(), get(), Dispatchers.IO) }
    // ViewModels
    viewModel { AuthViewModel(get(), get()) }
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { CustomerViewModel(get()) }
    viewModel { InvoiceCreationViewModel(get()) }
    viewModel { params -> InvoiceDetailViewModel(invoiceId = params.get(), repository = get()) }
    viewModel { params -> CustomerDetailViewModel(customerId = params.get(), repository = get()) }
}