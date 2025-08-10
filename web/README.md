# RetailAssistant Web (Node + HTML/CSS/JS)

A minimal companion web app for desktop users to log in and view their customers and invoices using the same Supabase backend.

## Quick start

1. Create `.env` from example and set your Supabase values:
   ```bash
   cp .env.example .env
   # edit .env with SUPABASE_URL and SUPABASE_ANON_KEY
   ```
2. Install deps and run the server:
   ```bash
   npm install
   npm run dev
   ```
3. Open `http://localhost:5173` in your browser.

## Features
- Email/password login with Supabase Auth
- Lists `customers` and `invoices` belonging to the logged-in user
- Sign out

RLS is enforced by Supabase using the user JWT from the browser.
