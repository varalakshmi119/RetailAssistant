-- =====================================================
-- RETAIL ASSISTANT - SIMPLE SUPABASE SETUP
-- =====================================================
-- Run this entire script in your Supabase SQL Editor
-- =====================================================

-- =====================================================
-- 1. CREATE CUSTOMERS TABLE
-- =====================================================
CREATE TABLE public.customers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    "userId" TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Add indexes for performance
CREATE INDEX idx_customers_user_id ON public.customers("userId");
CREATE INDEX idx_customers_name ON public.customers(name);

-- =====================================================
-- 2. CREATE INVOICES TABLE
-- =====================================================
CREATE TABLE public.invoices (
    id TEXT PRIMARY KEY,
    "customerId" TEXT NOT NULL,
    "totalAmount" DECIMAL(10,2) NOT NULL,
    "amountPaid" DECIMAL(10,2) DEFAULT 0.0,
    "issueDate" TEXT NOT NULL,
    status TEXT DEFAULT 'UNPAID' CHECK (status IN ('UNPAID', 'PAID', 'OVERDUE')),
    "originalScanUrl" TEXT NOT NULL,
    "createdAt" BIGINT NOT NULL,
    "userId" TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Add indexes for performance
CREATE INDEX idx_invoices_user_id ON public.invoices("userId");
CREATE INDEX idx_invoices_customer_id ON public.invoices("customerId");
CREATE INDEX idx_invoices_status ON public.invoices(status);
CREATE INDEX idx_invoices_created_at ON public.invoices("createdAt");

-- =====================================================
-- 3. CREATE FOREIGN KEY RELATIONSHIP
-- =====================================================
ALTER TABLE public.invoices 
ADD CONSTRAINT fk_invoices_customers 
FOREIGN KEY ("customerId") REFERENCES public.customers(id) 
ON DELETE CASCADE;

-- =====================================================
-- 4. ENABLE ROW LEVEL SECURITY
-- =====================================================
ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoices ENABLE ROW LEVEL SECURITY;

-- =====================================================
-- 5. CREATE RLS POLICIES FOR CUSTOMERS
-- =====================================================

CREATE POLICY "Users can view own customers" ON public.customers
    FOR SELECT USING (auth.uid()::text = "userId");

CREATE POLICY "Users can insert own customers" ON public.customers
    FOR INSERT WITH CHECK (auth.uid()::text = "userId");

CREATE POLICY "Users can update own customers" ON public.customers
    FOR UPDATE USING (auth.uid()::text = "userId");

CREATE POLICY "Users can delete own customers" ON public.customers
    FOR DELETE USING (auth.uid()::text = "userId");

-- =====================================================
-- 6. CREATE RLS POLICIES FOR INVOICES
-- =====================================================

CREATE POLICY "Users can view own invoices" ON public.invoices
    FOR SELECT USING (auth.uid()::text = "userId");

CREATE POLICY "Users can insert own invoices" ON public.invoices
    FOR INSERT WITH CHECK (auth.uid()::text = "userId");

CREATE POLICY "Users can update own invoices" ON public.invoices
    FOR UPDATE USING (auth.uid()::text = "userId");

CREATE POLICY "Users can delete own invoices" ON public.invoices
    FOR DELETE USING (auth.uid()::text = "userId");

-- =====================================================
-- 7. CREATE STORAGE BUCKET
-- =====================================================
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'invoice-images',
    'invoice-images',
    true,
    52428800,
    ARRAY['image/jpeg', 'image/jpg', 'image/png', 'image/webp']
);

-- =====================================================
-- 8. CREATE STORAGE POLICIES
-- =====================================================

CREATE POLICY "Allow authenticated uploads" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'invoice-images' AND
        auth.role() = 'authenticated'
    );

CREATE POLICY "Allow authenticated reads" ON storage.objects
    FOR SELECT USING (
        bucket_id = 'invoice-images' AND
        auth.role() = 'authenticated'
    );

CREATE POLICY "Allow authenticated updates" ON storage.objects
    FOR UPDATE USING (
        bucket_id = 'invoice-images' AND
        auth.role() = 'authenticated'
    );

CREATE POLICY "Allow authenticated deletes" ON storage.objects
    FOR DELETE USING (
        bucket_id = 'invoice-images' AND
        auth.role() = 'authenticated'
    );

-- =====================================================
-- 9. CREATE AUTOMATIC TIMESTAMP FUNCTION
-- =====================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- =====================================================
-- 10. CREATE TRIGGERS FOR AUTOMATIC TIMESTAMPS
-- =====================================================
CREATE TRIGGER update_customers_updated_at 
    BEFORE UPDATE ON public.customers 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_invoices_updated_at 
    BEFORE UPDATE ON public.invoices 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- SETUP COMPLETE!
-- =====================================================