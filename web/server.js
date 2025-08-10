import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import { createClient as createSupabaseClient } from '@supabase/supabase-js';
import { makeWASocket, useMultiFileAuthState, DisconnectReason } from '@whiskeysockets/baileys';
import Pino from 'pino';
import QRCode from 'qrcode';
import axios from 'axios';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 5173;
app.use(express.json({ limit: '2mb' }));

// Serve environment configuration to the frontend
app.get('/env.js', (_req, res) => {
  const config = {
    SUPABASE_URL: process.env.SUPABASE_URL || '',
    SUPABASE_ANON_KEY: process.env.SUPABASE_ANON_KEY || '',
  };
  res.type('application/javascript').send(`window.ENV = ${JSON.stringify(config)};`);
});

// ---- WhatsApp (Baileys) integration ----
const DEFAULT_COUNTRY_CODE = process.env.DEFAULT_COUNTRY_CODE || '1';
const WA_AUTH_DIR = path.join(__dirname, 'wa_auth');
let waSock = null;
let waIsConnected = false;
let lastQrDataUrl = null;
let reconnecting = false;

function formatPhoneForWa(phoneRaw) {
  if (!phoneRaw) return null;
  const digits = String(phoneRaw).replace(/\D/g, '');
  const withCc = digits.length <= 10 ? `${DEFAULT_COUNTRY_CODE}${digits}` : digits;
  return `${withCc}@s.whatsapp.net`;
}

async function initWhatsApp(startIfNeeded = false) {
  const logger = Pino({ level: 'error' });
  const { state, saveCreds } = await useMultiFileAuthState(WA_AUTH_DIR);

  waSock = makeWASocket({ logger, printQRInTerminal: false, auth: state });

  waSock.ev.on('creds.update', saveCreds);

  waSock.ev.on('connection.update', async (update) => {
    const { connection, lastDisconnect, qr } = update;
    if (qr) {
      try { lastQrDataUrl = await QRCode.toDataURL(qr); } catch {}
    }
    if (connection === 'open') {
      waIsConnected = true;
      lastQrDataUrl = null;
    } else if (connection === 'close') {
      waIsConnected = false;
      const shouldReconnect = (lastDisconnect?.error?.output?.statusCode !== DisconnectReason.loggedOut);
      if (shouldReconnect && startIfNeeded && !reconnecting) {
        reconnecting = true;
        setTimeout(async () => {
          try { await initWhatsApp(true); } finally { reconnecting = false; }
        }, 2_000);
      }
    }
  });
}

async function ensureWhatsAppStarted() {
  if (!waSock) {
    await initWhatsApp(true);
  }
  return waSock;
}

async function sendWhatsAppImageMessage(toPhoneRaw, imageUrl, captionText) {
  if (!waIsConnected) throw new Error('WhatsApp not connected');
  const jid = formatPhoneForWa(toPhoneRaw);
  if (!jid) throw new Error('Invalid phone');

  // Fetch image as buffer
  const response = await axios.get(imageUrl, { responseType: 'arraybuffer' });
  const buffer = Buffer.from(response.data);

  await waSock.sendMessage(jid, { image: buffer, caption: captionText });
}

app.get('/api/wa/status', async (_req, res) => {
  res.json({ connected: waIsConnected, qrDataUrl: lastQrDataUrl || null });
});

app.post('/api/wa/start', async (_req, res) => {
  try {
    await ensureWhatsAppStarted();
    res.json({ ok: true, connected: waIsConnected, qrDataUrl: lastQrDataUrl || null });
  } catch (e) {
    res.status(500).json({ ok: false, error: String(e?.message || e) });
  }
});

app.post('/api/wa/stop', async (_req, res) => {
  try {
    if (waSock) {
      await waSock.logout();
      waSock = null;
      waIsConnected = false;
      lastQrDataUrl = null;
    }
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ ok: false, error: String(e?.message || e) });
  }
});

// ---- Reminders endpoint ----
// Expects: { accessToken: string }
// Queries user's UNPAID invoices due today or earlier, sends image+caption to customer phone
app.post('/api/reminders/send', async (req, res) => {
  const accessToken = req.body?.accessToken;
  if (!accessToken) return res.status(400).json({ ok: false, error: 'Missing accessToken' });
  if (!process.env.SUPABASE_URL || !process.env.SUPABASE_ANON_KEY) {
    return res.status(500).json({ ok: false, error: 'Server missing Supabase config' });
  }
  try {
    await ensureWhatsAppStarted();
    if (!waIsConnected) return res.status(400).json({ ok: false, error: 'WhatsApp not connected' });

    const supabase = createSupabaseClient(process.env.SUPABASE_URL, process.env.SUPABASE_ANON_KEY, {
      auth: { persistSession: false },
      global: { headers: { Authorization: `Bearer ${accessToken}` } },
    });

    const today = new Date().toISOString().slice(0, 10);
    const { data, error } = await supabase
      .from('invoices')
      .select('id, total_amount, amount_paid, due_date, status, original_scan_url, customers(name, phone)')
      .eq('status', 'UNPAID')
      .lte('due_date', today)
      .order('due_date', { ascending: true });

    if (error) throw error;

    const results = [];
    for (const inv of data || []) {
      const phone = inv.customers?.phone;
      const customerName = inv.customers?.name || 'Customer';
      const path = inv.original_scan_url;
      if (!phone || !path) {
        results.push({ invoiceId: inv.id, sent: false, reason: 'Missing phone or image path' });
        continue;
      }

      // Create signed URL for the storage object
      const { data: signed, error: signErr } = await supabase.storage.from('invoice-scans').createSignedUrl(path, 3600);
      if (signErr || !signed?.signedUrl) {
        results.push({ invoiceId: inv.id, sent: false, reason: signErr?.message || 'Could not sign URL' });
        continue;
      }

      const caption = `Payment reminder for ${customerName}\nInvoice due: ${inv.due_date}\nTotal: $${inv.total_amount}`;
      try {
        await sendWhatsAppImageMessage(phone, signed.signedUrl, caption);
        results.push({ invoiceId: inv.id, sent: true });
      } catch (e) {
        results.push({ invoiceId: inv.id, sent: false, reason: String(e?.message || e) });
      }
    }

    res.json({ ok: true, results });
  } catch (e) {
    res.status(500).json({ ok: false, error: String(e?.message || e) });
  }
});

// Serve static assets
app.use(express.static(path.join(__dirname, 'public'), {
  extensions: ['html'],
  maxAge: process.env.NODE_ENV === 'production' ? '1h' : 0,
}));

// Fallback to index.html
app.get('*', (_req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`RetailAssistant web running at http://localhost:${PORT}`);
});
