package com.example.retailassistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The Room database definition. It serves as the single source of truth for all
 * local data in the application.
 */
@Database(
    entities = [Customer::class, Invoice::class, InteractionLog::class],
    version = 1,
    exportSchema = false // Schema export is recommended for production apps but disabled here for simplicity.
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun interactionLogDao(): InteractionLogDao
}
