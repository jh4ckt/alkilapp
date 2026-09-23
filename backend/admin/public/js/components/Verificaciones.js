// ==========================================================================
// AlkilApp Admin - Verificaciones Component
// ==========================================================================

import { formatDate, formatRelative, openModal, confirmModal, showToast } from '../utils/helpers.js';

const ESTADO_LABELS = { pendiente: 'Pendiente', aprobado: 'Aprobado', rechazado: 'Rechazado' };
const ESTADO_PILLS = { pendiente: 'pend', aprobado: 'ok', rechazado: 'bad' };

export default class Verificaciones {
  constructor(api) {
    this.api = api;
    this.currentPage = 1;
    this.limit = 20;
    this.filters = { q: '', estado: '' };
    this.sortColumn = 'creado';
    this.sortDirection = 'desc';
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
          <h1 class="page-title">Verificaciones de identidad</h1>
          <p class="page-subtitle">Gestiona la verificación de documentos de los usuarios</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="exportBtn"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg> Exportar CSV</button>
        </div>
      </header>

      <div class="filters-bar">
        <div class="filter-group" style="flex:1;min-width:280px">
          <label class="filter-label">Buscar</label>
          <input type="search" id="searchInput" class="form-input" placeholder="Email, nombre, documento..." value="${this.filters.q}">
        </div>
        <div class="filter-group">
          <label class="filter-label">Estado</label>
          <select id="estadoFilter" class="form-select">
            <option value="">Todos</option>
            <option value="pendiente"${this.filters.estado === 'pendiente' ? ' selected' : ''}>Pendientes</option>
            <option value="aprobado"${this.filters.estado === 'aprobado' ? ' selected' : ''}>Aprobadas</option>
            <option value="rechazado"${this.filters.estado === 'rechazado' ? ' selected' : ''}>Rechazadas</option>
          </select>
        </div>
        <div class="filter-actions">
          <button class="btn btn-secondary" id="clearFilters" style="display:${this.filters.q || this.filters.estado ? 'inline-flex' : 'none'}">Limpiar</button>
        </div>
      </div>

      <div class="card">
        <div class="table-container" id="tableContainer">
          <table class="table" id="verifTable">
            <thead>
              <tr>
                <th data-sort="nombre">Usuario</th>
                <th data-sort="email">Email</th>
                <th data-sort="tipoDocumento">Documento</th>
                <th data-sort="estado">Estado</th>
                <th data-sort="creado">Enviado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody id="verifBody"></tbody>
          </table>
        </div>
        <div class="card-footer">
          <div class="pagination" id="pagination"></div>
        </div>
      </div>
    `;
  }

  bindEvents() {
    // Search
    const searchInput = document.getElementById('searchInput');
    searchInput?.addEventListener('input', debounce(() => {
      this.filters.q = searchInput.value.trim();
      this.currentPage = 1;
      this.loadData();
    }, 300));

    // Estado filter
    document.getElementById('estadoFilter')?.addEventListener('change', e => {
      this.filters.estado = e.target.value;
      this.currentPage = 1;
      this.loadData();
    });

    // Clear filters
    document.getElementById('clearFilters')?.addEventListener('click', () => {
      this.filters = { q: '', estado: '' };
      if (searchInput) searchInput.value = '';
      const estadoFilter = document.getElementById('estadoFilter');
      if (estadoFilter) estadoFilter.value = '';
      this.currentPage = 1;
      this.loadData();
    });

    // Export
    document.getElementById('exportBtn')?.addEventListener('click', () => this.exportCSV());

    // Table sorting
    document.querySelectorAll('#verifTable th[data-sort]').forEach(th => {
      th.style.cursor = 'pointer';
      th.addEventListener('click', () => this.handleSort(th.dataset.sort));
    });

    // Table actions (delegation)
    document.getElementById('verifBody')?.addEventListener('click', e => {
      const btn = e.target.closest('button[data-action]');
      if (!btn) return;
      const row = btn.closest('tr');
      const id = row?.dataset.id;
      if (!id) return;
      const action = btn.dataset.action;
      if (action === 'ver') this.viewDocument(id);
      else if (action === 'aprobar') this.aprobar(id);
      else if (action === 'rechazar') this.rechazar(id);
    });

    // Pagination delegation
    document.getElementById('pagination')?.addEventListener('click', e => {
      const btn = e.target.closest('.page-btn');
      if (btn && !btn.disabled) this.goToPage(parseInt(btn.dataset.page, 10));
    });
  }

  async loadData() {
    try {
      const params = {
        q: this.filters.q,
        estado: this.filters.estado,
        page: this.currentPage,
        limit: this.limit,
      };
      const res = await this.api.get('/verificaciones', params);
      this.renderTable(res.data || []);
      this.renderPagination(res.total || 0, res.page || 1, res.limit || this.limit, res.totalPages || 1);
    } catch (err) {
      console.error('Error cargando verificaciones:', err);
      this.showError('Error al cargar verificaciones');
    }
  }

  renderTable(items) {
    const tbody = document.getElementById('verifBody');
    if (!tbody) return;

    if (!items.length) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:32px;color:var(--text-muted)">Sin solicitudes de verificación</td></tr>`;
      return;
    }

    tbody.innerHTML = items.map(v => {
      const pill = ESTADO_PILLS[v.estado] || 'grey';
      const estadoLabel = ESTADO_LABELS[v.estado] || v.estado;
      const acciones = v.estado === 'pendiente' ? `
        <button class="btn btn-sm btn-primary" data-action="aprobar" title="Aprobar"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg></button>
        <button class="btn btn-sm btn-danger" data-action="rechazar" title="Rechazar"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></button>
      ` : `
        <span class="muted">Sin acciones</span>
      `;

      return `
        <tr data-id="${v.id}">
          <td><strong>${this.escape(v.nombre || '(sin nombre)')}</strong></td>
          <td>${this.escape(v.email || '-')}</td>
          <td>${this.escape(v.tipoDocumento || '-')} ${this.escape(v.numeroDocumento || '')}</td>
          <td><span class="pill ${pill}">${estadoLabel}</span></td>
          <td class="cell-secondary">${v.creado ? formatDate(v.creado) : '-'}</td>
          <td class="cell-actions">${acciones}
            <button class="btn btn-sm btn-ghost" data-action="ver" title="Ver documentos"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg></button>
          </td>
        </tr>
      `;
    }).join('');
  }

  renderPagination(total, page, limit, totalPages) {
    const container = document.getElementById('pagination');
    if (!container) return;

    if (totalPages <= 1) { 
      container.innerHTML = ''; 
      return; 
    }

    let html = '';
    // Prev
    html += `<button class="page-btn" data-page="${page - 1}" ${page === 1 ? 'disabled' : ''} aria-label="Anterior"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg></button>`;

    // Page numbers
    const maxVisible = 5;
    let start = Math.max(1, page - Math.floor(maxVisible / 2));
    let end = Math.min(totalPages, start + maxVisible - 1);
    if (end - start + 1 < maxVisible) start = Math.max(1, end - maxVisible + 1);

    for (let i = start; i <= end; i++) {
      html += `<button class="page-btn${i === page ? ' active' : ''}" data-page="${i}">${i}</button>`;
    }

    // Next
    html += `<button class="page-btn" data-page="${page + 1}" ${page === totalPages ? 'disabled' : ''} aria-label="Siguiente"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg></button>`;

    container.innerHTML = html;
  }

  goToPage(page) {
    this.currentPage = page;
    this.loadData();
  }

  handleSort(column) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
    this.loadData();
  }

async viewDocument(id) {
    try {
      // Usar cliente this.api para incluir autenticación y la ruta correcta
      const v = await this.api.get(`/verificaciones/${id}`);

      const html = `
        <div style="display:grid;gap:16px">
          <div><strong>Usuario:</strong> ${this.escape(v.nombre)} (${this.escape(v.email)})</div>
          <div><strong>Documento:</strong> ${this.escape(v.tipoDocumento)} ${this.escape(v.numeroDocumento)}</div>
          <div><strong>Estado:</strong> <span class="pill ${ESTADO_PILLS[v.estado]}">${ESTADO_LABELS[v.estado] || v.estado}</span></div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
            ${v.imagen ? `<div><strong>Anverso</strong><br><img src="data:image/jpeg;base64,${v.imagen}" alt="Anverso" style="max-width:100%;border-radius:8px;border:1px solid var(--border)"></div>` : '<div><strong>Anverso</strong><br><span class="muted">Sin imagen</span></div>'}
            ${v.imagenReverso ? `<div><strong>Reverso</strong><br><img src="data:image/jpeg;base64,${v.imagenReverso}" alt="Reverso" style="max-width:100%;border-radius:8px;border:1px solid var(--border)"></div>` : '<div><strong>Reverso</strong><br><span class="muted">Sin imagen</span></div>'}
          </div>
          ${v.motivo ? `<div><strong>Motivo:</strong> ${this.escape(v.motivo)}</div>` : ''}
        </div>
      `;

      const footer = v.estado === 'pendiente' ? `
        <button class="btn btn-secondary" data-dismiss="false">Cerrar</button>
        <button class="btn btn-primary" data-action="aprobar">Aprobar</button>
        <button class="btn btn-danger" data-action="rechazar">Rechazar</button>
      ` : `<button class="btn btn-secondary" data-dismiss="false">Cerrar</button>`;

      await openModal(html, { title: 'Verificación', footer });
    } catch (err) {
      console.error(err);
      showToast('Error al obtener el documento: ' + err.message, 'danger');
    }
  }
  
  async aprobar(id) {
    const confirmed = await confirmModal(
      '¿Aprobar esta verificación? El usuario recibirá el badge "Verificado por AlkilApp".',
      { title: 'Aprobar verificación', confirmText: 'Aprobar' }
    );
    if (!confirmed) return;

    try {
      await this.api.post(`/verificaciones/${id}/aprobar`);
      showToast('Verificación aprobada', 'success');
      this.loadData();
    } catch (err) {
      showToast('Error al aprobar: ' + err.message, 'danger');
    }
  }

  async rechazar(id) {
    const motivo = prompt('Motivo del rechazo (obligatorio):');
    if (!motivo) return;

    const confirmed = await confirmModal(
      `¿Rechazar esta verificación? Motivo: "${motivo}"`,
      { title: 'Rechazar verificación', confirmText: 'Rechazar', danger: true }
    );
    if (!confirmed) return;

    try {
      await this.api.post(`/verificaciones/${id}/rechazar`, { motivo });
      showToast('Verificación rechazada', 'success');
      this.loadData();
    } catch (err) {
      showToast('Error al rechazar: ' + err.message, 'danger');
    }
  }

  async exportCSV() {
    try {
      const res = await this.api.get('/verificaciones', { limit: 1000, ...this.filters });
      const headers = ['ID', 'Nombre', 'Email', 'Tipo Doc', 'Número Doc', 'Estado', 'Enviado'];
      const rows = (res.data || []).map(v => [
        v.id, v.nombre, v.email, v.tipoDocumento, v.numeroDocumento, v.estado, v.creado
      ]);
      const csv = [headers, ...rows].map(r => r.map(c => `"${String(c ?? '').replace(/"/g, '""')}"`).join(',')).join('\n');
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; 
      a.download = `verificaciones_${new Date().toISOString().slice(0, 10)}.csv`;
      a.click(); 
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error(err);
      showToast('Error al exportar', 'danger');
    }
  }

  showError(msg) {
    const container = document.getElementById('verifBody');
    if (container) container.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:32px;color:var(--danger)">${msg}</td></tr>`;
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

function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}