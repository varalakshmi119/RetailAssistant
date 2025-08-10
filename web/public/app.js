import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.45.0'

const config = window.ENV || {}
const cfgWarning = document.getElementById('config-warning')
if (!config.SUPABASE_URL || !config.SUPABASE_ANON_KEY) {
  cfgWarning?.classList.remove('hidden')
}

const supabase = createClient(config.SUPABASE_URL || 'http://invalid', config.SUPABASE_ANON_KEY || 'invalid')

const loginForm = document.getElementById('login-form')
const emailInput = document.getElementById('email')
const passwordInput = document.getElementById('password')
const authError = document.getElementById('auth-error')

const authSection = document.getElementById('auth-section')
const dashboard = document.getElementById('dashboard')
const customersList = document.getElementById('customers-list')
const invoicesList = document.getElementById('invoices-list')
const userControls = document.getElementById('user-controls')
const userEmail = document.getElementById('user-email')
const signOutBtn = document.getElementById('sign-out')
const signUpBtn = document.getElementById('sign-up')

const waStatusDot = document.getElementById('wa-status-dot')
const waStatusText = document.getElementById('wa-status-text')
const waStartBtn = document.getElementById('wa-start')
const waSendBtn = document.getElementById('wa-send')

const modal = document.getElementById('modal')
const modalBody = document.getElementById('modal-body')
const modalClose = document.getElementById('modal-close')

function openModal(contentHtml) {
  modalBody.innerHTML = contentHtml
  modal.classList.remove('hidden')
}
function closeModal() {
  modal.classList.add('hidden')
  modalBody.innerHTML = ''
}
modalClose.addEventListener('click', closeModal)
modal.addEventListener('click', (e) => {
  if (e.target.classList.contains('modal-backdrop')) closeModal()
})

function formatCurrency(value) {
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(num)
}

async function loadCustomers() {
  customersList.innerHTML = '<div class="item">Loading...</div>'
  const { data, error } = await supabase
    .from('customers')
    .select('id, name, phone, email, created_at')
    .order('created_at', { ascending: false })

  if (error) {
    customersList.innerHTML = `<div class="item error">${error.message}</div>`
    return
  }

  if (!data || data.length === 0) {
    customersList.innerHTML = '<div class="item muted">No customers yet.</div>'
    return
  }

  customersList.innerHTML = data
    .map(c => `
      <div class="item" style="grid-template-columns: 1fr auto;">
        <div>
          <div><strong>${c.name}</strong></div>
          <div class="muted">${[c.email, c.phone].filter(Boolean).join(' • ') || '—'}</div>
        </div>
      </div>
    `)
    .join('')
}

async function signPaths(paths) {
  const results = new Map()
  for (const p of paths) {
    try {
      const { data, error } = await supabase.storage.from('invoice-scans').createSignedUrl(p, 3600)
      if (!error && data?.signedUrl) results.set(p, data.signedUrl)
    } catch {}
  }
  return results
}

async function loadInvoices() {
  invoicesList.innerHTML = '<div class="item">Loading...</div>'
  const { data, error } = await supabase
    .from('invoices')
    .select('id, total_amount, amount_paid, issue_date, due_date, status, original_scan_url, customers(name)')
    .order('due_date', { ascending: true })

  if (error) {
    invoicesList.innerHTML = `<div class="item error">${error.message}</div>`
    return
  }

  if (!data || data.length === 0) {
    invoicesList.innerHTML = '<div class="item muted">No invoices yet.</div>'
    return
  }

  const paths = Array.from(new Set((data || []).map(i => i.original_scan_url).filter(Boolean)))
  const pathToUrl = await signPaths(paths)

  invoicesList.innerHTML = data
    .map(i => {
      const url = i.original_scan_url ? (pathToUrl.get(i.original_scan_url) || '') : ''
      return `
      <div class="item">
        <img class="thumb" src="${url}" alt="Invoice" onerror="this.style.visibility='hidden'" data-full="${url}" />
        <div>
          <div><strong>${i.customers?.name || 'Unknown customer'}</strong></div>
          <div class="muted">${i.status} • Due ${i.due_date}</div>
          <div>${formatCurrency(i.total_amount)} total${Number(i.amount_paid) ? ` • ${formatCurrency(i.amount_paid)} paid` : ''}</div>
        </div>
        <div>
          ${url ? `<button class="btn" data-view="${url}">View</button>` : ''}
        </div>
      </div>
    `
    })
    .join('')

  // Bind view buttons & thumb click
  invoicesList.querySelectorAll('button[data-view]').forEach(btn => {
    btn.addEventListener('click', () => openModal(`<img src="${btn.getAttribute('data-view')}" alt="Invoice" />`))
  })
  invoicesList.querySelectorAll('.thumb').forEach(img => {
    img.addEventListener('click', () => {
      const full = img.getAttribute('data-full')
      if (full) openModal(`<img src="${full}" alt="Invoice" />`)
    })
  })
}

function showDashboard(session) {
  authSection.classList.add('hidden')
  dashboard.classList.remove('hidden')
  userControls.classList.remove('hidden')
  userEmail.textContent = session?.user?.email || ''
  loadCustomers()
  loadInvoices()
  refreshWaStatus()
}

function showAuth() {
  authSection.classList.remove('hidden')
  dashboard.classList.add('hidden')
  userControls.classList.add('hidden')
  userEmail.textContent = ''
}

// Initial session check
;(async () => {
  const {
    data: { session },
  } = await supabase.auth.getSession()
  if (session?.user) {
    showDashboard(session)
  } else {
    showAuth()
  }
})()

// Auth state changes
supabase.auth.onAuthStateChange((_event, session) => {
  if (session?.user) {
    showDashboard(session)
  } else {
    showAuth()
  }
})

loginForm.addEventListener('submit', async (e) => {
  e.preventDefault()
  authError.textContent = ''
  const email = emailInput.value.trim()
  const password = passwordInput.value
  if (!email || !password) return

  const { error } = await supabase.auth.signInWithPassword({ email, password })
  if (error) {
    authError.textContent = error.message
  }
})

signUpBtn.addEventListener('click', async () => {
  authError.textContent = ''
  const email = emailInput.value.trim()
  const password = passwordInput.value
  if (!email || !password) {
    authError.textContent = 'Enter email and password, then click Sign up.'
    return
  }
  const { error } = await supabase.auth.signUp({ email, password })
  if (error) {
    authError.textContent = error.message
    return
  }
  authError.textContent = 'Check your email to confirm your account, then log in.'
})

signOutBtn.addEventListener('click', async () => {
  await supabase.auth.signOut()
})

async function refreshWaStatus() {
  try {
    const res = await fetch('/api/wa/status')
    const data = await res.json()
    waStatusDot.classList.remove('ok', 'bad')
    if (data.connected) {
      waStatusDot.classList.add('ok')
      waStatusText.textContent = 'Connected'
    } else {
      waStatusDot.classList.add('bad')
      waStatusText.textContent = 'Not connected'
    }
  } catch {
    waStatusDot.classList.remove('ok'); waStatusDot.classList.add('bad')
    waStatusText.textContent = 'Unavailable'
  }
}

waStartBtn.addEventListener('click', async () => {
  const res = await fetch('/api/wa/start', { method: 'POST' })
  const data = await res.json()
  await refreshWaStatus()
  if (data.qrDataUrl && !data.connected) {
    openModal(`<img src="${data.qrDataUrl}" alt="WhatsApp QR" />`)
  }
})

waSendBtn.addEventListener('click', async () => {
  waSendBtn.disabled = true
  try {
    const { data: { session } } = await supabase.auth.getSession()
    const accessToken = session?.access_token
    if (!accessToken) throw new Error('Not authenticated')

    const res = await fetch('/api/reminders/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ accessToken })
    })
    const result = await res.json()
    if (!result.ok) throw new Error(result.error || 'Failed to send reminders')
    openModal(`<div>
      <h3>Reminders result</h3>
      <pre style="white-space: pre-wrap">${JSON.stringify(result.results, null, 2)}</pre>
    </div>`)
  } catch (e) {
    openModal(`<div class="error">${e?.message || e}</div>`) 
  } finally {
    waSendBtn.disabled = false
    refreshWaStatus()
  }
})
