-----

### **Retail Assistant - Invoicing & Customer Management App**

Retail Assistant is an offline-first Android application designed for small business owners to efficiently manage their customers and invoices. It integrates AI-powered invoice scanning, robust data synchronization, and a companion web dashboard to provide a seamless cross-platform experience.

-----

### **Features**

#### **Android Application**

  * **Modern UI:** A contemporary user interface built with Jetpack Compose, featuring a custom design system with full support for both light and dark themes.
  * **Offline-First Architecture:** Users can read, create, and update invoices and customer data without an active internet connection. Data is cached locally using Room and synchronized automatically when connectivity is restored.
  * **AI-Powered Invoice Scanning:** Scan paper invoices using the device camera. Google's Gemini AI extracts key details such as customer information, dates, and amounts, minimizing manual data entry.
  * **Informative Dashboard:** Provides a concise overview of key business metrics, including total unpaid amounts and the number of overdue invoices.
  * **Invoice Management:** Track invoices with clear statuses (Unpaid, Paid, Overdue, Partially Paid). A comprehensive filtering and search system allows for easy navigation.
  * **Customer Management:** Maintain a detailed list of customers, view their complete transaction history, and monitor their total outstanding balance.
  * **Overdue Notifications:** A background service periodically checks for overdue invoices and issues notifications to prompt necessary action.
  * **Customizable Settings:** Tailor the application to your workflow by toggling features like AI data extraction, permanent image caching, dark mode, and notifications.
  * **WhatsApp Reminders:** Send pre-formatted, bilingual (English & Telugu) payment reminders to customers directly through WhatsApp.

#### **Web Companion**

  * **Web Dashboard:** A browser-based interface for viewing customer and invoice data from a desktop.
  * **Automated WhatsApp Reminders:** A companion Node.js server utilizes the Baileys library to send bulk payment reminders for all due invoices via WhatsApp Web.

-----

### **Tech Stack & Architecture**

#### **Backend**

  * **Supabase:** Serves as the primary backend, providing the database (PostgreSQL), authentication, and file storage services.
  * **PostgreSQL Functions (RPC):** Business logic is encapsulated in secure, server-side SQL functions to maintain data integrity and security.
  * **Row-Level Security (RLS):** Policies are enforced at the database level to ensure that users can only access their own data.

#### **Android App**

  * **Architecture:** Implements the Model-View-Intent (MVI) pattern for a predictable, unidirectional data flow.
  * **UI:** Developed entirely with Jetpack Compose for a declarative and modern UI.
  * **Asynchronous Operations:** Kotlin Coroutines and Flow are used extensively for managing background operations and reactive data streams.
  * **Dependency Injection:** Koin is used for managing dependencies throughout the application.
  * **Database:** Room provides a robust local cache for offline support.
  * **Networking:** Ktor Client is used for all network communications.
  * **AI:** Google Gemini facilitates intelligent data extraction from invoice images.
  * **Image Handling:** Coil ensures efficient image loading, with a custom keyer implemented for caching Supabase's signed URLs.
  * **Document Scanning:** The Google ML Kit Document Scanner provides a high-quality image capture experience.
  * **Background Tasks:** WorkManager is used for scheduling reliable background jobs, such as the notification service.

#### **Web App**

  * **Runtime:** A Node.js server built with the Express framework.
  * **Frontend:** Developed using HTML, CSS, and vanilla JavaScript.
  * **WhatsApp Integration:** The Baileys library enables interaction with the WhatsApp Web API.

-----

### **Getting Started**

#### **1. Supabase Setup**

The backend is powered entirely by Supabase.

1.  **Create a Project:** Navigate to [supabase.com](https://supabase.com) and create a new project.
2.  **Run the SQL Script:** In your Supabase dashboard, go to the SQL Editor. Copy the entire content of the `supabase_sql.sql` file from this repository and execute it. This script will:
      * Create the necessary tables (`customers`, `invoices`, `interaction_logs`).
      * Establish relationships and performance indexes.
      * Create the `invoice-scans` storage bucket.
      * Enable Row Level Security (RLS) and apply policies.
      * Define server-side RPC functions for core operations.
3.  **Get API Keys:** In your Supabase dashboard, navigate to **Project Settings \> API**. You will need the **Project URL** and the **anon public** key.

#### **2. Android App Setup**

1.  **Get a Gemini API Key:**

      * Visit the [Google AI for Developers](https://ai.google.dev/) website and generate an API key for the Gemini API.

2.  **Create `local.properties`:**

      * In the root directory of the repository, create a file named `local.properties`.
      * Add your secret keys to this file as follows:

    <!-- end list -->

    ```properties
    SUPABASE_URL="YOUR_SUPABASE_PROJECT_URL"
    SUPABASE_KEY="YOUR_SUPABASE_ANON_KEY"
    GEMINI_API_KEY="YOUR_GEMINI_API_KEY"
    ```

3.  **Run the App:**

      * Open the project in Android Studio.
      * Allow Gradle to sync the project dependencies.
      * Build and run the `app` module on an emulator or a physical device.

#### **3. Web App Setup**

1.  **Navigate to the web directory:**
    ```bash
    cd web
    ```
2.  **Create a `.env` file:**
      * Create a file named `.env` inside the `web` directory.
      * Add your Supabase credentials:
    <!-- end list -->
    ```env
    SUPABASE_URL="YOUR_SUPABASE_PROJECT_URL"
    SUPABASE_ANON_KEY="YOUR_SUPABASE_ANON_KEY"
    ```
3.  **Install Dependencies and Run:**
    ```bash
    npm install
    npm run dev
    ```
4.  Open `http://localhost:5173` in your browser. To use the WhatsApp features, click "Start / Show QR" and scan the generated QR code with your phone.

-----

### **Project Structure**

```
.
├── app/                  # Android Application source code
│   ├── schemas/          # Room database schema definitions
│   ├── src/main/
│   │   ├── java/com/retailassistant/
│   │   │   ├── core/           # Core utilities (MVI, ErrorHandler, etc.)
│   │   │   ├── data/           # Data layer (Repository, DB, Remote clients)
│   │   │   ├── di/             # Koin dependency injection modules
│   │   │   ├── features/       # All UI screens and ViewModels
│   │   │   ├── ui/             # Compose UI components, navigation, and theme
│   │   │   └── workers/        # Background tasks using WorkManager
│   │   └── res/            # Android resources (drawables, values, etc.)
│   └── build.gradle.kts  # Android app-specific build script
├── web/                  # Web companion app
│   ├── public/           # Static assets (HTML, CSS, JS)
│   ├── server.js         # Node.js/Express server logic
│   └── package.json      # Node.js dependencies
└── supabase_sql.sql      # The complete SQL script to set up the Supabase backend
```

-----

### **License**

This project is licensed under the MIT License. See the `LICENSE` file for details.
