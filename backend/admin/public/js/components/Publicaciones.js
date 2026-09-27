// ==========================================================================
// AlkilApp Admin - Publicaciones Component
// ==========================================================================
import { formatCurrency, formatDate, showToast, debounce } from '../utils/helpers.js';

export default class Publicaciones {
  constructor(api) {
    this.api = api;
    this.currentPage = 1;
    this.limit = 15;
    this.total = 0;
    this.filters = { q: '', estado: '', destacado: '' };
    this.publicaciones = [];
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
          <h1 class="page-title" style="font-size: 1.5rem; font-weight: 700; color: var(--text-color, #0f172a); margin: 0 0 0.25rem 0;">Publicaciones</h1>
          <p class="page-subtitle" style="font-size: 0.875rem; color: var(--text-muted, #64748b); margin: 0;">Modera y gestiona los inmuebles publicados en la plataforma</p>
        </div>
        <div class="page-actions">
          <button class="btn btn-secondary" id="refreshBtn" style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.5rem 1rem; border-radius: 0.5rem; font-weight: 500; cursor: pointer;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px;">
              <path d="M23 4v6h-6M1 20v-6h6"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            Refrescar
          </button>
        </div>
      </header>

      <!-- Barra de Filtros -->
      <div class="filters-bar" style="display: flex; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.5rem; align-items: flex-end; background: var(--card-bg, #ffffff); padding: 1rem 1.25rem; border-radius: 0.75rem; border: 1px solid var(--border-color, #e2e8f0); box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
        <div class="filter-group" style="flex: 2; min-width: 240px;">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Buscar publicación</label>
          <div style="position: relative;">
            <input type="search" id="searchInput" class="form-input" placeholder="Título, dirección, propietario..." value="${this.filters.q}" style="width: 100%; padding: 0.5rem 0.75rem 0.5rem 2.25rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.5rem; font-size: 0.875rem; outline: none;">
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
            <option value="activa" ${this.filters.estado === 'activa' ? 'selected' : ''}>Activas</option>
            <option value="pendiente" ${this.filters.estado === 'pendiente' ? 'selected' : ''}>Pendientes</option>
            <option value="pausada" ${this.filters.estado === 'pausada' ? 'selected' : ''}>Pausadas</option>
            <option value="rechazada" ${this.filters.estado === 'rechazada' ? 'selected' : ''}>Rechazadas</option>
          </select>
        </div>

        <div class="filter-group" style="flex: 1; min-width: 160px;">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Destacado</label>
          <select id="destacadoFilter" class="form-select" style="width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.5rem; font-size: 0.875rem; background-color: var(--card-bg, #fff);">
            <option value="">Todos</option>
            <option value="true" ${this.filters.destacado === 'true' ? 'selected' : ''}>Sí (Destacadas)</option>
            <option value="false" ${this.filters.destacado === 'false' ? 'selected' : ''}>No</option>
          </select>
        </div>

        <div class="filter-actions">
          <button class="btn btn-secondary" id="clearFiltersBtn" style="display: ${this.filters.q || this.filters.estado || this.filters.destacado ? 'inline-flex' : 'none'}; align-items: center; gap: 0.375rem; padding: 0.5rem 0.875rem; border-radius: 0.5rem; font-size: 0.875rem; cursor: pointer;">
            Limpiar Filtros
          </button>
        </div>
      </div>

      <!-- Tabla / Card -->
      <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.75rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05); overflow: hidden;">
        <div class="table-responsive" style="overflow-x: auto;">
          <table class="table" style="width: 100%; border-collapse: collapse; text-align: left; font-size: 0.875rem;">
            <thead>
              <tr style="background-color: var(--table-header-bg, #f8fafc); border-bottom: 1px solid var(--border-color, #e2e8f0); text-transform: uppercase; font-size: 0.75rem; letter-spacing: 0.05em; color: var(--text-muted, #64748b);">
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Inmueble</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Propietario</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Precio</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Estado Actual</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Destacado</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Fecha</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600; text-align: center;">Acciones</th>
              </tr>
            </thead>
            <tbody id="publicacionesTbody">
              <tr>
                <td colspan="7" style="padding: 3rem; text-align: center; color: var(--text-muted, #64748b);">
                  <div style="display: flex; flex-direction: column; align-items: center; gap: 0.5rem;">
                    <div class="spinner" style="width: 24px; height: 24px; border: 2px solid #cbd5e1; border-top-color: #3b82f6; border-radius: 50%; animation: spin 0.8s linear infinite;"></div>
                    <span>Cargando publicaciones...</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Paginación -->
        <div class="pagination-container" style="display: flex; justify-content: space-between; align-items: center; padding: 0.875rem 1.25rem; border-top: 1px solid var(--border-color, #e2e8f0); background-color: var(--card-bg, #ffffff);">
          <span id="paginationInfo" style="font-size: 0.85rem; color: var(--text-muted, #64748b);">Mostrando 0 - 0 de 0 publicaciones</span>
          <div style="display: flex; gap: 0.5rem;">
            <button class="btn btn-secondary btn-sm" id="prevPageBtn" style="padding: 0.375rem 0.75rem; border-radius: 0.375rem; font-size: 0.8125rem; cursor: pointer;" disabled>Anterior</button>
            <button class="btn btn-secondary btn-sm" id="nextPageBtn" style="padding: 0.375rem 0.75rem; border-radius: 0.375rem; font-size: 0.8125rem; cursor: pointer;" disabled>Siguiente</button>
          </div>
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

    this.container.querySelector('#destacadoFilter')?.addEventListener('change', (e) => {
      this.filters.destacado = e.target.value;
      this.currentPage = 1;
      this.loadData();
    });

    this.container.querySelector('#clearFiltersBtn')?.addEventListener('click', () => {
      this.filters = { q: '', estado: '', destacado: '' };
      this.currentPage = 1;
      this.render(this.container);
    });

    this.container.querySelector('#refreshBtn')?.addEventListener('click', () => {
      this.loadData();
    });

    this.container.querySelector('#prevPageBtn')?.addEventListener('click', () => {
      if (this.currentPage > 1) {
        this.currentPage--;
        this.loadData();
      }
    });

    this.container.querySelector('#nextPageBtn')?.addEventListener('click', () => {
      this.currentPage++;
      this.loadData();
    });

    const tbody = this.container.querySelector('#publicacionesTbody');
    
    // Al cambiar la opción del selector de estado
    tbody?.addEventListener('change', (e) => {
      if (e.target.classList.contains('estado-select')) {
        const id = e.target.dataset.id;
        const newEstado = e.target.value;
        const originalEstado = e.target.dataset.original;

        const saveBtn = tbody.querySelector(`.save-status-btn[data-id="${id}"]`);
        if (saveBtn) {
          if (newEstado !== originalEstado) {
            saveBtn.classList.remove('btn-secondary');
            saveBtn.classList.add('btn-primary');
            saveBtn.style.opacity = '1';
            saveBtn.style.cursor = 'pointer';
            saveBtn.disabled = false;
          } else {
            saveBtn.classList.add('btn-secondary');
            saveBtn.classList.remove('btn-primary');
            saveBtn.style.opacity = '0.5';
            saveBtn.style.cursor = 'not-allowed';
            saveBtn.disabled = true;
          }
        }
      }
    });

    // Delegación de eventos para clicks en guardar o toggle destacado
    tbody?.addEventListener('click', async (e) => {
      const saveBtn = e.target.closest('.save-status-btn');
      if (saveBtn && !saveBtn.disabled) {
        const id = saveBtn.dataset.id;
        const selectEl = tbody.querySelector(`.estado-select[data-id="${id}"]`);
        if (!selectEl) return;

        const nuevoEstado = selectEl.value;
        await this.guardarEstado(id, nuevoEstado, saveBtn, selectEl);
      }

      const starBtn = e.target.closest('.toggle-destacado-btn');
      if (starBtn) {
        const id = starBtn.dataset.id;
        const actualDestacado = starBtn.dataset.destacado === 'true';
        await this.toggleDestacado(id, !actualDestacado);
      }
    });
  }

  async loadData() {
    try {
      const queryParams = new URLSearchParams({
        page: this.currentPage,
        limit: this.limit,
        ...(this.filters.q && { q: this.filters.q }),
        ...(this.filters.estado && { estado: this.filters.estado }),
        ...(this.filters.destacado && { destacado: this.filters.destacado })
      }).toString();

      const response = await this.api.get(`/publicaciones?${queryParams}`);
      
      this.publicaciones = response.items || response.data || response || [];
      this.total = response.total || this.publicaciones.length;

      this.renderTable();
      this.updatePagination();
    } catch (err) {
      console.error('Error al cargar publicaciones:', err);
      showToast('Error al cargar la lista de publicaciones', 'error');
    }
  }

  renderTable() {
    const tbody = this.container.querySelector('#publicacionesTbody');
    if (!tbody) return;

    if (this.publicaciones.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="7" style="padding: 3.5rem 1rem; text-align: center; color: var(--text-muted, #64748b);">
            <div style="display: flex; flex-direction: column; align-items: center; gap: 0.75rem;">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="width: 42px; height: 42px; color: #94a3b8;">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                <polyline points="9 22 9 12 15 12 15 22"/>
              </svg>
              <span style="font-weight: 500; font-size: 0.95rem;">No se encontraron publicaciones con los filtros aplicados.</span>
            </div>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = this.publicaciones.map(pub => {
      // Captura flexible de ID
      const pubId = pub.id || pub._id || pub.docId || pub.propId;

      // Resolución completa del nombre de usuario / propietario desde BD
      const propietarioNombre = 
        pub.propietarioNombre || 
        pub.propietario?.nombre || 
        pub.propietario?.nombreCompleto || 
        pub.propietario?.displayName || 
        pub.usuario?.nombre || 
        pub.usuario?.nombreCompleto || 
        pub.usuario?.displayName || 
        pub.usuarioNombre || 
        pub.usuarioEmail || 
        pub.propietarioEmail || 
        pub.propietario?.email || 
        'Propietario sin nombre';

      // Teléfono o contacto del propietario
      const propietarioTel = 
        pub.propietarioPhone || 
        pub.propietario?.telefono || 
        pub.propietario?.phone || 
        pub.usuario?.telefono || 
        pub.usuarioTelefono || 
        pub.telefono || 
        '';

      const imagenUrl = pub.imagenPrincipal || pub.imagenes?.[0] || '/assets/placeholder-house.svg';
      const estado = pub.estado || 'pendiente';
      const esDestacado = Boolean(pub.destacado);

      return `
        <tr style="border-bottom: 1px solid var(--border-color, #f1f5f9); vertical-align: middle; transition: background-color 0.15s ease;" onmouseover="this.style.backgroundColor='var(--hover-bg, #f8fafc)'" onmouseout="this.style.backgroundColor='transparent'">
          
          <!-- Inmueble (Imagen Preview + Título + Ubicación) -->
          <td style="padding: 0.875rem 1rem;">
            <div style="display: flex; align-items: center; gap: 0.875rem;">
              <img src="${imagenUrl}" alt="Portada" style="width: 48px; height: 48px; border-radius: 8px; object-fit: cover; background: #e2e8f0; border: 1px solid #e2e8f0; flex-shrink: 0;" onerror="this.src='/assets/placeholder-house.svg'">
              <div style="min-width: 0;">
                <strong style="display: block; font-size: 0.875rem; font-weight: 600; color: var(--text-color, #0f172a); text-overflow: ellipsis; overflow: hidden; white-space: nowrap; max-width: 220px;" title="${pub.titulo || 'Sin título'}">
                  ${pub.titulo || 'Sin título'}
                </strong>
                <small style="color: var(--text-muted, #64748b); font-size: 0.775rem; display: block; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;">
                  ${pub.ciudad || 'Sin ciudad'} ${pub.distrito ? '• ' + pub.distrito : ''}
                </small>
              </div>
            </div>
          </td>

          <!-- Propietario -->
          <td style="padding: 0.875rem 1rem;">
            <div>
              <strong style="display: block; font-size: 0.85rem; font-weight: 500; color: var(--text-color, #1e293b);">${propietarioNombre}</strong>
              ${propietarioTel ? `<small style="color: var(--text-muted, #64748b); font-size: 0.75rem; display: flex; align-items: center; gap: 4px; margin-top: 2px;">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:12px;height:12px;"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg> ${propietarioTel}
              </small>` : ''}
            </div>
          </td>

          <!-- Precio -->
          <td style="padding: 0.875rem 1rem; font-weight: 600; color: var(--text-color, #0f172a); font-size: 0.875rem; white-space: nowrap;">
            ${formatCurrency ? formatCurrency(pub.precio || 0) : 'S/ ' + (pub.precio || 0)}
          </td>

          <!-- Estado Actual (Badge) -->
          <td style="padding: 0.875rem 1rem;">
            <span class="badge badge-${this.getBadgeClass(estado)}" style="padding: 0.25rem 0.625rem; border-radius: 9999px; font-weight: 600; font-size: 0.7rem; letter-spacing: 0.025em; display: inline-flex; align-items: center; gap: 0.375rem; ${this.getBadgeStyle(estado)}">
              <span style="width: 6px; height: 6px; border-radius: 50%; background-color: currentColor;"></span>
              ${estado.toUpperCase()}
            </span>
          </td>

          <!-- Destacado -->
          <td style="padding: 0.875rem 1rem; white-space: nowrap;">
            ${esDestacado 
              ? `<span style="background: #fef3c7; color: #b45309; border: 1px solid #fde68a; padding: 0.25rem 0.5rem; border-radius: 0.375rem; font-weight: 600; font-size: 0.725rem; display: inline-flex; align-items: center; gap: 0.25rem;">
                  <svg viewBox="0 0 24 24" fill="currentColor" style="width:12px;height:12px;"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg> DESTACADO
                </span>` 
              : `<span style="color: var(--text-muted, #94a3b8); font-size: 0.8rem; font-weight: 400;">Estándar</span>`}
          </td>

          <!-- Fecha -->
          <td style="padding: 0.875rem 1rem; font-size: 0.8rem; color: var(--text-muted, #64748b); white-space: nowrap;">
            ${formatDate ? formatDate(pub.createdAt) : (pub.createdAt ? new Date(pub.createdAt).toLocaleDateString() : '-')}
          </td>

          <!-- Acciones -->
          <td style="padding: 0.875rem 1rem; text-align: center;">
            <div style="display: flex; align-items: center; justify-content: center; gap: 0.375rem;">
              
              <select class="form-select form-select-sm estado-select" data-id="${pubId}" data-original="${estado}" style="width: auto; padding: 0.3rem 0.5rem; font-size: 0.8rem; border-radius: 0.375rem; border: 1px solid var(--border-color, #cbd5e1); background-color: var(--card-bg, #fff);">
                <option value="activa" ${estado === 'activa' ? 'selected' : ''}>Activa</option>
                <option value="pendiente" ${estado === 'pendiente' ? 'selected' : ''}>Pendiente</option>
                <option value="pausada" ${estado === 'pausada' ? 'selected' : ''}>Pausada</option>
                <option value="rechazada" ${estado === 'rechazada' ? 'selected' : ''}>Rechazada</option>
              </select>

              <button class="btn btn-sm btn-secondary save-status-btn" data-id="${pubId}" style="opacity: 0.5; cursor: not-allowed; padding: 0.3rem 0.625rem; font-size: 0.775rem; border-radius: 0.375rem; font-weight: 500;" title="Guardar cambios" disabled>
                Guardar
              </button>

              <button class="btn btn-sm btn-secondary toggle-destacado-btn" data-id="${pubId}" data-destacado="${esDestacado}" style="padding: 0.3rem 0.5rem; border-radius: 0.375rem; font-size: 0.8rem; display: inline-flex; align-items: center; justify-content: center; color: ${esDestacado ? '#d97706' : 'var(--text-muted, #64748b)'}; border: 1px solid var(--border-color, #cbd5e1);" title="${esDestacado ? 'Quitar destacado' : 'Marcar como destacado'}">
                <svg viewBox="0 0 24 24" fill="${esDestacado ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2" style="width: 14px; height: 14px;">
                  <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                </svg>
              </button>

            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  async guardarEstado(id, nuevoEstado, saveBtn, selectEl) {
    if (!id || id === 'undefined' || id === 'null') {
      showToast('Error: No se encontró un ID válido para esta publicación', 'error');
      return;
    }

    try {
      saveBtn.disabled = true;
      saveBtn.textContent = 'Guardando...';

      const payload = { estado: nuevoEstado };
      
      await this.api.post(`/publicaciones/${id}/estado`, payload);

      showToast(`Estado actualizado a "${nuevoEstado}" correctamente`, 'success');

      selectEl.dataset.original = nuevoEstado;
      saveBtn.classList.add('btn-secondary');
      saveBtn.classList.remove('btn-primary');
      saveBtn.style.opacity = '0.5';
      saveBtn.style.cursor = 'not-allowed';
      saveBtn.disabled = true;
      saveBtn.textContent = 'Guardar';

      await this.loadData();
    } catch (err) {
      console.error('Error al guardar estado:', err);
      showToast('No se pudo guardar el cambio de estado: ' + (err.message || 'Error del servidor'), 'error');
      saveBtn.disabled = false;
      saveBtn.textContent = 'Guardar';
    }
  }

  async toggleDestacado(id, nuevoDestacado) {
    if (!id || id === 'undefined' || id === 'null') {
      showToast('Error: ID inválido para destacar', 'error');
      return;
    }

    try {
      const payload = { destacado: nuevoDestacado };
      
      await this.api.post(`/publicaciones/${id}/destacado`, payload);

      showToast(nuevoDestacado ? 'Publicación destacada' : 'Se quitó el estado destacado', 'success');
      await this.loadData();
    } catch (err) {
      console.error('Error al cambiar destacado:', err);
      showToast('Error al actualizar la opción destacado: ' + (err.message || 'Error del servidor'), 'error');
    }
  }

  getBadgeClass(estado) {
    switch (estado?.toLowerCase()) {
      case 'activa': return 'success';
      case 'pendiente': return 'warning';
      case 'pausada': return 'secondary';
      case 'rechazada': return 'danger';
      default: return 'info';
    }
  }

  getBadgeStyle(estado) {
    switch (estado?.toLowerCase()) {
      case 'activa':
        return 'background-color: #ecfdf5; color: #047857; border: 1px solid #a7f3d0;';
      case 'pendiente':
        return 'background-color: #fffbeb; color: #b45309; border: 1px solid #fde68a;';
      case 'pausada':
        return 'background-color: #f1f5f9; color: #475569; border: 1px solid #cbd5e1;';
      case 'rechazada':
        return 'background-color: #fef2f2; color: #b91c1c; border: 1px solid #fca5a5;';
      default:
        return 'background-color: #f0f9ff; color: #0369a1; border: 1px solid #bae6fd;';
    }
  }

  updatePagination() {
    const totalPages = Math.ceil(this.total / this.limit) || 1;
    
    const info = this.container.querySelector('#paginationInfo');
    if (info) {
      const start = (this.currentPage - 1) * this.limit + 1;
      const end = Math.min(this.currentPage * this.limit, this.total);
      info.textContent = `Mostrando ${this.total > 0 ? start : 0} - ${end} de ${this.total} publicaciones`;
    }

    const prevBtn = this.container.querySelector('#prevPageBtn');
    const nextBtn = this.container.querySelector('#nextPageBtn');

    if (prevBtn) prevBtn.disabled = this.currentPage <= 1;
    if (nextBtn) nextBtn.disabled = this.currentPage >= totalPages;
  }
}