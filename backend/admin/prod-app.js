// ==========================================================================
// AlkilApp Admin - Main Application Entry Point
// ==========================================================================

import { initTheme, toggleTheme } from './utils/helpers.js';
import { api } from './services/api.js';
import { showToast, toastSuccess, toastError } from './utils/helpers.js';

// Page components (lazy loaded)
const pageModules = {
  dashboard: () => import('./components/Dashboard.js'),
  verificaciones: () => import('./components/Verificaciones.js'),
  publicaciones: () => import('./components/Publicaciones.js'),
  reportes: () => import('./components/Reportes.js'),
  usuarios: () => import('./components/Usuarios.js'),
  stats: () => import('./components/Stats.js'),
};

class AdminApp {
  constructor() {
    this.currentPage = 'dashboard';
    this.pageInstance = null;
    this.isLoading = false;
    this.searchDebounce = null;
  }

  async init() {
    initTheme();
    this.bindEvents();
    await this.loadPage(this.getCurrentPage());
    this.updateBadges();
  }

  getCurrentPage() {
    const path = window.location.pathname;
    if (path === '/' || path === '/dashboard') return 'dashboard';
    return path.slice(1).split('/')[0] || 'dashboard';
  }

  bindEvents() {
    // Theme toggle
    document.getElementById('themeToggle')?.addEventListener('click', () => toggleTheme());

    // Sidebar toggle (mobile)
    document.getElementById('menuToggle')?.addEventListener('click', () => this.toggleSidebar());
    document.getElementById('sidebarOverlay')?.addEventListener('click', () => this.closeSidebar());

    // Navigation links
    document.querySelectorAll('.nav-item').forEach(link => {
      link.addEventListener('click', e => this.handleNav(e));
    });

    // Logout
    document.getElementById('logoutBtn')?.addEventListener('click', () => this.logout());

    // Global search
    const searchInput = document.getElementById('globalSearch');
    if (searchInput) {
      searchInput.addEventListener('input', debounce(e => {
        if (this.pageInstance && typeof this.pageInstance.handleGlobalSearch === 'function') {
          this.pageInstance.handleGlobalSearch(e.target.value);
        }
      }, 300));

      // Keyboard shortcut: Cmd/Ctrl + K
      document.addEventListener('keydown', e => {
        if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
          e.preventDefault();
          document.getElementById('globalSearch')?.focus();
        }
      });
    }

    // Logout button in sidebar
    document.getElementById('logoutBtn')?.addEventListener('click', () => this.logout());

    // Handle browser back/forward
    window.addEventListener('popstate', () => this.loadPage(this.getCurrentPage()));
  }

  async handleNav(e) {
    e.preventDefault();
    const link = e.currentTarget;
    const page = link.dataset.page;
    if (page) {
      this.navigate(page);
      this.closeSidebar();
    }
  }

  navigate(page) {
    if (page === this.currentPage) return;
    window.history.pushState({}, '', '/' + (page === 'dashboard' ? '' : page));
    this.loadPage(page);
  }

  async loadPage(page) {
    if (this.isLoading) return;
    this.isLoading = true;

    // Update nav active state
    document.querySelectorAll('.nav-item').forEach(l => l.classList.toggle('active', l.dataset.page === page));
    this.currentPage = page;

    try {
      // Destroy previous page instance
      if (this.pageInstance && typeof this.pageInstance.destroy === 'function') {
        this.pageInstance.destroy();
      }

      // Load page module
      const loader = pageModules[page];
      if (!loader) throw new Error(`Página no encontrada: ${page}`);

      const module = await loader();
      const PageClass = module.default;

      // Render page
      const container = document.getElementById('pageContent');
      container.innerHTML = '<div class="loading" style="padding:60px;text-align:center"><span class="loading"><span class="spinner"></span>Cargando...</span></div>';

      this.pageInstance = new PageClass(api);
      await this.pageInstance.render(document.getElementById('pageContent'));

      // Update page title
      const titles = {
        dashboard: 'Dashboard',
        verificaciones: 'Verificaciones',
        publicaciones: 'Publicaciones',
        reportes: 'Denuncias',
        usuarios: 'Usuarios',
        stats: 'Estadísticas',
      };
      document.title = `AlkilApp Admin - ${titles[page] || page}`;

      // Update badges
      this.updateBadges();

    } catch (err) {
      console.error('Error cargando página:', err);
      document.getElementById('pageContent').innerHTML = `
        <div class="empty-state">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <h3>Error al cargar la página</h3>
          <p>${err.message}</p>
          <button class="btn btn-primary" onclick="location.reload()">Recargar</button>
        </div>`;
    } finally {
      this.isLoading = false;
    }
  }

  async updateBadges() {
    try {
      const data = await api.get('/resumen');
      this.setBadge('verificaciones', data.verificaciones?.pendientes || 0);
      this.setBadge('publicaciones', data.propiedades?.pendientes || 0);
      this.setBadge('reportes', data.reportes?.pendientes || 0);
    } catch {}
  }

  setBadge(id, count) {
    const el = document.getElementById(`badge-${id}`);
    if (el) {
      el.textContent = count > 99 ? '99+' : count;
      el.style.display = count > 0 ? 'inline-flex' : 'none';
    }
  }

  toggleSidebar() {
    document.getElementById('sidebar')?.classList.toggle('open');
    document.getElementById('sidebarOverlay')?.classList.toggle('visible');
    const btn = document.getElementById('menuToggle');
    if (btn) btn.setAttribute('aria-expanded', document.getElementById('sidebar')?.classList.contains('open') || 'false');
  }

  closeSidebar() {
    document.getElementById('sidebar')?.classList.remove('open');
    document.getElementById('sidebarOverlay')?.classList.remove('visible');
    document.getElementById('menuToggle')?.setAttribute('aria-expanded', 'false');
  }

  async logout() {
    try {
      await fetch('/logout', { method: 'POST', credentials: 'include' });
      window.location.href = '/login';
    } catch {
      window.location.href = '/login';
    }
  }
}

// Debounce helper
function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(window.debounceTimer);
    window.debounceTimer = setTimeout(() => fn.apply(this, arguments), delay);
  };
}

// Initialize app when DOM ready
document.addEventListener('DOMContentLoaded', async () => {
  const app = new AdminApp();
  window.adminApp = app; // Global for debugging
  await app.init();
});

// Global error handler
window.addEventListener('unhandledrejection', e => {
  console.error('Unhandled rejection:', e.reason);
});