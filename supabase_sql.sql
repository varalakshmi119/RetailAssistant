-- =================================================================
--      RETAIL ASSISTANT - COMPLETE SUPABASE SETUP SCRIPT (v4.1)
-- =================================================================
-- This script configures the entire database schema, storage,
-- security policies, and server-side functions. It is designed to
-- be run once from top to bottom.

-- -----------------------------------------------------------------
-- (Optional) DEVELOPMENT RESET SCRIPT ⚠️
-- -----------------------------------------------------------------
-- Uncomment the following lines ONLY if you want to completely
-- reset your database for a clean start.
-- WARNING: THIS WILL DELETE ALL DATA IN THESE TABLES.

DROP FUNCTION IF EXISTS public.delete_customer(uuid) CASCADE;
DROP FUNCTION IF EXISTS public.delete_invoice(uuid) CASCADE;
DROP FUNCTION IF EXISTS public.postpone_due_date(uuid, date, text) CASCADE;
DROP FUNCTION IF EXISTS public.add_note(uuid, text) CASCADE;
DROP FUNCTION IF EXISTS public.add_payment(uuid, numeric, text) CASCADE;
DROP FUNCTION IF EXISTS public.create_invoice_with_customer(uuid, text, text, text, numeric, date, date, text) CASCADE;

DROP TABLE IF EXISTS public.interaction_logs CASCADE;
DROP TABLE IF EXISTS public.invoices CASCADE;
DROP TABLE IF EXISTS public.customers CASCADE;

DROP TYPE IF EXISTS public.interaction_type CASCADE;
DROP TYPE IF EXISTS public.invoice_status CASCADE;


-- =================================================================
-- 1. CUSTOM TYPES (ENUMS) 🏷️
-- =================================================================
-- These must be created before the tables that use them.

CREATE TYPE public.invoice_status AS ENUM (
    'UNPAID',
    'PAID',
    'OVERDUE',
    'PARTIALLY_PAID'
);

CREATE TYPE public.interaction_type AS ENUM (
    'NOTE',
    'PAYMENT',
    'DUE_DATE_CHANGED'
);


-- =================================================================
-- 2. DATABASE TABLES 🏗️
-- =================================================================

-- ---------------------------------
-- Table: customers
-- ---------------------------------
CREATE TABLE public.customers (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name text NOT NULL,
    phone text,
    email text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    -- Prevents a user from having two customers with the exact same name.
    CONSTRAINT customers_user_id_name_key UNIQUE (user_id, name)
);
COMMENT ON TABLE public.customers IS 'Stores customer information for each user.';

-- ---------------------------------
-- Table: invoices
-- ---------------------------------
CREATE TABLE public.invoices (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    customer_id uuid NOT NULL REFERENCES public.customers(id) ON DELETE CASCADE,
    total_amount numeric NOT NULL CHECK (total_amount > 0),
    amount_paid numeric NOT NULL DEFAULT 0.0 CHECK (amount_paid >= 0),
    issue_date date NOT NULL,
    due_date date NOT NULL,
    status public.invoice_status NOT NULL DEFAULT 'UNPAID',
    original_scan_url text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE public.invoices IS 'Stores individual invoice records linked to customers.';

-- ---------------------------------
-- Table: interaction_logs
-- ---------------------------------
CREATE TABLE public.interaction_logs (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    invoice_id uuid NOT NULL REFERENCES public.invoices(id) ON DELETE CASCADE,
    type public.interaction_type NOT NULL,
    notes text,
    value numeric, -- Used for payment amounts, etc.
    created_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE public.interaction_logs IS 'Tracks all activities related to an invoice, like payments and notes.';


-- =================================================================
-- 3. PERFORMANCE INDEXES ⚡
-- =================================================================

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_customers_user_id ON public.customers(user_id);
CREATE INDEX IF NOT EXISTS idx_customers_user_name ON public.customers(user_id, name);

CREATE INDEX IF NOT EXISTS idx_invoices_user_id ON public.invoices(user_id);
CREATE INDEX IF NOT EXISTS idx_invoices_customer_id ON public.invoices(customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_user_customer ON public.invoices(user_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON public.invoices(status) WHERE status <> 'PAID';
CREATE INDEX IF NOT EXISTS idx_invoices_due_date ON public.invoices(due_date) WHERE status <> 'PAID';
CREATE INDEX IF NOT EXISTS idx_invoices_original_scan_url ON public.invoices(original_scan_url); -- Speeds up storage RLS

CREATE INDEX IF NOT EXISTS idx_interaction_logs_user_id ON public.interaction_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_interaction_logs_invoice_id ON public.interaction_logs(invoice_id);
CREATE INDEX IF NOT EXISTS idx_interaction_logs_invoice_created ON public.interaction_logs(invoice_id, created_at DESC);


-- =================================================================
-- 4. STORAGE BUCKET 📦
-- =================================================================
-- Create a private bucket for storing scanned invoice images.
INSERT INTO storage.buckets (id, name, public)
VALUES ('invoice-scans', 'invoice-scans', false)
ON CONFLICT (id) DO NOTHING;


-- =================================================================
-- 5. ROW LEVEL SECURITY (RLS) POLICIES 🛡️
-- =================================================================
-- This is a critical part for multi-tenant data security.

ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.interaction_logs ENABLE ROW LEVEL SECURITY;

-- Policies for 'customers' table
CREATE POLICY "Users can manage their own customers" ON public.customers
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- Policies for 'invoices' table
CREATE POLICY "Users can manage their own invoices" ON public.invoices
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- Policies for 'interaction_logs' table
CREATE POLICY "Users can view logs for their own invoices" ON public.interaction_logs
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can create logs for their own invoices" ON public.interaction_logs
    FOR INSERT WITH CHECK (auth.uid() = user_id);


-- =================================================================
-- 6. STORAGE SECURITY POLICIES 🔐
-- =================================================================

-- Drop existing policies for this bucket to avoid conflicts on re-run
DROP POLICY IF EXISTS "Users can upload invoice scans to their own folder" ON storage.objects;
DROP POLICY IF EXISTS "Users can view their own invoice scans" ON storage.objects;
DROP POLICY IF EXISTS "Users can delete their own invoice scans" ON storage.objects;

-- Create fresh policies
CREATE POLICY "Users can upload invoice scans to their own folder"
    ON storage.objects FOR INSERT
    WITH CHECK ( bucket_id = 'invoice-scans' AND (storage.foldername(name))[1] = auth.uid()::text );

CREATE POLICY "Users can view their own invoice scans"
    ON storage.objects FOR SELECT
    USING ( bucket_id = 'invoice-scans' AND auth.uid() = (SELECT user_id FROM public.invoices WHERE original_scan_url = name) );

CREATE POLICY "Users can delete their own invoice scans"
    ON storage.objects FOR DELETE
    USING ( bucket_id = 'invoice-scans' AND auth.uid() = (SELECT user_id FROM public.invoices WHERE original_scan_url = name) );


-- =================================================================
-- 7. AUTOMATIC TIMESTAMP TRIGGERS ⚙️
-- =================================================================

CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for 'customers' table
CREATE TRIGGER update_customers_updated_at
    BEFORE UPDATE ON public.customers
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Trigger for 'invoices' table
CREATE TRIGGER update_invoices_updated_at
    BEFORE UPDATE ON public.invoices
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


-- =================================================================
-- 8. RPC (SERVER-SIDE FUNCTIONS) 🚀
-- =================================================================
-- Drop existing functions first to ensure a clean setup.
DROP FUNCTION IF EXISTS public.create_invoice_with_customer(uuid, text, text, text, numeric, date, date, text);
DROP FUNCTION IF EXISTS public.add_payment(uuid, numeric, text);
DROP FUNCTION IF EXISTS public.add_note(uuid, text);
DROP FUNCTION IF EXISTS public.postpone_due_date(uuid, date, text);
DROP FUNCTION IF EXISTS public.delete_invoice(uuid);
DROP FUNCTION IF EXISTS public.delete_customer(uuid);

-- ---------------------------------
-- Function: create_invoice_with_customer
-- ---------------------------------
CREATE FUNCTION public.create_invoice_with_customer(
    p_customer_id uuid,
    p_customer_name text,
    p_customer_phone text,
    p_customer_email text,
    p_total_amount numeric,
    p_issue_date date,
    p_due_date date,
    p_image_path text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_customer_id uuid;
    v_invoice_id uuid;
    v_user_id uuid := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
    IF trim(p_customer_name) IS NULL OR trim(p_customer_name) = '' THEN RAISE EXCEPTION 'Customer name is required'; END IF;
    IF p_total_amount <= 0 THEN RAISE EXCEPTION 'Total amount must be greater than zero'; END IF;
    IF trim(p_image_path) IS NULL OR trim(p_image_path) = '' THEN RAISE EXCEPTION 'Image path is required'; END IF;

    IF p_customer_id IS NULL THEN
        -- Create new customer
        INSERT INTO customers (user_id, name, phone, email)
        VALUES (v_user_id, trim(p_customer_name), NULLIF(trim(p_customer_phone), ''), NULLIF(trim(p_customer_email), ''))
        RETURNING id INTO v_customer_id;
    ELSE
        -- Validate and update existing customer
        SELECT id INTO v_customer_id FROM customers WHERE id = p_customer_id AND user_id = v_user_id;
        IF v_customer_id IS NULL THEN RAISE EXCEPTION 'Customer not found or access denied'; END IF;
        
        UPDATE customers SET 
            name = trim(p_customer_name),
            phone = NULLIF(trim(p_customer_phone), ''),
            email = NULLIF(trim(p_customer_email), '')
        WHERE id = v_customer_id;
    END IF;

    -- Create the invoice
    INSERT INTO invoices (user_id, customer_id, total_amount, issue_date, due_date, original_scan_url)
    VALUES (v_user_id, v_customer_id, p_total_amount, p_issue_date, p_due_date, p_image_path)
    RETURNING id INTO v_invoice_id;

    RETURN v_invoice_id;
END;
$$;

-- ---------------------------------
-- Function: add_payment
-- ---------------------------------
CREATE FUNCTION public.add_payment(p_invoice_id uuid, p_amount numeric, p_note text DEFAULT NULL)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_invoice invoices;
    v_new_amount_paid numeric;
    v_new_status invoice_status;
    v_user_id uuid := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
    IF p_amount <= 0 THEN RAISE EXCEPTION 'Payment amount must be greater than zero'; END IF;

    SELECT * INTO v_invoice FROM invoices WHERE id = p_invoice_id AND user_id = v_user_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'Invoice not found or access denied'; END IF;

    v_new_amount_paid := v_invoice.amount_paid + p_amount;
    v_new_status := CASE 
        WHEN v_new_amount_paid >= v_invoice.total_amount THEN 'PAID'::invoice_status
        ELSE 'PARTIALLY_PAID'::invoice_status 
    END;

    UPDATE invoices SET amount_paid = v_new_amount_paid, status = v_new_status WHERE id = p_invoice_id;
    INSERT INTO interaction_logs (user_id, invoice_id, type, notes, value) VALUES (v_user_id, p_invoice_id, 'PAYMENT', p_note, p_amount);
END;
$$;

-- ---------------------------------
-- Function: add_note
-- ---------------------------------
CREATE FUNCTION public.add_note(p_invoice_id uuid, p_note text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE 
    v_user_id uuid := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
    IF trim(p_note) IS NULL OR trim(p_note) = '' THEN RAISE EXCEPTION 'Note cannot be empty'; END IF;

    IF NOT EXISTS (SELECT 1 FROM invoices WHERE id = p_invoice_id AND user_id = v_user_id) THEN
        RAISE EXCEPTION 'Invoice not found or access denied';
    END IF;

    INSERT INTO interaction_logs (user_id, invoice_id, type, notes) VALUES (v_user_id, p_invoice_id, 'NOTE', trim(p_note));
END;
$$;

-- ---------------------------------
-- Function: postpone_due_date
-- ---------------------------------
CREATE FUNCTION public.postpone_due_date(p_invoice_id uuid, p_new_due_date date, p_reason text DEFAULT NULL)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE 
    v_user_id uuid := auth.uid();
    v_current_due_date date;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;

    SELECT due_date INTO v_current_due_date FROM invoices WHERE id = p_invoice_id AND user_id = v_user_id;
    IF v_current_due_date IS NULL THEN RAISE EXCEPTION 'Invoice not found or access denied'; END IF;
    IF p_new_due_date <= v_current_due_date THEN RAISE EXCEPTION 'New due date must be later than the current one'; END IF;

    UPDATE invoices SET due_date = p_new_due_date WHERE id = p_invoice_id;
    INSERT INTO interaction_logs (user_id, invoice_id, type, notes)
    VALUES (v_user_id, p_invoice_id, 'DUE_DATE_CHANGED', format('Due date changed from %s to %s. Reason: %s', v_current_due_date, p_new_due_date, COALESCE(trim(p_reason), 'Not provided')));
END;
$$;

-- ---------------------------------
-- Function: delete_invoice
-- ---------------------------------
CREATE FUNCTION public.delete_invoice(p_invoice_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_scan_path text;
    v_user_id uuid := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;

    SELECT original_scan_url INTO v_scan_path FROM invoices WHERE id = p_invoice_id AND user_id = v_user_id;
    IF v_scan_path IS NULL THEN RAISE EXCEPTION 'Invoice not found or access denied'; END IF;

    DELETE FROM invoices WHERE id = p_invoice_id AND user_id = v_user_id;

    BEGIN
        PERFORM storage.delete_object('invoice-scans', v_scan_path);
    EXCEPTION WHEN OTHERS THEN
        RAISE WARNING 'Failed to delete invoice scan file from storage: %', v_scan_path;
    END;
END;
$$;

-- ---------------------------------
-- Function: delete_customer
-- ---------------------------------
CREATE FUNCTION public.delete_customer(p_customer_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_scan_paths text[];
    v_user_id uuid := auth.uid();
    v_path text;
    v_deleted_count int;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;

    SELECT array_agg(original_scan_url) INTO v_scan_paths
    FROM invoices WHERE customer_id = p_customer_id AND user_id = v_user_id AND original_scan_url IS NOT NULL;

    DELETE FROM customers WHERE id = p_customer_id AND user_id = v_user_id;
    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
    IF v_deleted_count = 0 THEN RAISE EXCEPTION 'Customer not found or access denied'; END IF;

    IF v_scan_paths IS NOT NULL THEN
        FOREACH v_path IN ARRAY v_scan_paths LOOP
            BEGIN
                PERFORM storage.delete_object('invoice-scans', v_path);
            EXCEPTION WHEN OTHERS THEN
                RAISE WARNING 'Failed to delete an invoice scan file from storage: %', v_path;
            END;
        END LOOP;
    END IF;
END;
$$;


-- =================================================================
-- 9. GRANT PERMISSIONS TO FUNCTIONS 🔑
-- =================================================================

GRANT EXECUTE ON FUNCTION public.create_invoice_with_customer(uuid, text, text, text, numeric, date, date, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.add_payment(uuid, numeric, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.add_note(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.postpone_due_date(uuid, date, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_invoice(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_customer(uuid) TO authenticated;


-- =================================================================
-- 10. FINAL OPTIMIZATIONS 📊
-- =================================================================

-- Analyze tables for better query planning after setup
ANALYZE public.customers;
ANALYZE public.invoices;
ANALYZE public.interaction_logs;

-- =================================================================
--                       END OF SCRIPT
-- =================================================================
