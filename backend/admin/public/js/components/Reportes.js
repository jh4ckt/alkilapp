// ==========================================================================
// AlkilApp Admin - Reportes Component
// ==========================================================================

import { debounce, confirmModal, showToast } from '../utils/helpers.js';

const ESTADO_LABELS = { pendiente: 'Pendiente', resuelto: 'Resuelto' };
const ESTADO_PILLS = { pendiente: 'pend', resuelto: 'ok' };

export default class Reportes {
  constructor(api) {
    this.api = api;
    this.currentPage = 1;
    this.limit = 20;
    this.filters = { q: '', estado: '' };
  }

  async render(container) {
    this.container = container;
    container.innerHTML = this.getTemplate();
    this.bindEvents();
    await this.loadData();
  }

  getTemplate() {
    return `
      <header class="page-header">
        <div>
          <h1 class="page-title">Denuncias</h1>
          <p class="page-subtitle">Gestiona los reportes de usuarios sobre inmuebles</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="exportBtn"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg> Exportar CSV</button>
        </div>
      </header>

      <div class="filters-bar">
        <div class="filter-group" style="flex:1;min-width:280px">
          <label class="filter-label">Buscar</label>
          <input type="search" id="searchInput" class="form-input" placeholder="Motivo, inmueble, denunciante..." value="${this.filters.q}">
        </div>
        <div class="filter-group">
          <label class="filter-label">Estado</label>
          <select id="estadoFilter" class="form-select">
            <option value="">Todos</option>
            <option value="pendiente"${this.filters.estado === 'pendiente' ? ' selected' : ''}>Pendientes</option>
            <option value="resuelto"${this.filters.estado === 'resuelto' ? ' selected' : ''}>Resueltos</option>
          </select>
        </div>
        <div class="filter-actions">
          <button class="btn btn-secondary" id="clearFilters" style="display:${this.filters.q || this.filters.estado ? 'inline-flex' : 'none'}">Limpiar</button>
        </div>
      </div>

      <div class="card">
        <div class="table-container">
          <table class="table" id="reportsTable">
            <thead>
              <tr>
                <th>Motivo</th>
                <th>Inmueble</th>
                <th>Creado</th>
                <th>Denunciante</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody id="reportsBody"></tbody>
          </table>
        </div>
        <div class="card-footer">
          <div class="pagination" id="pagination"></div>
        </div>
      </div>
    `;
  }

  bindEvents() {
    const searchInput = document.getElementById('searchInput');
    searchInput?.addEventListener('input', debounce(() => {
      this.filters.q = searchInput.value.trim();
      this.currentPage = 1;
      this.loadData();
    }, 300));

    document.getElementById('estadoFilter')?.addEventListener('change', e => {
      this.filters.estado = e.target.value;
      this.currentPage = 1;
      this.loadData();
    });

    document.getElementById('clearFilters')?.addEventListener('click', () => {
      this.filters = { q: '', estado: '' };
      const sInput = document.getElementById('searchInput');
      const eFilter = document.getElementById('estadoFilter');
      if (sInput) sInput.value = '';
      if (eFilter) eFilter.value = '';
      this.currentPage = 1;
      this.loadData();
    });

    document.getElementById('exportBtn')?.addEventListener('click', () => this.exportCSV());

    document.getElementById('reportsBody')?.addEventListener('click', e => {
      const btn = e.target.closest('button[data-action]');
      if (!btn) return;
      const row = btn.closest('tr');
      const id = row?.dataset.id;
      if (!id) return;
      if (btn.dataset.action === 'resolver') this.resolver(id);
    });

    document.getElementById('pagination')?.addEventListener('click', e => {
      const btn = e.target.closest('.page-btn');
      if (btn && !btn.disabled) this.goToPage(parseInt(btn.dataset.page, 10));
    });
  }

  async loadData() {
    try {
      const params = { q: this.filters.q, estado: this.filters.estado, page: this.currentPage, limit: this.limit };
      const res = await this.api.get('/reportes', params);
      this.renderTable(res.data || []);
      this.renderPagination(res.total || 0, res.page || 1, res.limit || this.limit, res.totalPages || 1);
      
      const clearBtn = document.getElementById('clearFilters');
      if (clearBtn) {
        clearBtn.style.display = (this.filters.q || this.filters.estado) ? 'inline-flex' : 'none';
      }
    } catch (err) {
      console.error('Error cargando reportes:', err);
      this.showError('Error al cargar denuncias');
    }
  }

  renderTable(items) {
    const tbody = document.getElementById('reportsBody');
    if (!tbody) return;

    if (!items.length) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:32px;color:var(--text-muted)">Sin denuncias</td></tr>`;
      return;
    }

    tbody.innerHTML = items.map(r => `
      <tr data-id="${r.id}">
        <td>
          <strong>${this.escape(r.motivo || '-')}</strong>
          <div class="cell-secondary">${this.escape(r.detalle || '')}</div>
        </td>
        <td>${this.escape(r.listingId || '-')}</td>
        <td class="cell-secondary">${r.creado || '-'}</td>
        <td class="cell-secondary">${this.escape(r.reporterId || '-')}</td>
        <td><span class="pill ${ESTADO_PILLS[r.estado] || 'pend'}">${ESTADO_LABELS[r.estado] || r.estado}</span></td>
        <td>
          ${r.estado === 'pendiente' ? `<button class="btn btn-sm btn-primary" data-action="resolver" title="Marcar como resuelto"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg></button>` : ''}
        </td>
      </tr>
    `).join('');
  }

  renderPagination(total, page, limit, totalPages) {
    const container = document.getElementById('pagination');
    if (!container) return;
    if (totalPages <= 1) { container.innerHTML = ''; return; }

    let html = '';
    html += `<button class="page-btn" data-page="${page - 1}" ${page === 1 ? 'disabled' : ''} aria-label="Anterior"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg></button>`;

    let start = Math.max(1, page - 2);
    let end = Math.min(totalPages, start + 4);
    if (end - start < 4) start = Math.max(1, end - 4);

    for (let i = start; i <= end; i++) {
      html += `<button class="page-btn${i === page ? ' active' : ''}" data-page="${i}">${i}</button>`;
    }

    html += `<button class="page-btn" data-page="${page + 1}" ${page === totalPages ? 'disabled' : ''} aria-label="Siguiente"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg></button>`;

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
      showToast('Denuncia marcada como resuelta', 'success');
      this.loadData();
    } catch (err) {
      showToast('Error: ' + err.message, 'danger');
    }
  }

  async exportCSV() {
    try {
      const res = await this.api.get('/reportes', { limit: 1000, ...this.filters });
      const headers = ['ID', 'Motivo', 'Detalle', 'Inmueble', 'Creado', 'Denunciante', 'Estado'];
      const rows = (res.data || []).map(r => [r.id, r.motivo, r.detalle, r.listingId, r.creado, r.reporterId, r.estado]);
      const csv = [headers, ...rows].map(r => r.map(c => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',')).join('\n');
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `reportes_${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      showToast('Error al exportar', 'danger');
    }
  }

  showError(msg) {
    const tbody = document.getElementById('reportsBody');
    if (tbody) tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:32px;color:var(--danger)">${msg}</td></tr>`;
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

  destroy() {}
}
