// ==========================================================================
// AlkilApp Admin - Publicaciones Component (Complete)
// ==========================================================================

import { formatCurrency } from '../utils/helpers.js';

const ESTADO_LABELS = { publicado: 'Publicado', disponible: 'Disponible', finalizado: 'Finalizado', under_review: 'En revisión', pendiente: 'Pendiente' };
const ESTADO_PILLS = { publicado: 'ok', disponible: 'ok', finalizado: 'grey', under_review: 'pend', pendiente: 'pend' };

export default class Publicaciones {
  constructor(api) {
    this.api = api;
    this.currentPage = 1;
    this.limit = 20;
    this.filters = { q: '', estado: '', destacado: '' };
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
          <h1 class="page-title">Publicaciones</h1>
          <p class="page-subtitle">Gestiona los inmuebles publicados en la plataforma</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="exportBtn"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg> Exportar CSV</button>
        </div>
      </header>

      <div class="filters-bar">
        <div class="filter-group" style="flex:1;min-width:280px">
          <label class="filter-label">Buscar</label>
          <input type="search" id="searchInput" class="form-input" placeholder="Título, dirección, barrio, dueño..." value="${this.filters.q}">
        </div>
        <div class="filter-group">
          <label class="filter-label">Estado</label>
          <select id="estadoFilter" class="form-select">
            <option value="">Todos</option>
            <option value="publicado"${this.filters.estado === 'publicado' ? ' selected' : ''}>Publicados</option>
            <option value="disponible"${this.filters.estado === 'disponible' ? ' selected' : ''}>Disponibles</option>
            <option value="finalizado"${this.filters.estado === 'finalizado' ? ' selected' : ''}>Finalizados</option>
            <option value="under_review"${this.filters.estado === 'under_review' ? ' selected' : ''}>En revisión</option>
            <option value="pendiente"${this.filters.estado === 'pendiente' ? ' selected' : ''}>Pendientes</option>
          </select>
        </div>
        <div class="filter-group">
          <label class="filter-label">Destacado</label>
          <select id="destacadoFilter" class="form-select">
            <option value="">Todos</option>
            <option value="si"${this.filters.destacado === 'si' ? ' selected' : ''}>Destacados</option>
            <option value="no"${this.filters.destacado === 'no' ? ' selected' : ''}>Normales</option>
          </select>
        </div>
        <div class="filter-actions">
          <button class="btn btn-secondary" id="clearFilters" style="display:${this.filters.q || this.filters.estado || this.filters.destacado ? 'inline-flex' : 'none'}">Limpiar</button>
        </div>
      </div>

      <div class="card">
        <div class="table-container">
          <table class="table" id="propsTable">
            <thead>
              <tr>
                <th>Inmueble</th>
                <th>Estado</th>
                <th>Precio</th>
                <th>Publicado</th>
                <th>Dueño</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody id="propsBody"></tbody>
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

    document.getElementById('destacadoFilter')?.addEventListener('change', e => {
      this.filters.destacado = e.target.value;
      this.currentPage = 1;
      this.loadData();
    });

    document.getElementById('clearFilters')?.addEventListener('click', () => {
      this.filters = { q: '', estado: '', destacado: '' };
      document.getElementById('searchInput').value = '';
      document.getElementById('estadoFilter').value = '';
      document.getElementById('destacadoFilter').value = '';
      this.currentPage = 1;
      this.loadData();
    });

    document.getElementById('exportBtn')?.addEventListener('click', () => this.exportCSV());

    document.getElementById('propsBody')?.addEventListener('click', e => {
      const btn = e.target.closest('button[data-action]');
      if (!btn) return;
      const row = btn.closest('tr');
      const id = row?.dataset.id;
      if (!id) return;
      const action = btn.dataset.action;
      if (action === 'aprobar') this.aprobar(id);
      else if (action === 'finalizar') this.finalizar(id);
      else if (action === 'destacar') this.destacar(id);
      else if (action === 'eliminar') this.eliminar(id);
      else if (action === 'estado') this.cambiarEstado(id, e.target);
    });

    document.getElementById('pagination')?.addEventListener('click', e => {
      const btn = e.target.closest('.page-btn');
      if (btn && !btn.disabled) this.goToPage(parseInt(btn.dataset.page));
    });
  }

  async loadData() {
    try {
      const params = { q: this.filters.q, estado: this.filters.estado, destacado: this.filters.destacado, page: this.currentPage, limit: this.limit };
      const res = await this.api.get('/publicaciones', params);
      this.renderTable(res.data);
      this.renderPagination(res.total, res.page, res.limit, res.totalPages);

      const hasFilters = this.filters.q || this.filters.estado || this.filters.destacado;
      const clearBtn = document.getElementById('clearFilters');
      if (clearBtn) clearBtn.style.display = hasFilters ? 'inline-flex' : 'none';
    } catch (err) {
      console.error('Error cargando publicaciones:', err);
      this.showError('Error al cargar publicaciones');
    }
  }

  renderTable(items) {
    const tbody = document.getElementById('propsBody');
    if (!tbody) return;

    if (!items.length) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:32px;color:var(--text-muted)">Sin inmuebles</td></tr>`;
      return;
    }

    tbody.innerHTML = items.map(p => {
      const estado = p.estado || 'disponible';
      const pill = ESTADO_PILLS[estado] || 'grey';
      const estadoLabel = estado;
      const oculto = p.publicado === false ? ' <span class="pill grey">oculto</span>' : '';
      let gold = '';
      if (p.isFeatured) {
        const restan = p.destacadoInfo?.dias;
        const vence = p.destacadoInfo?.vence || '-';
        gold = ` <span class="pill gold">destacado${restan != null ? ` - ${restan} día(s)` : ''}</span><div class="muted">vence ${vence}</div>`;
      }
      if (p.solicitudDestacar === true) {
        gold += ` <span class="pill pend">pidio destacar${p.destacadoDias ? ` (${p.destacadoDias} días)` : ''}</span>`;
      }

      const aprobar = estado === 'under_review' ? `<button class="btn btn-sm btn-primary" data-action="aprobar" title="Aprobar"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg></button>` : '';
      const estadoOpts = ['publicado', 'disponible', 'finalizado', 'under_review', 'pendiente'].map(e => `<option value="${e}"${e === (p.estado || 'disponible') ? ' selected' : ''}>${e}</option>`).join('');
      const cambiarEstado = `<select class="form-select" style="padding:4px 8px;font-size:12px" data-action="estado" onchange="this.form?.submit()">${['publicado','disponible','finalizado','under_review','pendiente'].map(e => `<option value="${e}"${e === (p.estado||'disponible')?' selected':''}>${e}</option>`).join('')}</select>`;
      const destacarTxt = p.isFeatured ? 'Quitar' : (p.destacadoDias ? `Destacar ${p.destacadoDias}d` : 'Destacar 30d');
      const destacar = `<button class="btn btn-sm btn-gold" data-action="destacar" title="${p.isFeatured ? 'Quitar destacado' : 'Destacar'}">${destacarTxt}</button>`;
      const cerrar = estado !== 'finalizado' ? `<button class="btn btn-sm btn-ghost" data-action="finalizar" title="Finalizar"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg></button>` : '';
      const eliminar = `<button class="btn btn-sm btn-danger" data-action="eliminar" title="Eliminar"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg></button>`;

      return `
        <tr data-id="${p.id}">
          <td>
            <div class="cell-primary">${this.escape(p.titulo || '(sin título)')}${p.publicado === false ? ' <span class="pill grey">oculto</span>' : ''}${p.isFeatured ? ` <span class="pill gold">destacado${p.destacadoInfo?.dias != null ? ` - ${p.destacadoInfo.dias} día(s)` : ''}</span><div class="muted">vence ${p.destacadoInfo?.vence || '-'}</div>` : ''}${p.solicitudDestacar ? ' <span class="pill pend">pidio destacar</span>' : ''}</div>
            <div class="cell-secondary">${this.escape(p.direccion || '')} - ${this.escape(p.barrio || '')}, ${this.escape(p.ciudad || '')}</div>
          </td>
          <td><span class="pill ${ESTADO_PILLS[p.estado || 'disponible']}">${p.estado || 'disponible'}</span></td>
          <td>${p.precio != null ? p.precio : '-'}</td>
          <td class="cell-secondary">${p.creado}</td>
          <td class="cell-secondary">${this.escape(p.idPropietario?.slice(0, 10) + '...')}</td>
          <td class="cell-actions">
            ${estado === 'under_review' ? '<button class="btn btn-sm btn-primary" data-action="aprobar" title="Aprobar"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg></button>' : ''}
            <select class="form-select" style="padding:4px 8px;font-size:12px" data-action="estado">${['publicado','disponible','finalizado','under_review','pendiente'].map(e => `<option value="${e}"${e === (p.estado||'disponible')?' selected':''}>${e}</option>`).join('')}</select>
            ${destacar}
            ${cerrar}
            ${eliminar}
          </td>
        </tr>
      `;
    }).join('');
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

  async loadData() {
    try {
      const params = { q: this.filters.q, estado: this.filters.estado, destacado: this.filters.destacado, page: this.currentPage, limit: this.limit };
      const res = await this.api.get('/publicaciones', params);
      this.renderTable(res.data);
      this.renderPagination(res.total, res.page, res.limit, res.totalPages);
      document.getElementById('clearFilters').style.display = (this.filters.q || this.filters.estado || this.filters.destacado) ? 'inline-flex' : 'none';
    } catch (err) {
      console.error('Error cargando publicaciones:', err);
      this.showError('Error al cargar publicaciones');
    }
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

  // Actions
  async aprobar(id) {
    try {
      await this.api.post(`/publicaciones/${id}/aprobar`);
      showToast('Publicación aprobada y publicada', 'success');
      this.loadData();
    } catch (err) { toastError('Error: ' + err.message); }
  }

  async finalizar(id) {
    const confirmed = await confirmModal('¿Marcar esta publicación como finalizada?', { title: 'Finalizar publicación', danger: true });
    if (!confirmed) return;
    try {
      await this.api.post(`/publicaciones/${id}/finalizar`);
      showToast('Publicación finalizada', 'success');
      this.loadData();
    } catch (err) { toastError('Error: ' + err.message); }
  }

  async destacar(id) {
    try {
      await this.api.post(`/publicaciones/${id}/destacar`);
      showToast('Destacado actualizado', 'success');
      this.loadData();
    } catch (err) { toastError('Error: ' + err.message); }
  }

  async eliminar(id) {
    const confirmed = await confirmModal('¿Eliminar definitivamente esta publicación? No se puede deshacer.', { title: 'Eliminar publicación', danger: true });
    if (!confirmed) return;
    try {
      await this.api.post(`/publicaciones/${id}/eliminar`);
      showToast('Publicación eliminada', 'success');
      this.loadData();
    } catch (err) { toastError('Error: ' + err.message); }
  }

  async cambiarEstado(id, selectEl) {
    const nuevo = selectEl.value;
    const validos = ['publicado', 'disponible', 'finalizado', 'under_review', 'pendiente'];
    if (!validos.includes(nuevo)) return;
    try {
      await this.api.post(`/publicaciones/${id}/estado`, { estado: nuevo });
      showToast('Estado cambiado a ' + nuevo, 'success');
      this.loadData();
    } catch (err) { toastError('Error: ' + err.message); }
  }

  async exportCSV() {
    try {
      const res = await this.api.get('/publicaciones', { limit: 1000, ...this.filters });
      const headers = ['ID', 'Título', 'Dirección', 'Barrio', 'Ciudad', 'Estado', 'Precio', 'Destacado', 'Dueño', 'Creado'];
      const rows = res.data.map(p => [p.id, p.titulo, p.direccion, p.barrio, p.ciudad, p.estado || 'disponible', p.precio, p.isFeatured ? 'Sí' : 'No', p.idPropietario, p.creado]);
      const csv = [headers, ...rows].map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n');
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = `publicaciones_${new Date().toISOString().slice(0,10)}.csv`;
      a.click(); URL.revokeObjectURL(url);
    } catch (err) { toastError('Error al exportar'); }
  }

  showError(msg) {
    const tbody = document.getElementById('propsBody');
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

function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(window.debounceTimer);
    window.debounceTimer = setTimeout(() => fn.apply(this, args), delay);
  };
}