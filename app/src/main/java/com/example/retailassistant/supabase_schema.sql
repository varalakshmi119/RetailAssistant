-- ==================================================================================
-- RETAIL ASSISTANT - V3 SUPABASE SCHEMA
-- ==================================================================================
-- This enhanced schema includes email fields and improved indexing.
-- Run this entire script in your Supabase SQL Editor.
-- ==================================================================================

-- Drop existing tables and types if they exist, in reverse order of dependency
DROP TABLE IF EXISTS public.interaction_logs CASCADE;
DROP TABLE IF EXISTS public.invoices CASCADE;
DROP TABLE IF EXISTS public.customers CASCADE;
DROP TYPE IF EXISTS public.interaction_type;
DROP TYPE IF EXISTS public.invoice_status;

-- =====================================================
-- 1. CREATE CUSTOM ENUM TYPES
-- =====================================================
CREATE TYPE public.invoice_status AS ENUM ('UNPAID', 'PAID', 'OVERDUE', 'PARTIALLY_PAID');
CREATE TYPE public.interaction_type AS ENUM ('CALL', 'NOTE', 'PAYMENT');

-- =====================================================
-- 2. CREATE CUSTOMERS TABLE
-- =====================================================
CREATE TABLE public.customers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    email TEXT,
    "userId" UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);
-- Add indexes for performance
CREATE INDEX idx_customers_user_id ON public.customers("userId");
COMMENT ON TABLE public.customers IS 'Stores customer information.';

-- =====================================================
-- 3. CREATE INVOICES TABLE
-- =====================================================
CREATE TABLE public.invoices (
    id TEXT PRIMARY KEY,
    "customerId" TEXT NOT NULL REFERENCES public.customers(id) ON DELETE CASCADE,
    "totalAmount" DECIMAL(10,2) NOT NULL,
    "amountPaid" DECIMAL(10,2) DEFAULT 0.0 NOT NULL,
    "issueDate" TEXT NOT NULL, -- Format: YYYY-MM-DD
    "dueDate" TEXT NOT NULL,   -- Format: YYYY-MM-DD
    status public.invoice_status DEFAULT 'UNPAID' NOT NULL,
    "originalScanUrl" TEXT NOT NULL,
    "createdAt" BIGINT NOT NULL,
    "userId" UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);
-- Add indexes for performance
CREATE INDEX idx_invoices_user_id ON public.invoices("userId");
CREATE INDEX idx_invoices_customer_id ON public.invoices("customerId");
CREATE INDEX idx_invoices_status ON public.invoices(status);
CREATE INDEX idx_invoices_due_date ON public.invoices("dueDate");
COMMENT ON TABLE public.invoices IS 'Stores invoice details for each customer.';

-- =====================================================
-- 4. CREATE INTERACTION LOGS TABLE
-- =====================================================
CREATE TABLE public.interaction_logs (
    id TEXT PRIMARY KEY,
    "invoiceId" TEXT NOT NULL REFERENCES public.invoices(id) ON DELETE CASCADE,
    "userId" UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    type public.interaction_type NOT NULL,
    notes TEXT,
    value DECIMAL(10,2), -- e.g., for payment amount
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);
-- Add indexes for performance
CREATE INDEX idx_interaction_logs_invoice_id ON public.interaction_logs("invoiceId");
CREATE INDEX idx_interaction_logs_user_id ON public.interaction_logs("userId");
COMMENT ON TABLE public.interaction_logs IS 'Logs all interactions with an invoice, like payments or notes.';

-- =====================================================
-- 5. ENABLE ROW LEVEL SECURITY
-- =====================================================
ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.interaction_logs ENABLE ROW LEVEL SECURITY;

-- =====================================================
-- 6. CREATE RLS POLICIES
-- =====================================================
-- Customers Policies
CREATE POLICY "Users can manage their own customers" ON public.customers
FOR ALL USING (auth.uid() = "userId")
WITH CHECK (auth.uid() = "userId");

-- Invoices Policies
CREATE POLICY "Users can manage their own invoices" ON public.invoices
FOR ALL USING (auth.uid() = "userId")
WITH CHECK (auth.uid() = "userId");

-- Interaction Logs Policies
CREATE POLICY "Users can manage their own interaction logs" ON public.interaction_logs
FOR ALL USING (auth.uid() = "userId")
WITH CHECK (auth.uid() = "userId");

-- =====================================================
-- 7. CREATE STORAGE BUCKET & POLICIES
-- =====================================================
-- Create Bucket
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('invoice-images', 'invoice-images', false, 5242880, ARRAY['image/jpeg', 'image/png'])
ON CONFLICT (id) DO NOTHING;

-- Storage Policies
CREATE POLICY "Allow authenticated users to upload images" ON storage.objects
FOR INSERT TO authenticated WITH CHECK (bucket_id = 'invoice-images');

CREATE POLICY "Allow users to read their own images" ON storage.objects
FOR SELECT TO authenticated USING (
    bucket_id = 'invoice-images' AND
    auth.uid()::text = (storage.foldername(name))[1] -- Assumes folder structure is {user_id}/{file_name}
);

CREATE POLICY "Allow users to delete their own images" ON storage.objects
FOR DELETE TO authenticated USING (
    bucket_id = 'invoice-images' AND
    auth.uid()::text = (storage.foldername(name))[1]
);

-- =====================================================
-- 8. AUTOMATIC TIMESTAMP FUNCTION & TRIGGERS
-- =====================================================
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
   NEW.updated_at = NOW();
   RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_customers_updated_at
BEFORE UPDATE ON public.customers
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_invoices_updated_at
BEFORE UPDATE ON public.invoices
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- =====================================================
-- SETUP COMPLETE!
-- =====================================================
