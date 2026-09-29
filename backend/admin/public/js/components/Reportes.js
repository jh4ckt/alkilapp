// ==========================================================================
// AlkilApp Admin - Reportes Component
// ==========================================================================

import { debounce, confirmModal, showToast, formatDate } from '../utils/helpers.js';

const ESTADO_LABELS = { 
  pendiente: 'Pendiente', 
  resuelto: 'Resuelto' 
};

export default class Reportes {
  constructor(api) {
    this.api = api;
    this.currentPage = 1;
    this.limit = 15;
    this.total = 0;
    this.filters = { q: '', estado: '' };
    this.items = [];
  }

  async render(container) {
    this.container = container;
    this.container.innerHTML = this.getTemplate();
    this.bindEvents();
    await this.loadData();
  }

  getTemplate() {
    return `
      <header class="page-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; flex-wrap: wrap; gap: 1rem;">
        <div>
          <h1 class="page-title" style="font-size: 1.5rem; font-weight: 700; color: var(--text-color, #0f172a); margin: 0 0 0.25rem 0;">Denuncias y Reportes</h1>
          <p class="page-subtitle" style="font-size: 0.875rem; color: var(--text-muted, #64748b); margin: 0;">Gestiona los reportes recibidos sobre inmuebles y usuarios</p>
        </div>
        <div class="page-actions" style="display: flex; gap: 0.5rem; flex-wrap: wrap;">
          <button class="btn btn-secondary" id="refreshBtn" style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.5rem 1rem; border-radius: 0.5rem; font-weight: 500; cursor: pointer;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px;">
              <path d="M23 4v6h-6M1 20v-6h6"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            Refrescar
          </button>
          <button class="btn btn-secondary" id="exportBtn" style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.5rem 1rem; border-radius: 0.5rem; font-weight: 500; cursor: pointer;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px;">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg> 
            Exportar CSV
          </button>
        </div>
      </header>

      <!-- Barra de Filtros -->
      <div class="filters-bar" style="display: flex; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.5rem; align-items: flex-end; background: var(--card-bg, #ffffff); padding: 1rem 1.25rem; border-radius: 0.75rem; border: 1px solid var(--border-color, #e2e8f0); box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
        <div class="filter-group" style="flex: 2; min-width: 240px;">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Buscar denuncia</label>
          <div style="position: relative;">
            <input type="search" id="searchInput" class="form-input" placeholder="Motivo, inmueble, denunciante..." value="${this.filters.q}" style="width: 100%; padding: 0.5rem 0.75rem 0.5rem 2.25rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.5rem; font-size: 0.875rem; outline: none;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="position: absolute; left: 0.75rem; top: 50%; transform: translateY(-50%); width: 16px; height: 16px; color: #94a3b8;">
              <circle cx="11" cy="11" r="8"></circle>
              <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
          </div>
        </div>

        <div class="filter-group" style="flex: 1; min-width: 160px;">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Estado</label>
          <select id="estadoFilter" class="form-select" style="width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.5rem; font-size: 0.875rem; background-color: var(--card-bg, #fff);">
            <option value="">Todos los estados</option>
            <option value="pendiente" ${this.filters.estado === 'pendiente' ? 'selected' : ''}>Pendientes</option>
            <option value="resuelto" ${this.filters.estado === 'resuelto' ? 'selected' : ''}>Resueltos</option>
          </select>
        </div>

        <div class="filter-actions">
          <button class="btn btn-secondary" id="clearFiltersBtn" style="display: ${this.filters.q || this.filters.estado ? 'inline-flex' : 'none'}; align-items: center; gap: 0.375rem; padding: 0.5rem 0.875rem; border-radius: 0.5rem; font-size: 0.875rem; cursor: pointer;">
            Limpiar Filtros
          </button>
        </div>
      </div>

      <!-- Card / Tabla -->
      <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.75rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05); overflow: hidden;">
        <div class="table-responsive" style="overflow-x: auto;">
          <table class="table" style="width: 100%; border-collapse: collapse; text-align: left; font-size: 0.875rem;">
            <thead>
              <tr style="background-color: var(--table-header-bg, #f8fafc); border-bottom: 1px solid var(--border-color, #e2e8f0); text-transform: uppercase; font-size: 0.75rem; letter-spacing: 0.05em; color: var(--text-muted, #64748b);">
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Motivo / Detalle</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Inmueble ID</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Denunciante</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Fecha</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Estado</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600; text-align: center;">Acciones</th>
              </tr>
            </thead>
            <tbody id="reportsBody">
              <tr>
                <td colspan="6" style="padding: 3rem; text-align: center; color: var(--text-muted, #64748b);">
                  <div style="display: flex; flex-direction: column; align-items: center; gap: 0.5rem;">
                    <div class="spinner" style="width: 24px; height: 24px; border: 2px solid #cbd5e1; border-top-color: #3b82f6; border-radius: 50%; animation: spin 0.8s linear infinite;"></div>
                    <span>Cargando reportes...</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Paginación -->
        <div class="pagination-container" style="display: flex; justify-content: space-between; align-items: center; padding: 0.875rem 1.25rem; border-top: 1px solid var(--border-color, #e2e8f0); background-color: var(--card-bg, #ffffff);">
          <span id="paginationInfo" style="font-size: 0.85rem; color: var(--text-muted, #64748b);">Mostrando 0 - 0 de 0 reportes</span>
          <div id="pagination" style="display: flex; gap: 0.375rem; align-items: center;"></div>
        </div>
      </div>
    `;
  }

  bindEvents() {
    const searchInput = this.container.querySelector('#searchInput');
    searchInput?.addEventListener('input', debounce((e) => {
      this.filters.q = e.target.value.trim();
      this.currentPage = 1;
      this.loadData();
    }, 300));

    this.container.querySelector('#estadoFilter')?.addEventListener('change', (e) => {
      this.filters.estado = e.target.value;
      this.currentPage = 1;
      this.loadData();
    });

    this.container.querySelector('#clearFiltersBtn')?.addEventListener('click', () => {
      this.filters = { q: '', estado: '' };
      this.currentPage = 1;
      this.render(this.container);
    });

    this.container.querySelector('#refreshBtn')?.addEventListener('click', () => {
      this.loadData();
    });

    this.container.querySelector('#exportBtn')?.addEventListener('click', () => {
      this.exportCSV();
    });

    // Delegación de eventos en la tabla
    this.container.querySelector('#reportsBody')?.addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-action]');
      if (!btn) return;

      const row = btn.closest('tr');
      const id = row?.dataset.id;
      if (!id) return;

      if (btn.dataset.action === 'resolver') {
        this.resolver(id);
      }
    });

    // Eventos de paginación
    this.container.querySelector('#pagination')?.addEventListener('click', (e) => {
      const btn = e.target.closest('.page-btn');
      if (btn && !btn.disabled) {
        this.goToPage(parseInt(btn.dataset.page, 10));
      }
    });
  }

  async loadData() {
    try {
      const params = new URLSearchParams({
        page: this.currentPage,
        limit: this.limit,
        ...(this.filters.q && { q: this.filters.q }),
        ...(this.filters.estado && { estado: this.filters.estado })
      }).toString();

      const res = await this.api.get(`/reportes?${params}`);
      
      this.items = res.data || res.items || res || [];
      this.total = res.total || this.items.length;
      const totalPages = res.totalPages || Math.ceil(this.total / this.limit) || 1;

      this.renderTable();
      this.renderPagination(totalPages);
    } catch (err) {
      console.error('Error al cargar reportes:', err);
      this.showError('Error al cargar la lista de denuncias.');
    }
  }

  renderTable() {
    const tbody = this.container.querySelector('#reportsBody');
    if (!tbody) return;

    if (!this.items.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="6" style="padding: 3.5rem 1rem; text-align: center; color: var(--text-muted, #64748b);">
            <div style="display: flex; flex-direction: column; align-items: center; gap: 0.75rem;">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="width: 42px; height: 42px; color: #94a3b8;">
                <path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
              </svg>
              <span style="font-weight: 500; font-size: 0.95rem;">No se encontraron denuncias registradas.</span>
            </div>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = this.items.map(r => {
      const id = r.id || r._id;
      const estado = (r.estado || 'pendiente').toLowerCase();
      const fechaFormateada = formatDate ? formatDate(r.creado || r.createdAt) : (r.creado || '-');

      return `
        <tr data-id="${id}" class="table-row-hover" style="border-bottom: 1px solid var(--border-color, #f1f5f9); vertical-align: middle;">
          
          <!-- Motivo y Detalle -->
          <td style="padding: 0.875rem 1rem;">
            <strong style="display: block; font-size: 0.875rem; font-weight: 600; color: var(--text-color, #0f172a);">${this.escape(r.motivo || 'Sin motivo especificado')}</strong>
            ${r.detalle ? `<div style="font-size: 0.775rem; color: var(--text-muted, #64748b); margin-top: 2px; max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${this.escape(r.detalle)}">${this.escape(r.detalle)}</div>` : ''}
          </td>

          <!-- Inmueble -->
          <td style="padding: 0.875rem 1rem; white-space: nowrap;">
            <code style="background: #f1f5f9; padding: 0.2rem 0.4rem; border-radius: 0.25rem; font-size: 0.8rem; color: #334155;">${this.escape(r.listingId || r.inmuebleId || '-')}</code>
          </td>

          <!-- Denunciante -->
          <td style="padding: 0.875rem 1rem; color: var(--text-muted, #475569); font-size: 0.825rem;">
            ${this.escape(r.reporterNombre || r.reporterEmail || r.reporterId || '-')}
          </td>

          <!-- Fecha -->
          <td style="padding: 0.875rem 1rem; font-size: 0.8rem; color: var(--text-muted, #64748b); white-space: nowrap;">
            ${fechaFormateada}
          </td>

          <!-- Estado (Badge) -->
          <td style="padding: 0.875rem 1rem;">
            <span style="padding: 0.25rem 0.625rem; border-radius: 9999px; font-weight: 600; font-size: 0.7rem; letter-spacing: 0.025em; display: inline-flex; align-items: center; gap: 0.375rem; ${this.getBadgeStyle(estado)}">
              <span style="width: 6px; height: 6px; border-radius: 50%; background-color: currentColor;"></span>
              ${(ESTADO_LABELS[estado] || estado).toUpperCase()}
            </span>
          </td>

          <!-- Acciones -->
          <td style="padding: 0.875rem 1rem; text-align: center;">
            ${estado === 'pendiente' 
              ? `<button class="btn btn-sm btn-primary" data-action="resolver" style="display: inline-flex; align-items: center; gap: 0.375rem; padding: 0.35rem 0.75rem; font-size: 0.775rem; border-radius: 0.375rem; font-weight: 500; cursor: pointer;" title="Marcar como resuelto">
                   <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                   Resolver
                 </button>` 
              : `<span style="font-size: 0.775rem; color: #10b981; font-weight: 500; display: inline-flex; align-items: center; gap: 0.25rem;">
                   <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg> Finalizado
                 </span>`
            }
          </td>

        </tr>
      `;
    }).join('');
  }

  getBadgeStyle(estado) {
    switch (estado) {
      case 'resuelto':
        return 'background-color: #ecfdf5; color: #047857; border: 1px solid #a7f3d0;';
      case 'pendiente':
      default:
        return 'background-color: #fffbeb; color: #b45309; border: 1px solid #fde68a;';
    }
  }

  renderPagination(totalPages) {
    const info = this.container.querySelector('#paginationInfo');
    if (info) {
      const start = (this.currentPage - 1) * this.limit + 1;
      const end = Math.min(this.currentPage * this.limit, this.total);
      info.textContent = `Mostrando ${this.total > 0 ? start : 0} - ${end} de ${this.total} reportes`;
    }

    const container = this.container.querySelector('#pagination');
    if (!container) return;
    if (totalPages <= 1) { 
      container.innerHTML = ''; 
      return; 
    }

    let html = `
      <button class="btn btn-secondary btn-sm page-btn" data-page="${this.currentPage - 1}" ${this.currentPage === 1 ? 'disabled' : ''} style="padding: 0.35rem 0.625rem; font-size: 0.8rem; cursor: pointer;">
        Anterior
      </button>
    `;

    let start = Math.max(1, this.currentPage - 2);
    let end = Math.min(totalPages, start + 4);
    if (end - start < 4) start = Math.max(1, end - 4);

    for (let i = start; i <= end; i++) {
      const active = i === this.currentPage;
      html += `
        <button class="btn btn-sm page-btn ${active ? 'btn-primary' : 'btn-secondary'}" data-page="${i}" style="padding: 0.35rem 0.625rem; font-size: 0.8rem; cursor: pointer; ${active ? 'font-weight:700;' : ''}">
          ${i}
        </button>
      `;
    }

    html += `
      <button class="btn btn-secondary btn-sm page-btn" data-page="${this.currentPage + 1}" ${this.currentPage === totalPages ? 'disabled' : ''} style="padding: 0.35rem 0.625rem; font-size: 0.8rem; cursor: pointer;">
        Siguiente
      </button>
    `;

    container.innerHTML = html;
  }

  goToPage(page) {
    this.currentPage = page;
    this.loadData();
  }

  async resolver(id) {
    const confirmed = await confirmModal('¿Marcar esta denuncia como resuelta?', { title: 'Resolver denuncia' });
    if (!confirmed) return;

    try {
      await this.api.post(`/reportes/${id}/resolver`);
      showToast('Denuncia marcada como resuelta correctamente', 'success');
      await this.loadData();
    } catch (err) {
      console.error('Error al resolver la denuncia:', err);
      showToast('Error al resolver: ' + (err.message || 'Error del servidor'), 'danger');
    }
  }

  async exportCSV() {
    try {
      const res = await this.api.get('/reportes', { limit: 1000, ...this.filters });
      const itemsToExport = res.data || res.items || res || [];

      if (!itemsToExport.length) {
        showToast('No hay datos disponibles para exportar', 'warning');
        return;
      }

      const headers = ['ID', 'Motivo', 'Detalle', 'Inmueble ID', 'Fecha', 'Denunciante', 'Estado'];
      const rows = itemsToExport.map(r => [
        r.id || r._id || '',
        r.motivo || '',
        r.detalle || '',
        r.listingId || r.inmuebleId || '',
        r.creado || r.createdAt || '',
        r.reporterNombre || r.reporterEmail || r.reporterId || '',
        r.estado || ''
      ]);

      const csvContent = [headers, ...rows]
        .map(row => row.map(cell => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
        .join('\n');

      const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `reportes_${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      
      showToast('Exportación a CSV generada con éxito', 'success');
    } catch (err) {
      console.error('Error al exportar CSV:', err);
      showToast('Error al exportar los datos', 'danger');
    }
  }

  showError(msg) {
    const tbody = this.container.querySelector('#reportsBody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding:32px; color:var(--danger, #ef4444); font-weight:500;">${msg}</td></tr>`;
    }
  }

  escape(str) {
    if (!str) return '-';
    return String(str).replace(/[&<>"']/g, c => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    }[c]));
  }

  destroy() {
    this.container = null;
  }
}