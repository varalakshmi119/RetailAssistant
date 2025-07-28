package com.retailassistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The Room database definition. It serves as the single source of truth for all
 * local data in the application, enabling a robust offline-first experience.
 */
@Database(
    entities = [Customer::class, Invoice::class, InteractionLog::class],
    version = 2, // Version bump for schema changes
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun interactionLogDao(): InteractionLogDao
}
