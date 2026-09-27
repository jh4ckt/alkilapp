// ==========================================================================
// AlkilApp Admin - Verificaciones Component
// ==========================================================================
import { formatDate, formatRelative, openModal, confirmModal, showToast } from '../utils/helpers.js';

const ESTADO_LABELS = { 
  pendiente: 'Pendiente', 
  aprobado: 'Aprobado', 
  rechazado: 'Rechazado' 
};

const ESTADO_PILLS = { 
  pendiente: 'pend', 
  aprobado: 'ok', 
  rechazado: 'bad' 
};

export default class Verificaciones {
  constructor(api) {
    this.api = api;
    this.currentPage = 1;
    this.limit = 20;
    this.filters = { q: '', estado: '' };
    this.sortColumn = 'creado';
    this.sortDirection = 'desc';
    this.items = [];
    this.total = 0;
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
          <h1 class="page-title" style="font-size: 1.5rem; font-weight: 700; color: var(--text-color, #0f172a); margin: 0 0 0.25rem 0;">Verificaciones de Identidad</h1>
          <p class="page-subtitle" style="font-size: 0.875rem; color: var(--text-muted, #64748b); margin: 0;">Gestiona la validación de documentos y badges de los usuarios</p>
        </div>
        <div class="page-actions" style="display: flex; gap: 0.75rem;">
          <button class="btn btn-secondary" id="exportBtn" style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.5rem 1rem; border-radius: 0.5rem; font-weight: 500; cursor: pointer;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px;"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            Exportar CSV
          </button>
        </div>
      </header>

      <!-- Barra de Filtros -->
      <div class="filters-bar" style="display: flex; gap: 1rem; flex-wrap: wrap; align-items: flex-end; margin-bottom: 1.5rem; background: var(--card-bg, #ffffff); padding: 1rem; border-radius: 0.75rem; border: 1px solid var(--border-color, #e2e8f0);">
        <div class="filter-group" style="flex: 1; min-width: 280px;">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Buscar solicitud</label>
          <input type="search" id="searchInput" class="form-input" placeholder="Email, nombre, número de documento..." value="${this.escape(this.filters.q)}" style="width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.375rem; font-size: 0.875rem;">
        </div>

        <div class="filter-group" style="min-width: 180px;">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Estado</label>
          <select id="estadoFilter" class="form-select" style="width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.375rem; font-size: 0.875rem; background-color: var(--card-bg, #fff);">
            <option value="">Todos los estados</option>
            <option value="pendiente" ${this.filters.estado === 'pendiente' ? 'selected' : ''}>Pendientes</option>
            <option value="aprobado" ${this.filters.estado === 'aprobado' ? 'selected' : ''}>Aprobadas</option>
            <option value="rechazado" ${this.filters.estado === 'rechazado' ? 'selected' : ''}>Rechazadas</option>
          </select>
        </div>

        <div class="filter-actions" style="display: flex; align-items: flex-end;">
          <button class="btn btn-secondary" id="clearFilters" style="display: ${this.filters.q || this.filters.estado ? 'inline-flex' : 'none'}; padding: 0.5rem 0.875rem; border-radius: 0.375rem; font-size: 0.875rem; cursor: pointer;">Limpiar</button>
        </div>
      </div>

      <!-- Tabla de Solicitudes -->
      <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.75rem; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.04);">
        <div class="table-container" id="tableContainer" style="overflow-x: auto;">
          <table class="table" id="verifTable" style="width: 100%; border-collapse: collapse; text-align: left;">
            <thead>
              <tr style="background: var(--bg-surface-secondary, #f8fafc); border-bottom: 1px solid var(--border-color, #e2e8f0); font-size: 0.75rem; text-transform: uppercase; color: var(--text-muted, #64748b);">
                <th data-sort="nombre" style="padding: 0.875rem 1rem; font-weight: 600; cursor: pointer;">Usuario ↕</th>
                <th data-sort="email" style="padding: 0.875rem 1rem; font-weight: 600; cursor: pointer;">Email ↕</th>
                <th data-sort="tipoDocumento" style="padding: 0.875rem 1rem; font-weight: 600; cursor: pointer;">Documento ↕</th>
                <th data-sort="estado" style="padding: 0.875rem 1rem; font-weight: 600; cursor: pointer;">Estado ↕</th>
                <th data-sort="creado" style="padding: 0.875rem 1rem; font-weight: 600; cursor: pointer;">Enviado ↕</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600; text-align: center;">Acciones</th>
              </tr>
            </thead>
            <tbody id="verifBody">
              <tr>
                <td colspan="6" style="text-align: center; padding: 2.5rem; color: var(--text-muted, #64748b);">
                  Cargando solicitudes...
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Paginación -->
        <div class="card-footer" style="display: flex; justify-content: space-between; align-items: center; padding: 1rem 1.25rem; border-top: 1px solid var(--border-color, #e2e8f0); background: var(--card-bg, #ffffff);">
          <span id="paginationInfo" style="font-size: 0.875rem; color: var(--text-muted, #64748b);">Mostrando 0 de 0</span>
          <div class="pagination" id="pagination" style="display: flex; gap: 0.375rem;"></div>
        </div>
      </div>
    `;
  }

  bindEvents() {
    const searchInput = this.container.querySelector('#searchInput');
    searchInput?.addEventListener('input', debounce(() => {
      this.filters.q = searchInput.value.trim();
      this.currentPage = 1;
      this.toggleClearFiltersBtn();
      this.loadData();
    }, 300));

    this.container.querySelector('#estadoFilter')?.addEventListener('change', e => {
      this.filters.estado = e.target.value;
      this.currentPage = 1;
      this.toggleClearFiltersBtn();
      this.loadData();
    });

    this.container.querySelector('#clearFilters')?.addEventListener('click', () => {
      this.filters = { q: '', estado: '' };
      this.currentPage = 1;

      const inputQ = this.container.querySelector('#searchInput');
      const selectEstado = this.container.querySelector('#estadoFilter');

      if (inputQ) inputQ.value = '';
      if (selectEstado) selectEstado.value = '';

      this.toggleClearFiltersBtn();
      this.loadData();
    });

    this.container.querySelector('#exportBtn')?.addEventListener('click', () => this.exportCSV());

    // Ordenamiento por columna
    this.container.querySelectorAll('#verifTable th[data-sort]').forEach(th => {
      th.addEventListener('click', () => this.handleSort(th.dataset.sort));
    });

    // Delegación de eventos en la tabla
    const tbody = this.container.querySelector('#verifBody');
    tbody?.addEventListener('click', e => {
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

    // Paginación
    this.container.querySelector('#pagination')?.addEventListener('click', e => {
      const btn = e.target.closest('.page-btn');
      if (btn && !btn.disabled) {
        this.goToPage(parseInt(btn.dataset.page, 10));
      }
    });
  }

  toggleClearFiltersBtn() {
    const clearBtn = this.container.querySelector('#clearFilters');
    if (clearBtn) {
      clearBtn.style.display = (this.filters.q || this.filters.estado) ? 'inline-flex' : 'none';
    }
  }

  async loadData() {
    try {
      const params = {
        q: this.filters.q,
        estado: this.filters.estado,
        page: this.currentPage,
        limit: this.limit,
        sort: this.sortColumn,
        order: this.sortDirection
      };

      const res = await this.api.get('/verificaciones', params);
      this.items = res.data || res.items || (Array.isArray(res) ? res : []);
      this.total = res.total || this.items.length;

      const page = res.page || this.currentPage;
      const limit = res.limit || this.limit;
      const totalPages = res.totalPages || Math.ceil(this.total / limit) || 1;

      this.renderTable(this.items);
      this.renderPagination(this.total, page, limit, totalPages);
      this.toggleClearFiltersBtn();
    } catch (err) {
      console.error('Error cargando verificaciones:', err);
      this.showError('Error al cargar la lista de verificaciones');
    }
  }

  getVerifId(item) {
    return item.id || item._id || item.docId;
  }

  renderTable(items) {
    const tbody = this.container.querySelector('#verifBody');
    if (!tbody) return;

    if (!items || !items.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="6" style="text-align: center; padding: 2.5rem; color: var(--text-muted, #64748b);">
            Sin solicitudes de verificación encontradas
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = items.map(v => {
      const itemId = this.getVerifId(v);
      const estado = v.estado || 'pendiente';
      const pill = ESTADO_PILLS[estado] || 'grey';
      const estadoLabel = ESTADO_LABELS[estado] || estado;

      const acciones = estado === 'pendiente' ? `
        <button class="btn btn-sm btn-primary" data-action="aprobar" title="Aprobar solicitud" style="padding: 4px 8px; cursor: pointer;">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
        </button>
        <button class="btn btn-sm btn-danger" data-action="rechazar" title="Rechazar solicitud" style="padding: 4px 8px; cursor: pointer;">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      ` : `<span style="font-size: 0.75rem; color: var(--text-muted, #94a3b8);">Procesado</span>`;

      return `
        <tr data-id="${itemId}" style="border-bottom: 1px solid var(--border-color, #e2e8f0); vertical-align: middle;">
          <td style="padding: 0.75rem 1rem;">
            <strong style="display: block; font-size: 0.875rem; color: var(--text-color, #0f172a);">${this.escape(v.nombre || v.usuarioNombre || '(Sin nombre)')}</strong>
          </td>
          <td style="padding: 0.75rem 1rem; font-size: 0.875rem; color: var(--text-color, #334155);">
            ${this.escape(v.email || v.usuarioEmail || '-')}
          </td>
          <td style="padding: 0.75rem 1rem; font-size: 0.875rem;">
            <span style="font-weight: 600;">${this.escape(v.tipoDocumento || 'DNI')}</span>: ${this.escape(v.numeroDocumento || v.documento || '-')}
          </td>
          <td style="padding: 0.75rem 1rem;">
            <span class="pill ${pill}">
              ${estadoLabel}
            </span>
          </td>
          <td style="padding: 0.75rem 1rem; font-size: 0.85rem; color: var(--text-muted, #64748b);">
            ${v.creado || v.createdAt ? formatDate(v.creado || v.createdAt) : '-'}
          </td>
          <td style="padding: 0.75rem 1rem; text-align: center;">
            <div style="display: flex; align-items: center; justify-content: center; gap: 6px;">
              ${acciones}
              <button class="btn btn-sm btn-ghost" data-action="ver" title="Ver documentos adjuntos" style="padding: 4px 8px; cursor: pointer;">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  renderPagination(total, page, limit, totalPages) {
    const info = this.container.querySelector('#paginationInfo');
    if (info) {
      const start = total > 0 ? (page - 1) * limit + 1 : 0;
      const end = Math.min(page * limit, total);
      info.textContent = `Mostrando ${start} - ${end} de ${total} solicitudes`;
    }

    const container = this.container.querySelector('#pagination');
    if (!container) return;

    if (totalPages <= 1) { 
      container.innerHTML = ''; 
      return; 
    }

    let html = '';
    html += `<button class="page-btn" data-page="${page - 1}" ${page === 1 ? 'disabled' : ''} style="padding: 0.375rem 0.625rem; border-radius: 0.375rem; border: 1px solid var(--border-color, #cbd5e1); background: var(--card-bg, #fff); cursor: pointer;"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg></button>`;

    const maxVisible = 5;
    let start = Math.max(1, page - Math.floor(maxVisible / 2));
    let end = Math.min(totalPages, start + maxVisible - 1);
    if (end - start + 1 < maxVisible) start = Math.max(1, end - maxVisible + 1);

    for (let i = start; i <= end; i++) {
      html += `<button class="page-btn ${i === page ? 'active' : ''}" data-page="${i}" style="padding: 0.375rem 0.625rem; border-radius: 0.375rem; border: 1px solid var(--border-color, #cbd5e1); background: ${i === page ? 'var(--primary-color, #3b82f6)' : 'var(--card-bg, #fff)'}; color: ${i === page ? '#fff' : 'inherit'}; cursor: pointer;">${i}</button>`;
    }

    html += `<button class="page-btn" data-page="${page + 1}" ${page === totalPages ? 'disabled' : ''} style="padding: 0.375rem 0.625rem; border-radius: 0.375rem; border: 1px solid var(--border-color, #cbd5e1); background: var(--card-bg, #fff); cursor: pointer;"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg></button>`;

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
      let v = this.items.find(item => String(this.getVerifId(item)) === String(id));
      if (!v) {
        try {
          v = await this.api.get(`/verificaciones/${id}`);
        } catch (e) {
          console.warn('No se obtuvo el detalle remoto:', e);
        }
      }

      if (!v) {
        showToast('No se encontraron los datos del documento', 'danger');
        return;
      }

      const formatImgSrc = (img) => {
        if (!img) return null;
        if (img.startsWith('data:') || img.startsWith('http')) return img;
        return `data:image/jpeg;base64,${img}`;
      };

      const imgFront = formatImgSrc(v.imagen || v.imagenAnverso || v.documentoFrontal);
      const imgBack = formatImgSrc(v.imagenReverso || v.documentoPosterior);
      const estado = v.estado || 'pendiente';
      const pill = ESTADO_PILLS[estado] || 'grey';
      const estadoLabel = ESTADO_LABELS[estado] || estado;

      const html = `
        <div style="display: grid; gap: 16px;">
          <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; font-size: 0.875rem;">
            <div><strong>Usuario:</strong> ${this.escape(v.nombre || v.usuarioNombre || '(Sin nombre)')}</div>
            <div><strong>Email:</strong> ${this.escape(v.email || v.usuarioEmail || '-')}</div>
            <div><strong>Documento:</strong> ${this.escape(v.tipoDocumento || 'DNI')} ${this.escape(v.numeroDocumento || v.documento || '')}</div>
            <div><strong>Estado:</strong> <span class="pill ${pill}">${estadoLabel}</span></div>
          </div>

          <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-top: 8px;">
            <div style="background: var(--bg-surface-secondary, #f8fafc); padding: 12px; border-radius: 8px; border: 1px solid var(--border-color, #e2e8f0); text-align: center;">
              <strong style="display: block; font-size: 0.8rem; text-transform: uppercase; color: var(--text-muted, #64748b); margin-bottom: 8px;">Anverso (Frente)</strong>
              ${imgFront ? `<a href="${imgFront}" target="_blank" rel="noopener"><img src="${imgFront}" alt="Anverso" style="width: 100%; max-height: 260px; object-fit: contain; border-radius: 6px; border: 1px solid var(--border-color, #cbd5e1);"></a>` : '<p class="muted" style="margin: 20px 0; font-size: 0.85rem; color: var(--text-muted, #94a3b8);">Sin imagen frontal</p>'}
            </div>
            <div style="background: var(--bg-surface-secondary, #f8fafc); padding: 12px; border-radius: 8px; border: 1px solid var(--border-color, #e2e8f0); text-align: center;">
              <strong style="display: block; font-size: 0.8rem; text-transform: uppercase; color: var(--text-muted, #64748b); margin-bottom: 8px;">Reverso (Atrás)</strong>
              ${imgBack ? `<a href="${imgBack}" target="_blank" rel="noopener"><img src="${imgBack}" alt="Reverso" style="width: 100%; max-height: 260px; object-fit: contain; border-radius: 6px; border: 1px solid var(--border-color, #cbd5e1);"></a>` : '<p class="muted" style="margin: 20px 0; font-size: 0.85rem; color: var(--text-muted, #94a3b8);">Sin imagen posterior</p>'}
            </div>
          </div>

          ${v.motivo ? `<div style="font-size: 0.875rem; background: #fff1f2; color: #e11d48; padding: 10px 12px; border-radius: 6px;"><strong>Motivo de rechazo:</strong> ${this.escape(v.motivo)}</div>` : ''}
        </div>
      `;

      const footer = estado === 'pendiente' ? `
        <button class="btn btn-secondary" id="modalCloseBtn" style="padding: 0.5rem 1rem; font-size: 0.875rem; cursor: pointer;">Cerrar</button>
        <button class="btn btn-danger" id="modalRechazarBtn" style="padding: 0.5rem 1rem; font-size: 0.875rem; cursor: pointer;">Rechazar</button>
        <button class="btn btn-primary" id="modalAprobarBtn" style="padding: 0.5rem 1rem; font-size: 0.875rem; cursor: pointer;">Aprobar</button>
      ` : `<button class="btn btn-secondary" id="modalCloseBtn" style="padding: 0.5rem 1rem; font-size: 0.875rem; cursor: pointer;">Cerrar</button>`;

      const modalRef = await openModal(html, { title: 'Verificación de Identidad', footer });

      const closeModal = () => {
        if (modalRef && typeof modalRef.close === 'function') {
          modalRef.close();
        } else {
          document.querySelector('.modal-backdrop')?.remove();
          document.querySelector('.modal')?.remove();
        }
      };

      document.getElementById('modalAprobarBtn')?.addEventListener('click', async () => {
        closeModal();
        await this.aprobar(id);
      });

      document.getElementById('modalRechazarBtn')?.addEventListener('click', async () => {
        closeModal();
        await this.rechazar(id);
      });

      document.getElementById('modalCloseBtn')?.addEventListener('click', () => {
        closeModal();
      });

    } catch (err) {
      console.error(err);
      showToast('Error al obtener el documento', 'danger');
    }
  }

  async aprobar(id) {
    const confirmed = await confirmModal(
      '¿Aprobar esta verificación? El usuario recibirá el badge "Verificado por AlkilApp".',
      { title: 'Aprobar Verificación', confirmText: 'Aprobar' }
    );
    if (!confirmed) return;

    try {
      await this.api.post(`/verificaciones/${id}/aprobar`);
      showToast('Verificación aprobada exitosamente', 'success');
      await this.loadData();
    } catch (err) {
      showToast('Error al aprobar: ' + (err.message || 'Error del servidor'), 'danger');
    }
  }

  async rechazar(id) {
    // Modal estilizado en lugar del prompt() nativo
    const modalHtml = `
      <div style="display: grid; gap: 12px;">
        <p style="margin: 0; font-size: 0.875rem; color: var(--text-color, #0f172a);">
          Por favor, especifica el motivo por el cual se rechaza esta solicitud de verificación:
        </p>
        <textarea id="motivoRechazoInput" class="form-input" rows="3" placeholder="Ej: Las imágenes del documento se encuentran borrosas o ilegibles." style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.375rem; font-size: 0.875rem;"></textarea>
      </div>
    `;

    const footer = `
      <button class="btn btn-secondary" id="cancelRechazoBtn" style="padding: 0.5rem 1rem; font-size: 0.875rem; cursor: pointer;">Cancelar</button>
      <button class="btn btn-danger" id="confirmRechazoBtn" style="padding: 0.5rem 1rem; font-size: 0.875rem; cursor: pointer;">Rechazar Solicitud</button>
    `;

    await openModal(modalHtml, {
      title: 'Rechazar Verificación',
      footer
    });

    const closeModal = () => {
      document.querySelector('.modal-backdrop')?.remove();
      document.querySelector('.modal')?.remove();
    };

    document.getElementById('cancelRechazoBtn')?.addEventListener('click', () => {
      closeModal();
    });

    document.getElementById('confirmRechazoBtn')?.addEventListener('click', async (e) => {
      const btn = e.currentTarget;
      const motivo = document.getElementById('motivoRechazoInput')?.value.trim();

      if (!motivo) {
        showToast('Debes ingresar un motivo de rechazo', 'danger');
        return;
      }

      btn.disabled = true;
      btn.textContent = 'Procesando...';

      closeModal();

      try {
        await this.api.post(`/verificaciones/${id}/rechazar`, { motivo });
        showToast('Verificación rechazada correctamente', 'success');
        await this.loadData();
      } catch (err) {
        showToast('Error al rechazar: ' + (err.message || 'Error en servidor'), 'danger');
      }
    });
  }

  async exportCSV() {
    try {
      const res = await this.api.get('/verificaciones', { limit: 1000, ...this.filters });
      const itemsToExport = res.data || res.items || (Array.isArray(res) ? res : []);
      
      const headers = ['ID', 'Nombre', 'Email', 'Tipo Doc', 'Número Doc', 'Estado', 'Enviado'];
      const rows = itemsToExport.map(v => [
        this.getVerifId(v),
        v.nombre || v.usuarioNombre || '',
        v.email || v.usuarioEmail || '',
        v.tipoDocumento || '',
        v.numeroDocumento || v.documento || '',
        v.estado || '',
        v.creado || v.createdAt || ''
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
      showToast('Error al exportar los datos', 'danger');
    }
  }

  showError(msg) {
    const tbody = this.container.querySelector('#verifBody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; padding: 2rem; color: var(--danger, #ef4444);">${msg}</td></tr>`;
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

function debounce(fn, delay) {
  let timer;
  return function (...args) {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}