// ==========================================================================
// AlkilApp Admin - Soporte e Incidentes Component
// ==========================================================================

import { debounce, confirmModal, showToast, formatDate } from '../utils/helpers.js';

// Estados del ciclo de vida de un ticket de soporte
const ESTADO_LABELS = {
  pendiente: 'Pendiente',
  en_proceso: 'En proceso',
  atendido: 'Atendido',
  resuelto: 'Resuelto',
  cerrado: 'Cerrado',
};

// Estados que ofrece el selector de cambio de estado
const ESTADOS_CAMBIO = ['pendiente', 'en_proceso', 'atendido', 'resuelto', 'cerrado'];

export default class Soporte {
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
          <h1 class="page-title" style="font-size: 1.5rem; font-weight: 700; color: var(--text-color, #0f172a); margin: 0 0 0.25rem 0;">Soporte e Incidentes</h1>
          <p class="page-subtitle" style="font-size: 0.875rem; color: var(--text-muted, #64748b); margin: 0;">Tickets enviados desde la app y su estado de atención</p>
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
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Buscar ticket</label>
          <div style="position: relative;">
            <input type="search" id="searchInput" class="form-input" placeholder="Asunto, mensaje, correo, usuario..." value="${this.filters.q}" style="width: 100%; padding: 0.5rem 0.75rem 0.5rem 2.25rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.5rem; font-size: 0.875rem; outline: none;">
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
            ${ESTADOS_CAMBIO.map(e => `<option value="${e}" ${this.filters.estado === e ? 'selected' : ''}>${ESTADO_LABELS[e]}</option>`).join('')}
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
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Asunto / Mensaje</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Contacto</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Fecha</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Estado</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600; text-align: center;">Acciones</th>
              </tr>
            </thead>
            <tbody id="soporteBody">
              <tr>
                <td colspan="5" style="padding: 3rem; text-align: center; color: var(--text-muted, #64748b);">
                  <div style="display: flex; flex-direction: column; align-items: center; gap: 0.5rem;">
                    <div class="spinner" style="width: 24px; height: 24px; border: 2px solid #cbd5e1; border-top-color: #3b82f6; border-radius: 50%; animation: spin 0.8s linear infinite;"></div>
                    <span>Cargando tickets...</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Paginación -->
        <div class="pagination-container" style="display: flex; justify-content: space-between; align-items: center; padding: 0.875rem 1.25rem; border-top: 1px solid var(--border-color, #e2e8f0); background-color: var(--card-bg, #ffffff);">
          <span id="paginationInfo" style="font-size: 0.85rem; color: var(--text-muted, #64748b);">Mostrando 0 - 0 de 0 tickets</span>
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

    this.container.querySelector('#refreshBtn')?.addEventListener('click', () => this.loadData());
    this.container.querySelector('#exportBtn')?.addEventListener('click', () => this.exportCSV());

    // Delegación de eventos en la tabla
    this.container.querySelector('#soporteBody')?.addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-action]');
      if (!btn) return;
      const row = btn.closest('tr');
      const id = row?.dataset.id;
      if (!id) return;

      if (btn.dataset.action === 'ver') this.verTicket(id);
      if (btn.dataset.action === 'cambiar') this.cambiarEstado(id);
    });

    // Eventos de paginación
    this.container.querySelector('#pagination')?.addEventListener('click', (e) => {
      const btn = e.target.closest('.page-btn');
      if (btn && !btn.disabled) this.goToPage(parseInt(btn.dataset.page, 10));
    });
  }

  async loadData() {
    try {
      const params = new URLSearchParams({
        page: this.currentPage,
        limit: this.limit,
        ...(this.filters.q && { q: this.filters.q }),
        ...(this.filters.estado && { estado: this.filters.estado }),
      }).toString();

      const res = await this.api.get(`/soporte?${params}`);
      this.items = res.data || res.tickets || res || [];
      this.total = res.total || this.items.length;
      const totalPages = res.totalPages || Math.ceil(this.total / this.limit) || 1;

      this.renderTable();
      this.renderPagination(totalPages);
    } catch (err) {
      console.error('Error al cargar tickets de soporte:', err);
      this.showError('Error al cargar los tickets de soporte.');
    }
  }

  renderTable() {
    const tbody = this.container.querySelector('#soporteBody');
    if (!tbody) return;

    if (!this.items.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" style="padding: 3.5rem 1rem; text-align: center; color: var(--text-muted, #64748b);">
            <div style="display: flex; flex-direction: column; align-items: center; gap: 0.75rem;">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="width: 42px; height: 42px; color: #94a3b8;">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
              <span style="font-weight: 500; font-size: 0.95rem;">No se encontraron tickets de soporte.</span>
            </div>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = this.items.map(t => {
      const id = t.id || t._id;
      const estado = (t.estado || 'pendiente').toLowerCase();
      const fechaFormateada = formatDate ? formatDate(t.fechaCreacion || t.creado) : (t.creado || '-');
      const mensaje = t.mensaje || '';

      return `
        <tr data-id="${id}" style="border-bottom: 1px solid var(--border-color, #f1f5f9); vertical-align: middle; transition: background-color 0.15s ease;" onmouseover="this.style.backgroundColor='var(--hover-bg, #f8fafc)'" onmouseout="this.style.backgroundColor='transparent'">

          <!-- Asunto y Mensaje -->
          <td style="padding: 0.875rem 1rem;">
            <strong style="display: block; font-size: 0.875rem; font-weight: 600; color: var(--text-color, #0f172a);">${this.escape(t.asunto || 'Sin asunto')}</strong>
            <div style="font-size: 0.775rem; color: var(--text-muted, #64748b); margin-top: 2px; max-width: 340px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${this.escape(mensaje)}">${this.escape(mensaje)}</div>
          </td>

          <!-- Contacto -->
          <td style="padding: 0.875rem 1rem; font-size: 0.8rem; color: var(--text-muted, #475569);">
            <div style="font-weight: 500; color: var(--text-color, #0f172a);">${this.escape(t.emailContacto || '-')}</div>
            <code style="background: #f1f5f9; padding: 0.1rem 0.3rem; border-radius: 0.25rem; font-size: 0.7rem; color: #334155;">${this.escape((t.usuarioId || '-').slice(0, 14))}</code>
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
          <td style="padding: 0.875rem 1rem; text-align: center; white-space: nowrap;">
            <button class="btn btn-sm btn-secondary" data-action="ver" style="display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.35rem 0.6rem; font-size: 0.75rem; border-radius: 0.375rem; cursor: pointer; margin-right: 0.35rem;" title="Ver ticket completo">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              Ver
            </button>
            <button class="btn btn-sm btn-primary" data-action="cambiar" style="display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.35rem 0.6rem; font-size: 0.75rem; border-radius: 0.375rem; cursor: pointer;" title="Cambiar estado">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
              Estado
            </button>
          </td>

        </tr>
      `;
    }).join('');
  }

  getBadgeStyle(estado) {
    switch (estado) {
      case 'resuelto':
      case 'cerrado':
        return 'background-color: #ecfdf5; color: #047857; border: 1px solid #a7f3d0;';
      case 'en_proceso':
      case 'atendido':
        return 'background-color: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe;';
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
      info.textContent = `Mostrando ${this.total > 0 ? start : 0} - ${end} de ${this.total} tickets`;
    }

    const container = this.container.querySelector('#pagination');
    if (!container) return;
    if (totalPages <= 1) { container.innerHTML = ''; return; }

    let html = `
      <button class="btn btn-secondary btn-sm page-btn" data-page="${this.currentPage - 1}" ${this.currentPage === 1 ? 'disabled' : ''} style="padding: 0.35rem 0.625rem; font-size: 0.8rem; cursor: pointer;">Anterior</button>
    `;

    let start = Math.max(1, this.currentPage - 2);
    let end = Math.min(totalPages, start + 4);
    if (end - start < 4) start = Math.max(1, end - 4);

    for (let i = start; i <= end; i++) {
      const active = i === this.currentPage;
      html += `
        <button class="btn btn-sm page-btn ${active ? 'btn-primary' : 'btn-secondary'}" data-page="${i}" style="padding: 0.35rem 0.625rem; font-size: 0.8rem; cursor: pointer; ${active ? 'font-weight:700;' : ''}">${i}</button>
      `;
    }

    html += `
      <button class="btn btn-secondary btn-sm page-btn" data-page="${this.currentPage + 1}" ${this.currentPage === totalPages ? 'disabled' : ''} style="padding: 0.35rem 0.625rem; font-size: 0.8rem; cursor: pointer;">Siguiente</button>
    `;

    container.innerHTML = html;
  }

  goToPage(page) {
    this.currentPage = page;
    this.loadData();
  }

  // Modal con el ticket completo (asunto, mensaje, contacto, usuario, fecha, estado)
  verTicket(id) {
    const t = this.items.find(x => (x.id || x._id) === id);
    if (!t) return;
    const estado = (t.estado || 'pendiente').toLowerCase();
    const fecha = formatDate ? formatDate(t.fechaCreacion || t.creado) : (t.creado || '-');

    const html = `
      <div style="padding: 1.5rem;">
        <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; margin-bottom: 1rem;">
          <div>
            <h3 style="margin: 0 0 0.35rem 0; font-size: 1.125rem; font-weight: 700; color: var(--text-color, #0f172a);">${this.escape(t.asunto || 'Sin asunto')}</h3>
            <span style="padding: 0.2rem 0.6rem; border-radius: 9999px; font-weight: 600; font-size: 0.7rem; ${this.getBadgeStyle(estado)}">${(ESTADO_LABELS[estado] || estado).toUpperCase()}</span>
          </div>
        </div>

        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 0.75rem; margin-bottom: 1rem; font-size: 0.8rem;">
          <div style="background: var(--table-header-bg, #f8fafc); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.5rem; padding: 0.6rem 0.75rem;">
            <div style="font-size: 0.65rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); font-weight: 700;">Email de contacto</div>
            <div style="margin-top: 0.2rem; color: var(--text-color, #0f172a); word-break: break-all;">${this.escape(t.emailContacto || '-')}</div>
          </div>
          <div style="background: var(--table-header-bg, #f8fafc); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.5rem; padding: 0.6rem 0.75rem;">
            <div style="font-size: 0.65rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); font-weight: 700;">ID Usuario</div>
            <div style="margin-top: 0.2rem; font-family: monospace; font-size: 0.75rem; color: var(--text-color, #0f172a); word-break: break-all;">${this.escape(t.usuarioId || '-')}</div>
          </div>
          <div style="background: var(--table-header-bg, #f8fafc); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.5rem; padding: 0.6rem 0.75rem;">
            <div style="font-size: 0.65rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); font-weight: 700;">Fecha de creación</div>
            <div style="margin-top: 0.2rem; color: var(--text-color, #0f172a);">${fecha}</div>
          </div>
          <div style="background: var(--table-header-bg, #f8fafc); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.5rem; padding: 0.6rem 0.75rem;">
            <div style="font-size: 0.65rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); font-weight: 700;">Ticket ID</div>
            <div style="margin-top: 0.2rem; font-family: monospace; font-size: 0.75rem; color: var(--text-color, #0f172a); word-break: break-all;">${this.escape(t.id || '-')}</div>
          </div>
        </div>

        <div style="font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); font-weight: 700; margin-bottom: 0.35rem;">Mensaje</div>
        <div style="background: var(--table-header-bg, #f8fafc); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.5rem; padding: 0.875rem; font-size: 0.875rem; line-height: 1.6; color: var(--text-color, #0f172a); white-space: pre-wrap; word-break: break-word; max-height: 260px; overflow-y: auto;">${this.escape(t.mensaje || '(sin mensaje)')}</div>

        <div style="display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 1.25rem;">
          <button class="btn btn-secondary" id="modalCerrarBtn" style="padding: 0.5rem 1rem; border-radius: 0.5rem; cursor: pointer;">Cerrar</button>
        </div>
      </div>
    `;

    const root = document.getElementById('modalRoot');
    root.innerHTML = `
      <div id="ticketModal" style="position: fixed; inset: 0; background: rgba(15,23,42,0.55); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 1rem;">
        <div style="background: var(--card-bg, #fff); border-radius: 0.75rem; max-width: 640px; width: 100%; max-height: 85vh; overflow-y: auto; box-shadow: 0 20px 50px rgba(0,0,0,0.3);">
          ${html}
        </div>
      </div>
    `;

    const cerrar = () => { root.innerHTML = ''; };
    root.querySelector('#modalCerrarBtn')?.addEventListener('click', cerrar);
    root.querySelector('#ticketModal')?.addEventListener('click', (e) => {
      if (e.target.id === 'ticketModal') cerrar();
    });
  }

  // Cambiar estado con selector de opciones (PATCH /api/soporte/:id/estado)
  async cambiarEstado(id) {
    const t = this.items.find(x => (x.id || x._id) === id);
    if (!t) return;
    const actual = (t.estado || 'pendiente').toLowerCase();

    const html = `
      <div style="padding: 1.5rem;">
        <h3 style="margin: 0 0 0.35rem 0; font-size: 1.125rem; font-weight: 700; color: var(--text-color, #0f172a);">Cambiar estado del ticket</h3>
        <p style="margin: 0 0 1rem 0; font-size: 0.875rem; color: var(--text-muted, #64748b);">Ticket: <strong>${this.escape(t.asunto || '(sin asunto)')}</strong></p>

        <label style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Nuevo estado</label>
        <select id="nuevoEstado" class="form-select" style="width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.5rem; font-size: 0.875rem; background-color: var(--card-bg, #fff); margin-bottom: 1rem;">
          ${ESTADOS_CAMBIO.map(e => `<option value="${e}" ${actual === e ? 'selected' : ''}>${ESTADO_LABELS[e]}</option>`).join('')}
        </select>

        <div style="display: flex; justify-content: flex-end; gap: 0.5rem;">
          <button class="btn btn-secondary" id="estadoCancelarBtn" style="padding: 0.5rem 1rem; border-radius: 0.5rem; cursor: pointer;">Cancelar</button>
          <button class="btn btn-primary" id="estadoGuardarBtn" style="padding: 0.5rem 1rem; border-radius: 0.5rem; cursor: pointer;">Guardar</button>
        </div>
      </div>
    `;

    const root = document.getElementById('modalRoot');
    root.innerHTML = `
      <div id="estadoModal" style="position: fixed; inset: 0; background: rgba(15,23,42,0.55); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 1rem;">
        <div style="background: var(--card-bg, #fff); border-radius: 0.75rem; max-width: 440px; width: 100%; box-shadow: 0 20px 50px rgba(0,0,0,0.3);">
          ${html}
        </div>
      </div>
    `;

    const cerrar = () => { root.innerHTML = ''; };
    root.querySelector('#estadoCancelarBtn')?.addEventListener('click', cerrar);
    root.querySelector('#estadoModal')?.addEventListener('click', (e) => {
      if (e.target.id === 'estadoModal') cerrar();
    });
    root.querySelector('#estadoGuardarBtn')?.addEventListener('click', async () => {
      const nuevo = root.querySelector('#nuevoEstado')?.value;
      if (!nuevo || nuevo === actual) { cerrar(); return; }
      try {
        await this.api.patch(`/soporte/${encodeURIComponent(id)}/estado`, { estado: nuevo });
        showToast(`Ticket actualizado a "${ESTADO_LABELS[nuevo]}"`, 'success');
        cerrar();
        await this.loadData();
      } catch (err) {
        console.error('Error al cambiar estado:', err);
        showToast('Error al cambiar estado: ' + (err.message || 'Error del servidor'), 'danger');
      }
    });
  }

  async exportCSV() {
    try {
      const res = await this.api.get('/soporte', { limit: 1000, ...this.filters });
      const items = res.data || res.tickets || res || [];

      if (!items.length) {
        showToast('No hay datos disponibles para exportar', 'warning');
        return;
      }

      const headers = ['ID', 'Asunto', 'Mensaje', 'Email Contacto', 'ID Usuario', 'Estado', 'Fecha'];
      const rows = items.map(t => [
        t.id || '',
        t.asunto || '',
        t.mensaje || '',
        t.emailContacto || '',
        t.usuarioId || '',
        t.estado || 'pendiente',
        t.fechaCreacion || t.creado || '',
      ]);

      const csvContent = [headers, ...rows]
        .map(row => row.map(cell => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
        .join('\n');

      const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `soporte_${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);

      showToast('Exportación a CSV generada con éxito', 'success');
    } catch (err) {
      console.error('Error al exportar CSV:', err);
      showToast('Error al exportar los datos', 'danger');
    }
  }

  showError(msg) {
    const tbody = this.container.querySelector('#soporteBody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align:center; padding:32px; color:var(--danger, #ef4444); font-weight:500;">${msg}</td></tr>`;
    }
  }

  escape(str) {
    if (!str) return '-';
    return String(str).replace(/[&<>"']/g, c => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;',
    }[c]));
  }

  destroy() {
    const root = document.getElementById('modalRoot');
    if (root) root.innerHTML = '';
    this.container = null;
  }
}
