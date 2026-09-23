// ==========================================================================
// AlkilApp Admin - Usuarios Component
// ==========================================================================

import { debounce } from '../utils/helpers.js';

const TIPO_LABELS = { dueno: 'Propietario', inquilino: 'Inquilino', ambos: 'Ambos' };
const TRUST_LABELS = { nuevo: 'Nuevo', basic: 'Básico', verified: 'Verificado', premium: 'Premium' };

export default class Usuarios {
  constructor(api) {
    this.api = api;
    this.currentPage = 1;
    this.limit = 20;
    this.filters = { q: '', tipo: '', verif: '', trust: '' };
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
          <h1 class="page-title">Usuarios</h1>
          <p class="page-subtitle">Gestiona los usuarios registrados en la plataforma</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="exportBtn"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg> Exportar CSV</button>
        </div>
      </header>

      <div class="filters-bar">
        <div class="filter-group" style="flex:1;min-width:280px">
          <label class="filter-label">Buscar</label>
          <input type="search" id="searchInput" class="form-input" placeholder="Nombre, email, UID..." value="${this.escape(this.filters.q)}">
        </div>
        <div class="filter-group">
          <label class="filter-label">Tipo</label>
          <select id="tipoFilter" class="form-select">
            <option value="">Todos</option>
            <option value="dueno"${this.filters.tipo === 'dueno' ? ' selected' : ''}>Propietarios</option>
            <option value="inquilino"${this.filters.tipo === 'inquilino' ? ' selected' : ''}>Inquilinos</option>
            <option value="ambos"${this.filters.tipo === 'ambos' ? ' selected' : ''}>Ambos</option>
          </select>
        </div>
        <div class="filter-group">
          <label class="filter-label">Verificación</label>
          <select id="verifFilter" class="form-select">
            <option value="">Todos</option>
            <option value="verificado"${this.filters.verif === 'verificado' ? ' selected' : ''}>Verificados</option>
            <option value="pendiente"${this.filters.verif === 'pendiente' ? ' selected' : ''}>En revisión</option>
            <option value="sin_verificar"${this.filters.verif === 'sin_verificar' ? ' selected' : ''}>Sin verificar</option>
          </select>
        </div>
        <div class="filter-group">
          <label class="filter-label">Confianza</label>
          <select id="trustFilter" class="form-select">
            <option value="">Todos</option>
            <option value="nuevo"${this.filters.trust === 'nuevo' ? ' selected' : ''}>Nuevo</option>
            <option value="basic"${this.filters.trust === 'basic' ? ' selected' : ''}>Básico</option>
            <option value="verified"${this.filters.trust === 'verified' ? ' selected' : ''}>Verificado</option>
            <option value="premium"${this.filters.trust === 'premium' ? ' selected' : ''}>Premium</option>
          </select>
        </div>
        <div class="filter-actions">
          <button class="btn btn-secondary" id="clearFilters" style="display:${this.filters.q || this.filters.tipo || this.filters.verif || this.filters.trust ? 'inline-flex' : 'none'}">Limpiar</button>
        </div>
      </div>

      <div class="card">
        <div class="table-container">
          <table class="table" id="usersTable">
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Tipo</th>
                <th>Identidad</th>
                <th>Confianza</th>
                <th>Alta</th>
                <th>Rating</th>
              </tr>
            </thead>
            <tbody id="usersBody"></tbody>
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

    document.getElementById('tipoFilter')?.addEventListener('change', e => {
      this.filters.tipo = e.target.value;
      this.currentPage = 1;
      this.loadData();
    });

    document.getElementById('verifFilter')?.addEventListener('change', e => {
      this.filters.verif = e.target.value;
      this.currentPage = 1;
      this.loadData();
    });

    document.getElementById('trustFilter')?.addEventListener('change', e => {
      this.filters.trust = e.target.value;
      this.currentPage = 1;
      this.loadData();
    });

    document.getElementById('clearFilters')?.addEventListener('click', () => {
      this.filters = { q: '', tipo: '', verif: '', trust: '' };
      
      const search = document.getElementById('searchInput');
      const tipo = document.getElementById('tipoFilter');
      const verif = document.getElementById('verifFilter');
      const trust = document.getElementById('trustFilter');
      
      if (search) search.value = '';
      if (tipo) tipo.value = '';
      if (verif) verif.value = '';
      if (trust) trust.value = '';
      
      this.currentPage = 1;
      this.loadData();
    });

    document.getElementById('exportBtn')?.addEventListener('click', () => this.exportCSV());

    document.getElementById('pagination')?.addEventListener('click', e => {
      const btn = e.target.closest('.page-btn');
      if (btn && !btn.disabled) this.goToPage(parseInt(btn.dataset.page, 10));
    });
  }

  async loadData() {
    try {
      const params = { 
        q: this.filters.q, 
        tipo: this.filters.tipo, 
        verif: this.filters.verif, 
        trust: this.filters.trust, 
        page: this.currentPage, 
        limit: this.limit 
      };
      const res = await this.api.get('/usuarios', params);
      this.renderTable(res.data || []);
      this.renderPagination(res.total || 0, this.currentPage, res.limit || this.limit, res.totalPages || 1);

      const clearBtn = document.getElementById('clearFilters');
      if (clearBtn) {
        clearBtn.style.display = (this.filters.q || this.filters.tipo || this.filters.verif || this.filters.trust) ? 'inline-flex' : 'none';
      }
    } catch (err) {
      console.error('Error cargando usuarios:', err);
      this.showError('Error al cargar usuarios');
    }
  }

  renderTable(items) {
    const tbody = document.getElementById('usersBody');
    if (!tbody) return;

    if (!items || !items.length) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:32px;color:var(--text-muted)">Sin usuarios</td></tr>`;
      return;
    }

    tbody.innerHTML = items.map(u => {
      return `
        <tr>
          <td>
            <strong>${this.escape(u.nombre || '(sin nombre)')}</strong>
            <div class="cell-secondary">${this.escape(u.email || u.id)}</div>
          </td>
          <td>${this.escape(TIPO_LABELS[u.tipoUsuario || u.role] || u.tipoUsuario || u.role || '-')}</td>
          <td>
            ${u.verificado ? '<span class="pill ok">verificado</span>' : '<span class="pill grey">sin verificar</span>'}
            ${u.estadoVerif === 'pendiente' ? ' <span class="pill pend">en revisión</span>' : ''}
          </td>
          <td><span class="pill ${this.getTrustPill(u.trustLevel)}">${this.escape(TRUST_LABELS[u.trustLevel] || u.trustLevel || 'nuevo')}</span></td>
          <td class="cell-secondary">${this.escape(u.creado || '-')}</td>
          <td>${u.rating != null ? u.rating : '-'}</td>
        </tr>
      `;
    }).join('');
  }

  getTrustPill(level) {
    const pills = { nuevo: 'grey', basic: 'pend', verified: 'ok', premium: 'gold' };
    return pills[level] || 'grey';
  }

  renderPagination(total, page, limit, totalPages) {
    const container = document.getElementById('pagination');
    if (!container) return;
    if (totalPages <= 1) { container.innerHTML = ''; return; }

    let html = '';
    html += `<button class="page-btn" data-page="${this.currentPage - 1}" ${this.currentPage === 1 ? 'disabled' : ''} aria-label="Anterior"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg></button>`;

    let start = Math.max(1, this.currentPage - 2);
    let end = Math.min(totalPages, start + 4);
    if (end - start < 4) start = Math.max(1, end - 4);

    for (let i = start; i <= end; i++) {
      html += `<button class="page-btn${i === this.currentPage ? ' active' : ''}" data-page="${i}">${i}</button>`;
    }

    html += `<button class="page-btn" data-page="${this.currentPage + 1}" ${this.currentPage === totalPages ? 'disabled' : ''} aria-label="Siguiente"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg></button>`;

    container.innerHTML = html;
  }

  goToPage(page) {
    this.currentPage = page;
    this.loadData();
  }

  showError(msg) {
    const tbody = document.getElementById('usersBody');
    if (tbody) tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:32px;color:var(--danger)">${this.escape(msg)}</td></tr>`;
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

  exportCSV() {
    // Método stub para exportación
  }

  destroy() {}
}