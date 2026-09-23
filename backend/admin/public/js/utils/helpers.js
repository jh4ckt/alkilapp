// ==========================================================================
// AlkilApp Admin - Utility Functions
// ==========================================================================

/* ==========================================================================
   Date & Time
   ========================================================================== */

export function formatDate(date, options = {}) {
  if (!date) return '-';
  const d = date instanceof Date ? date : new Date(date);
  if (isNaN(d)) return '-';
  const opts = { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', ...options };
  return d.toLocaleString('es-PE', opts);
}

export function formatRelative(date) {
  if (!date) return '-';
  const d = date instanceof Date ? date : new Date(date);
  if (isNaN(d)) return '-';
  const diff = Date.now() - d.getTime();
  const seconds = Math.floor(diff / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  if (days > 0) return `hace ${days}d`;
  if (hours > 0) return `hace ${hours}h`;
  if (minutes > 0) return `hace ${minutes}m`;
  return 'hace un momento';
}

export function formatCurrency(value, currency = 'PEN') {
  if (value == null) return '-';
  return new Intl.NumberFormat('es-PE', { style: 'currency', currency, minimumFractionDigits: 0 }).format(value);
}

export function formatNumber(value) {
  if (value == null) return '-';
  return new Intl.NumberFormat('es-PE').format(value);
}

/* ==========================================================================
   String & Text
   ========================================================================== */

export function truncate(str, length = 50, suffix = '…') {
  if (!str || str.length <= length) return str;
  return str.slice(0, length).trim() + suffix;
}

export function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/[&<>"']/g, c => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[c]));
}

function escapeRegExp(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function highlightMatch(text, query) {
  if (!text) return '';
  if (!query) return escapeHtml(text);
  const escapedText = escapeHtml(text);
  const escapedQuery = escapeHtml(query);
  const regex = new RegExp(`(${escapeRegExp(escapedQuery)})`, 'gi');
  return escapedText.replace(regex, '<mark>$1</mark>');
}

/* ==========================================================================
   DOM Helpers
   ========================================================================== */

export function createElement(html) {
  const template = document.createElement('template');
  template.innerHTML = html.trim();
  return template.content.firstElementChild;
}

export function renderList(container, items, renderItem, emptyMessage = 'Sin resultados.') {
  if (!items.length) {
    container.innerHTML = `<div class="empty-state"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg><h3>${escapeHtml(emptyMessage)}</h3></div>`;
    return;
  }
  container.innerHTML = '';
  const fragment = document.createDocumentFragment();
  items.forEach(item => fragment.appendChild(renderItem(item)));
  container.appendChild(fragment);
}

/* ==========================================================================
   Toast Notifications
   ========================================================================== */

let toastContainer = null;

function getToastContainer() {
  if (!toastContainer) {
    toastContainer = document.getElementById('toastContainer');
    if (!toastContainer) {
      toastContainer = document.createElement('div');
      toastContainer.id = 'toastContainer';
      toastContainer.className = 'toast-container';
      document.body.appendChild(toastContainer);
    }
  }
  return toastContainer;
}

export function showToast(message, type = 'info', duration = 4000) {
  const container = getToastContainer();
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.style.pointerEvents = 'auto';

  const icons = {
    info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>',
    success: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
    warning: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
    danger: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>',
  };

  toast.innerHTML = `
    <div class="toast-icon">${icons[type] || icons.info}</div>
    <div class="toast-content">${escapeHtml(message)}</div>
    <button class="toast-close" aria-label="Cerrar"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
  `;

  const closeBtn = toast.querySelector('.toast-close');
  closeBtn.addEventListener('click', () => removeToast(toast));

  container.appendChild(toast);

  setTimeout(() => removeToast(toast), duration);

  return toast;
}

function removeToast(toast) {
  if (!toast || !toast.parentElement) return;
  toast.style.animation = 'slideOut .3s ease forwards';
  setTimeout(() => toast.remove(), 300);
}

export function toastSuccess(msg, dur) { return showToast(msg, 'success', dur); }
export function toastError(msg, dur) { return showToast(msg, 'danger', dur); }
export function toastWarning(msg, dur) { return showToast(msg, 'warning', dur); }
export function toastInfo(msg, dur) { return showToast(msg, 'info', dur); }

/* ==========================================================================
   Modal Helpers
   ========================================================================== */

let modalRoot = null;

export function openModal(html, options = {}) {
  if (!modalRoot) modalRoot = document.getElementById('modalRoot');
  if (!modalRoot) {
    modalRoot = document.createElement('div');
    modalRoot.id = 'modalRoot';
    document.body.appendChild(modalRoot);
  }

  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.innerHTML = `
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <div class="modal-header">
        <h3 id="modal-title" class="modal-title">${escapeHtml(options.title || '')}</h3>
        <button class="modal-close" aria-label="Cerrar"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
      </div>
      <div class="modal-body">${html}</div>
      ${options.footer ? `<div class="modal-footer">${options.footer}</div>` : ''}
    </div>
  `;

  const escHandler = (e) => {
    if (e.key === 'Escape') close();
  };

  const close = () => {
    document.removeEventListener('keydown', escHandler);
    overlay.classList.remove('open');
    setTimeout(() => overlay.remove(), 300);
    if (typeof options.onClose === 'function') options.onClose();
  };

  const closeBtn = overlay.querySelector('.modal-close');
  closeBtn.addEventListener('click', close);
  overlay.addEventListener('click', e => { if (e.target === overlay) close(); });
  document.addEventListener('keydown', escHandler);

  requestAnimationFrame(() => overlay.classList.add('open'));
  modalRoot.appendChild(overlay);
  return { close, overlay };
}

export function confirmModal(message, options = {}) {
  return new Promise(resolve => {
    let resolved = false;
    const footer = `
      <button class="btn btn-secondary" data-result="false">${escapeHtml(options.cancelText || 'Cancelar')}</button>
      <button class="btn ${options.danger ? 'btn-danger' : 'btn-primary'}" data-result="true">${escapeHtml(options.confirmText || 'Confirmar')}</button>
    `;

    const { close } = openModal(`<p>${escapeHtml(message)}</p>`, {
      title: options.title || 'Confirmar',
      footer,
      onClose: () => {
        if (!resolved) {
          resolved = true;
          resolve(false);
        }
      }
    });

    const modalEl = modalRoot.lastElementChild;
    modalEl.querySelectorAll('[data-result]').forEach(btn => {
      btn.addEventListener('click', () => {
        resolved = true;
        close();
        resolve(btn.dataset.result === 'true');
      });
    });
  });
}

/* ==========================================================================
   Loading State
   ========================================================================== */

export function setLoading(element, loading, text = 'Cargando...') {
  if (!element) return;
  if (loading) {
    element.dataset.originalHtml = element.innerHTML;
    element.innerHTML = `<span class="loading"><span class="spinner"></span>${escapeHtml(text)}</span>`;
    element.disabled = true;
  } else if (element.dataset.originalHtml) {
    element.innerHTML = element.dataset.originalHtml;
    element.disabled = false;
    delete element.dataset.originalHtml;
  }
}

/* ==========================================================================
   Debounce / Throttle
   ========================================================================== */

export function debounce(fn, delay) {
  let timer;
  return function (...args) {
    clearTimeout(timer);
    timer = setTimeout(() => {
      fn.apply(this, args);
    }, delay);
  };
}

export function throttle(fn, limit) {
  let inThrottle;
  return function (...args) {
    if (!inThrottle) {
      fn.apply(this, args);
      inThrottle = true;
      setTimeout(() => inThrottle = false, limit);
    }
  };
}

/* ==========================================================================
   Storage
   ========================================================================== */

export function getStorage(key, defaultValue = null) {
  try {
    const item = localStorage.getItem(key);
    return item ? JSON.parse(item) : defaultValue;
  } catch { return defaultValue; }
}

export function setStorage(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch {}
}

/* ==========================================================================
   Theme
   ========================================================================== */

export function initTheme() {
  const stored = getStorage('theme');
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const theme = stored || (prefersDark ? 'dark' : 'light');
  document.documentElement.setAttribute('data-theme', theme);
}

export function toggleTheme() {
  const current = document.documentElement.getAttribute('data-theme');
  const next = current === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', next);
  setStorage('theme', next);
  return next;
}

/* ==========================================================================
   URL / Query Params
   ========================================================================== */

export function getQueryParams() {
  return new URLSearchParams(window.location.search);
}

export function setQueryParams(params, replace = false) {
  const url = new URL(window.location.href);
  Object.entries(params).forEach(([k, v]) => {
    if (v === null || v === undefined || v === '') url.searchParams.delete(k);
    else url.searchParams.set(k, v);
  });
  if (replace) window.history.replaceState({}, '', url);
  else window.history.pushState({}, '', url);
}

/* ==========================================================================
   Formatters Export
   ========================================================================== */

export const formatters = {
  date: formatDate,
  relative: formatRelative,
  currency: formatCurrency,
  number: formatNumber,
  truncate,
  highlight: highlightMatch,
};