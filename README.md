# Retail Assistant Android App

A comprehensive Android application built with Jetpack Compose for managing invoices and customers in retail businesses.

## Features

- **Authentication**: Secure user authentication with Supabase Auth
- **Invoice Management**: Add, view, and track invoices with AI-powered data extraction
- **Customer Management**: Maintain customer database with contact information
- **Offline-First**: Works offline with automatic sync when online
- **AI Integration**: Automatic invoice data extraction using Google Gemini API
- **Modern UI**: Built with Jetpack Compose and Material Design 3

## Architecture

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture Pattern**: MVI (Model-View-Intent)
- **Dependency Injection**: Koin
- **Database**: Room (local) + Supabase Postgres (remote)
- **Backend**: Supabase (Auth, Database, Storage)
- **AI/ML**: Google Gemini API for invoice data extraction

## Setup Instructions

### Prerequisites

1. Android Studio (latest version)
2. Android SDK (API level 26+)
3. Supabase account and project
4. Google Gemini API key

### Configuration

1. **Supabase Setup**:
   - The app is already configured with Supabase URL and key
   - Create the following tables in your Supabase database:

   ```sql
   -- Customers table
   CREATE TABLE customers (
     id TEXT PRIMARY KEY,
     name TEXT NOT NULL,
     phone TEXT,
     userId TEXT NOT NULL
   );

   -- Invoices table
   CREATE TABLE invoices (
     id TEXT PRIMARY KEY,
     customerId TEXT NOT NULL,
     totalAmount REAL NOT NULL,
     amountPaid REAL DEFAULT 0.0,
     issueDate TEXT NOT NULL,
     status TEXT DEFAULT 'UNPAID',
     originalScanUrl TEXT NOT NULL,
     createdAt INTEGER NOT NULL,
     userId TEXT NOT NULL
   );

   -- Create storage bucket for invoice images
   INSERT INTO storage.buckets (id, name, public) VALUES ('invoice-images', 'invoice-images', true);
   ```

2. **Gemini API Setup**:
   - Get your API key from Google AI Studio
   - Update the API key in `data/DataSources.kt`:
   ```kotlin
   private const val API_KEY = "YOUR_GEMINI_API_KEY"
   ```

### Building the App

1. Clone the repository
2. Open in Android Studio
3. Sync the project
4. Build and run on device/emulator

```bash
./gradlew assembleDebug
```

## Project Structure

```
app/src/main/java/com/example/retailassistant/
├── RetailAssistantApplication.kt    # Application class
├── MainActivity.kt                  # Main activity with navigation
├── di/
│   └── AppModule.kt                # Koin dependency injection setup
├── data/
│   ├── Models.kt                   # Data classes and entities
│   ├── DataSources.kt              # Room database and API clients
│   └── InvoiceRepository.kt        # Repository pattern implementation
├── service/
│   └── NotificationWorker.kt       # Background notification worker
└── ui/
    ├── MviViewModel.kt             # Base MVI ViewModel
    ├── Components.kt               # Reusable UI components
    ├── AuthScreen.kt               # Authentication UI
    ├── MainScreen.kt               # Dashboard and customer list
    ├── InvoiceScreen.kt            # Add/edit invoice UI
    ├── theme/
    │   └── Theme.kt                # Material Design theme
    └── viewmodel/                  # All ViewModels
        ├── AuthViewModel.kt
        ├── DashboardViewModel.kt
        ├── CustomerViewModel.kt
        └── InvoiceViewModel.kt
```

## Key Technologies

- **Jetpack Compose**: Modern declarative UI toolkit
- **Room**: Local database with reactive queries
- **Supabase**: Backend-as-a-Service for auth, database, and storage
- **Koin**: Lightweight dependency injection
- **Kotlin Coroutines**: Asynchronous programming
- **Material Design 3**: Modern design system
- **Coil**: Image loading library
- **WorkManager**: Background task scheduling

## Usage

1. **Authentication**: Sign up or sign in with email/password
2. **Add Invoice**: 
   - Select an invoice image from gallery
   - AI automatically extracts customer name, date, and phone
   - Review and edit extracted data
   - Save invoice
3. **View Dashboard**: See recent invoices, unpaid amounts, and overdue count
4. **Manage Customers**: View all customers in a dedicated tab

## Development Notes

- The app follows MVI architecture for predictable state management
- All UI components are built with Jetpack Compose
- Offline-first approach ensures data availability without internet
- Repository pattern provides single source of truth for data
- Koin provides clean dependency injection without annotation processing

## Future Enhancements

- Payment tracking and history
- Invoice PDF generation
- Advanced reporting and analytics
- Push notifications for overdue invoices
- Multi-language support
- Dark theme support
- Export functionality

## License

This project is built as a demonstration of modern Android development practices.