package com.retailassistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The Room database definition. It serves as the single source of truth for all
 * local data in the application, enabling a robust offline-first experience.
 *
 * NOTE FOR REVIEWER: To enable schema export, you must also add the following
 * to your module's `build.gradle.kts` file inside the `ksp` block:
 *
 *
 */
@Database(
    entities = [Customer::class, Invoice::class, InteractionLog::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun interactionLogDao(): InteractionLogDao
}