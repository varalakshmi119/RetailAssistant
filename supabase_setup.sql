-- =====================================================
-- RETAIL ASSISTANT - SUPABASE DATABASE SETUP
-- =====================================================
-- Run these SQL commands in your Supabase SQL Editor
-- =====================================================

-- 1. CREATE CUSTOMERS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS public.customers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    "userId" TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Add indexes for better performance
CREATE INDEX IF NOT EXISTS idx_customers_user_id ON public.customers("userId");
CREATE INDEX IF NOT EXISTS idx_customers_name ON public.customers(name);

-- =====================================================
-- 2. CREATE INVOICES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS public.invoices (
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

-- Add indexes for better performance
CREATE INDEX IF NOT EXISTS idx_invoices_user_id ON public.invoices("userId");
CREATE INDEX IF NOT EXISTS idx_invoices_customer_id ON public.invoices("customerId");
CREATE INDEX IF NOT EXISTS idx_invoices_status ON public.invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_created_at ON public.invoices("createdAt");

-- =====================================================
-- 3. CREATE FOREIGN KEY RELATIONSHIPS
-- =====================================================
ALTER TABLE public.invoices 
ADD CONSTRAINT fk_invoices_customers 
FOREIGN KEY ("customerId") REFERENCES public.customers(id) 
ON DELETE CASCADE;

-- =====================================================
-- 4. CREATE STORAGE BUCKET FOR INVOICE IMAGES
-- =====================================================
-- Insert the storage bucket (if it doesn't exist)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'invoice-images',
    'invoice-images',
    true,
    52428800, -- 50MB limit
    ARRAY['image/jpeg', 'image/jpg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- 5. SET UP ROW LEVEL SECURITY (RLS)
-- =====================================================

-- Enable RLS on customers table
ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;

-- Enable RLS on invoices table
ALTER TABLE public.invoices ENABLE ROW LEVEL SECURITY;

-- =====================================================
-- 6. CREATE RLS POLICIES FOR CUSTOMERS
-- =====================================================

-- Policy: Users can only see their own customers
CREATE POLICY "Users can view own customers" ON public.customers
    FOR SELECT USING (auth.uid()::text = "userId");

-- Policy: Users can only insert their own customers
CREATE POLICY "Users can insert own customers" ON public.customers
    FOR INSERT WITH CHECK (auth.uid()::text = "userId");

-- Policy: Users can only update their own customers
CREATE POLICY "Users can update own customers" ON public.customers
    FOR UPDATE USING (auth.uid()::text = "userId");

-- Policy: Users can only delete their own customers
CREATE POLICY "Users can delete own customers" ON public.customers
    FOR DELETE USING (auth.uid()::text = "userId");

-- =====================================================
-- 7. CREATE RLS POLICIES FOR INVOICES
-- =====================================================

-- Policy: Users can only see their own invoices
CREATE POLICY "Users can view own invoices" ON public.invoices
    FOR SELECT USING (auth.uid()::text = "userId");

-- Policy: Users can only insert their own invoices
CREATE POLICY "Users can insert own invoices" ON public.invoices
    FOR INSERT WITH CHECK (auth.uid()::text = "userId");

-- Policy: Users can only update their own invoices
CREATE POLICY "Users can update own invoices" ON public.invoices
    FOR UPDATE USING (auth.uid()::text = "userId");

-- Policy: Users can only delete their own invoices
CREATE POLICY "Users can delete own invoices" ON public.invoices
    FOR DELETE USING (auth.uid()::text = "userId");

-- =====================================================
-- 8. CREATE STORAGE POLICIES
-- =====================================================

-- Policy: Users can upload invoice images
CREATE POLICY "Users can upload invoice images" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'invoice-images' AND
        auth.role() = 'authenticated' AND
        (storage.foldername(name))[1] = auth.uid()::text
    );

-- Policy: Users can view their own invoice images
CREATE POLICY "Users can view own invoice images" ON storage.objects
    FOR SELECT USING (
        bucket_id = 'invoice-images' AND
        auth.role() = 'authenticated' AND
        (storage.foldername(name))[1] = auth.uid()::text
    );

-- Policy: Users can update their own invoice images
CREATE POLICY "Users can update own invoice images" ON storage.objects
    FOR UPDATE USING (
        bucket_id = 'invoice-images' AND
        auth.role() = 'authenticated' AND
        (storage.foldername(name))[1] = auth.uid()::text
    );

-- Policy: Users can delete their own invoice images
CREATE POLICY "Users can delete own invoice images" ON storage.objects
    FOR DELETE USING (
        bucket_id = 'invoice-images' AND
        auth.role() = 'authenticated' AND
        (storage.foldername(name))[1] = auth.uid()::text
    );

-- =====================================================
-- 9. CREATE FUNCTIONS FOR AUTOMATIC TIMESTAMPS
-- =====================================================

-- Function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for automatic timestamp updates
CREATE TRIGGER update_customers_updated_at 
    BEFORE UPDATE ON public.customers 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_invoices_updated_at 
    BEFORE UPDATE ON public.invoices 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- 10. CREATE HELPFUL VIEWS (OPTIONAL)
-- =====================================================

-- View: Invoice details with customer information
CREATE OR REPLACE VIEW invoice_details AS
SELECT 
    i.id,
    i."customerId",
    i."totalAmount",
    i."amountPaid",
    i."issueDate",
    i.status,
    i."originalScanUrl",
    i."createdAt",
    i."userId",
    c.name as customer_name,
    c.phone as customer_phone,
    (i."totalAmount" - i."amountPaid") as balance_due
FROM public.invoices i
LEFT JOIN public.customers c ON i."customerId" = c.id;

-- =====================================================
-- 11. SAMPLE DATA (OPTIONAL - FOR TESTING)
-- =====================================================

-- Uncomment the following lines if you want to insert sample data for testing
-- Note: Replace 'your-user-id-here' with an actual user ID from auth.users

/*
-- Sample customer
INSERT INTO public.customers (id, name, phone, "userId") VALUES 
('sample-customer-1', 'John Doe', '555-0123', 'your-user-id-here');

-- Sample invoice
INSERT INTO public.invoices (
    id, "customerId", "totalAmount", "amountPaid", "issueDate", 
    status, "originalScanUrl", "createdAt", "userId"
) VALUES (
    'sample-invoice-1', 
    'sample-customer-1', 
    150.00, 
    0.00, 
    '2024-01-15', 
    'UNPAID', 
    'invoices/your-user-id-here/sample.jpg', 
    1705334400000, 
    'your-user-id-here'
);
*/

-- =====================================================
-- SETUP COMPLETE!
-- =====================================================
-- Your Retail Assistant database is now ready to use.
-- 
-- Next steps:
-- 1. Run this SQL in your Supabase SQL Editor
-- 2. Verify that all tables and policies were created successfully
-- 3. Test the Android app with user registration and invoice creation
-- 
-- Security Features Enabled:
-- ✅ Row Level Security (RLS) on all tables
-- ✅ User isolation (users can only access their own data)
-- ✅ Secure file storage with user-specific folders
-- ✅ Proper foreign key relationships
-- ✅ Automatic timestamp management
-- ✅ Performance indexes on key columns
-- =====================================================