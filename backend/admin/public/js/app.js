// ==========================================================================
// AlkilApp Admin - Main Application Entry Point
// ==========================================================================

import { initTheme, toggleTheme, showToast, toastSuccess, toastError, debounce } from './utils/helpers.js';
import { api } from './services/api.js';

// Módulos de páginas (Carga perezosa / Lazy loaded)
const pageModules = {
  dashboard: () => import('./components/Dashboard.js?v=27'),
  verificaciones: () => import('./components/Verificaciones.js?v=27'),
  publicaciones: () => import('./components/Publicaciones.js?v=27'),
  reportes: () => import('./components/Reportes.js?v=27'),
  soporte: () => import('./components/Soporte.js?v=27'),
  usuarios: () => import('./components/Usuarios.js?v=27'),
  stats: () => import('./components/Stats.js?v=27'),
};

const PAGE_TITLES = {
  dashboard: 'Dashboard',
  verificaciones: 'Verificaciones de Identidad',
  publicaciones: 'Publicaciones y Propiedades',
  reportes: 'Denuncias y Moderación',
  soporte: 'Soporte e Incidentes',
  usuarios: 'Gestión de Usuarios',
  stats: 'Estadísticas y Analíticas',
};

class AdminApp {
  constructor() {
    this.currentPage = 'dashboard';
    this.pageInstance = null;
    this.isLoading = false;
  }

  async init() {
    initTheme();
    this.bindEvents();
    await this.loadPage(this.getCurrentPage());
    await this.updateBadges();
  }

  getCurrentPage() {
    const path = window.location.pathname;
    if (path === '/' || path === '/dashboard' || !path) return 'dashboard';
    const page = path.slice(1).split('/')[0].toLowerCase();
    return pageModules[page] ? page : 'dashboard';
  }

  bindEvents() {
    // Alternar Tema (Oscuro / Claro)
    document.getElementById('themeToggle')?.addEventListener('click', () => toggleTheme());

    // Control del Menú Lateral (Mobile)
    document.getElementById('menuToggle')?.addEventListener('click', () => this.toggleSidebar());
    document.getElementById('sidebarOverlay')?.addEventListener('click', () => this.closeSidebar());

    // Enlaces de Navegación
    document.querySelectorAll('.nav-item').forEach(link => {
      link.addEventListener('click', e => this.handleNav(e));
    });

    // Cerrar Sesión
    document.getElementById('logoutBtn')?.addEventListener('click', () => this.logout());

    // Búsqueda Global (Teclado + Input)
    const searchInput = document.getElementById('globalSearch');
    if (searchInput) {
      searchInput.addEventListener('input', debounce(e => {
        const query = e.target.value.trim();
        this.dispatchGlobalSearch(query);
      }, 300));

      // Atajo de teclado: Cmd/Ctrl + K
      document.addEventListener('keydown', e => {
        if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
          e.preventDefault();
          searchInput.focus();
          searchInput.select();
        }
      });
    }

    // Manejar botones Atrás / Adelante del navegador
    window.addEventListener('popstate', () => this.loadPage(this.getCurrentPage()));
  }

  dispatchGlobalSearch(query) {
    if (this.pageInstance) {
      if (typeof this.pageInstance.handleGlobalSearch === 'function') {
        this.pageInstance.handleGlobalSearch(query);
      } else {
        // Fallback si el componente maneja filtros locales con un input #searchInput
        const localInput = document.getElementById('searchInput');
        if (localInput) {
          localInput.value = query;
          localInput.dispatchEvent(new Event('input', { bubbles: true }));
        }
      }
    }
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
    if (page === this.currentPage && !this.isLoading) return;
    const targetPath = page === 'dashboard' ? '/' : `/${page}`;
    window.history.pushState({}, '', targetPath);
    this.loadPage(page);
  }

  async loadPage(page) {
    if (this.isLoading) return;
    this.isLoading = true;

    // Actualizar estado activo en menú de navegación
    document.querySelectorAll('.nav-item').forEach(l => {
      l.classList.toggle('active', l.dataset.page === page);
    });
    this.currentPage = page;

    const container = document.getElementById('pageContent');

    try {
      // Destruir instancia del componente previo
      if (this.pageInstance && typeof this.pageInstance.destroy === 'function') {
        this.pageInstance.destroy();
        this.pageInstance = null;
      }

      // Cargar módulo dinámicamente
      const loader = pageModules[page];
      if (!loader) throw new Error(`Página no encontrada: "${page}"`);

      if (container) {
        container.innerHTML = `
          <div class="loading-state" style="display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80px 20px; color: var(--text-muted, #64748b);">
            <div class="spinner" style="width: 36px; height: 36px; border: 3px solid rgba(0,0,0,0.1); border-top-color: var(--primary-color, #3b82f6); border-radius: 50%; animation: spin 0.8s linear infinite; margin-bottom: 1rem;"></div>
            <p style="font-size: 0.875rem; font-weight: 500; margin: 0;">Cargando módulo...</p>
          </div>
        `;
      }

      const module = await loader();
      const PageClass = module.default;

      // Instanciar y renderizar el componente enviándole la instancia de API
      this.pageInstance = new PageClass(api);
      
      if (container) {
        await this.pageInstance.render(container);
      }

      // Actualizar título del documento
      document.title = `AlkilApp Admin - ${PAGE_TITLES[page] || page}`;

      // Actualizar badges en sidebar
      await this.updateBadges();

    } catch (err) {
      console.error('Error al cargar la página:', err);
      if (container) {
        container.innerHTML = `
          <div class="empty-state" style="text-align: center; padding: 60px 20px; background: var(--card-bg, #fff); border-radius: 12px; border: 1px solid var(--border-color, #e2e8f0); margin: 20px 0;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="width: 48px; height: 48px; color: var(--danger, #ef4444); margin-bottom: 12px;">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <h3 style="font-size: 1.125rem; font-weight: 700; margin: 0 0 8px 0; color: var(--text-color, #0f172a);">No se pudo cargar la vista</h3>
            <p style="font-size: 0.875rem; color: var(--text-muted, #64748b); margin: 0 0 16px 0;">${this.escape(err.message)}</p>
            <button class="btn btn-primary" onclick="window.location.reload()" style="padding: 0.5rem 1rem; border-radius: 0.375rem; cursor: pointer;">Reintentar</button>
          </div>
        `;
      }
    } finally {
      this.isLoading = false;
    }
  }

  async updateBadges() {
    try {
      const data = await api.get('/resumen');
      if (!data) return;

      this.setBadge('verificaciones', data.verificaciones?.pendientes ?? 0);
      this.setBadge('publicaciones', data.propiedades?.pendientes ?? data.publicaciones?.pendientes ?? 0);
      this.setBadge('reportes', data.reportes?.pendientes ?? 0);
      this.setBadge('soporte', data.soporte?.pendientes ?? 0);
    } catch (err) {
      console.warn('No se pudieron actualizar los contadores del sidebar:', err);
    }
  }

  setBadge(id, count) {
    const el = document.getElementById(`badge-${id}`);
    if (el) {
      const numericCount = Number(count) || 0;
      el.textContent = numericCount > 99 ? '99+' : numericCount;
      el.style.display = numericCount > 0 ? 'inline-flex' : 'none';
    }
  }

  toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');
    const btn = document.getElementById('menuToggle');

    const isOpen = sidebar?.classList.toggle('open');
    overlay?.classList.toggle('visible', isOpen);
    btn?.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
  }

  closeSidebar() {
    document.getElementById('sidebar')?.classList.remove('open');
    document.getElementById('sidebarOverlay')?.classList.remove('visible');
    document.getElementById('menuToggle')?.setAttribute('aria-expanded', 'false');
  }

  async logout() {
    try {
      await api.post('/logout').catch(() => fetch('/logout', { method: 'POST', credentials: 'include' }));
    } catch (e) {
      console.warn('Error en cierre de sesión:', e);
    } finally {
      window.location.href = '/login';
    }
  }

  escape(str) {
    if (!str) return '';
    return String(str).replace(/[&<>"']/g, c => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[c]));
  }
}

// Estilo de animación inyectado dinámicamente para el spinner
const style = document.createElement('style');
style.textContent = `
  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }
`;
document.head.appendChild(style);

// Inicializar la aplicación cuando el DOM esté listo
document.addEventListener('DOMContentLoaded', async () => {
  const app = new AdminApp();
  window.adminApp = app; // Exposición global para depuración y eventos de actualización
  await app.init();
});

// Manejo global de promesas no capturadas
window.addEventListener('unhandledrejection', e => {
  console.error('Excepción no controlada:', e.reason);
});