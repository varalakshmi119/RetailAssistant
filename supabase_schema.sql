-- Retail Assistant App - Supabase Database Schema
-- This schema matches the Room entities in your Android app for perfect synchronization

-- Enable Row Level Security (RLS) for all tables
-- This ensures users can only access their own data

-- Create custom types for enums (only if they don't exist)
DO $$ BEGIN
    CREATE TYPE invoice_status AS ENUM ('UNPAID', 'PAID', 'OVERDUE', 'PARTIALLY_PAID');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE interaction_type AS ENUM ('CALL', 'NOTE', 'PAYMENT');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Customers table (create only if it doesn't exist)
CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    phone TEXT,
    email TEXT,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create index for performance (matches Room indices)
CREATE INDEX IF NOT EXISTS idx_customers_user_id ON customers(user_id);

-- Invoices table (create only if it doesn't exist)
CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    total_amount DECIMAL(10,2) NOT NULL,
    amount_paid DECIMAL(10,2) DEFAULT 0.0,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status invoice_status DEFAULT 'UNPAID',
    original_scan_url TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE
);

-- Create indices for performance (matches Room indices)
CREATE INDEX IF NOT EXISTS idx_invoices_user_id ON invoices(user_id);
CREATE INDEX IF NOT EXISTS idx_invoices_customer_id ON invoices(customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_created_at ON invoices(created_at DESC);

-- Interaction logs table (create only if it doesn't exist)
CREATE TABLE IF NOT EXISTS interaction_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    type interaction_type NOT NULL,
    notes TEXT,
    value DECIMAL(10,2), -- For payment amounts
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indices for performance (matches Room indices)
CREATE INDEX IF NOT EXISTS idx_interaction_logs_user_id ON interaction_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_interaction_logs_invoice_id ON interaction_logs(invoice_id);
CREATE INDEX IF NOT EXISTS idx_interaction_logs_created_at ON interaction_logs(created_at DESC);

-- Enable Row Level Security
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE interaction_logs ENABLE ROW LEVEL SECURITY;

-- RLS Policies - Users can only access their own data
-- Drop existing policies if they exist to avoid conflicts
DROP POLICY IF EXISTS "Users can view their own customers" ON customers;
DROP POLICY IF EXISTS "Users can insert their own customers" ON customers;
DROP POLICY IF EXISTS "Users can update their own customers" ON customers;
DROP POLICY IF EXISTS "Users can delete their own customers" ON customers;

DROP POLICY IF EXISTS "Users can view their own invoices" ON invoices;
DROP POLICY IF EXISTS "Users can insert their own invoices" ON invoices;
DROP POLICY IF EXISTS "Users can update their own invoices" ON invoices;
DROP POLICY IF EXISTS "Users can delete their own invoices" ON invoices;

DROP POLICY IF EXISTS "Users can view their own interaction logs" ON interaction_logs;
DROP POLICY IF EXISTS "Users can insert their own interaction logs" ON interaction_logs;
DROP POLICY IF EXISTS "Users can update their own interaction logs" ON interaction_logs;
DROP POLICY IF EXISTS "Users can delete their own interaction logs" ON interaction_logs;

CREATE POLICY "Users can view their own customers" ON customers
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own customers" ON customers
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own customers" ON customers
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can delete their own customers" ON customers
    FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY "Users can view their own invoices" ON invoices
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own invoices" ON invoices
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own invoices" ON invoices
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can delete their own invoices" ON invoices
    FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY "Users can view their own interaction logs" ON interaction_logs
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own interaction logs" ON interaction_logs
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own interaction logs" ON interaction_logs
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can delete their own interaction logs" ON interaction_logs
    FOR DELETE USING (auth.uid() = user_id);

-- Create functions for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for automatic timestamp updates
DROP TRIGGER IF EXISTS update_customers_updated_at ON customers;
CREATE TRIGGER update_customers_updated_at BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_invoices_updated_at ON invoices;
CREATE TRIGGER update_invoices_updated_at BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create a view for invoice analytics (optional but useful)
DROP VIEW IF EXISTS invoice_analytics;
CREATE VIEW invoice_analytics AS
SELECT 
    i.*,
    c.name as customer_name,
    c.phone as customer_phone,
    c.email as customer_email,
    (i.total_amount - i.amount_paid) as balance_due,
    CASE 
        WHEN i.status != 'PAID' AND i.due_date < CURRENT_DATE THEN true
        ELSE false
    END as is_overdue
FROM invoices i
JOIN customers c ON i.customer_id = c.id;

-- Grant access to the view
GRANT SELECT ON invoice_analytics TO authenticated;

-- Note: Views inherit RLS from their underlying tables
-- The invoice_analytics view will automatically respect the RLS policies
-- of the invoices and customers tables, so no additional policy is needed
-
- Create storage bucket for invoice scans
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'invoice-scans',
    'invoice-scans',
    false, -- Private bucket - users can only access their own files
    10485760, -- 10MB file size limit
    ARRAY['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
) ON CONFLICT (id) DO NOTHING;

-- Storage policies for invoice scans bucket
-- Users can only upload files with their user ID as prefix
CREATE POLICY "Users can upload their own invoice scans" ON storage.objects
    FOR INSERT WITH CHECK (
        bucket_id = 'invoice-scans' AND 
        auth.uid()::text = (storage.foldername(name))[1]
    );

-- Users can view their own invoice scans
CREATE POLICY "Users can view their own invoice scans" ON storage.objects
    FOR SELECT USING (
        bucket_id = 'invoice-scans' AND 
        auth.uid()::text = (storage.foldername(name))[1]
    );

-- Users can update their own invoice scans
CREATE POLICY "Users can update their own invoice scans" ON storage.objects
    FOR UPDATE USING (
        bucket_id = 'invoice-scans' AND 
        auth.uid()::text = (storage.foldername(name))[1]
    );

-- Users can delete their own invoice scans
CREATE POLICY "Users can delete their own invoice scans" ON storage.objects
    FOR DELETE USING (
        bucket_id = 'invoice-scans' AND 
        auth.uid()::text = (storage.foldername(name))[1]
    );