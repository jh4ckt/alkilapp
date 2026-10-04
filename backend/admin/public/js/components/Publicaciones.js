// ==========================================================================
// AlkilApp Admin - Publicaciones Component
// ==========================================================================
import { formatCurrency, formatDate, showToast, debounce, openModal } from '../utils/helpers.js';

// Estados REALES que usa la app Android y la colección `propiedades`.
// Si esta lista no coincide con lo que hay en Firestore, el <select> no puede
// mostrar el estado actual y el botón Guardar nunca se habilita (bug corregido:
// faltaban under_review / publicado / disponible / finalizado).
export const ESTADOS = [
  { valor: 'under_review', etiqueta: 'En revisión' },
  { valor: 'pendiente', etiqueta: 'Pendiente' },
  { valor: 'publicado', etiqueta: 'Publicado' },
  { valor: 'disponible', etiqueta: 'Disponible' },
  { valor: 'pausada', etiqueta: 'Pausada' },
  { valor: 'finalizado', etiqueta: 'Finalizado' },
];

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
            ${ESTADOS.map(e => `<option value="${e.valor}" ${this.filters.estado === e.valor ? 'selected' : ''}>${e.etiqueta}</option>`).join('')}
          </select>
        </div>

        <div class="filter-group" style="flex: 1; min-width: 160px;">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Destacado</label>
          <select id="destacadoFilter" class="form-select" style="width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.5rem; font-size: 0.875rem; background-color: var(--card-bg, #fff);">
            <option value="">Todos</option>
            <option value="si" ${this.filters.destacado === 'si' ? 'selected' : ''}>Sí (Destacadas)</option>
            <option value="no" ${this.filters.destacado === 'no' ? 'selected' : ''}>No</option>
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
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Operación</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Tipo Inmueble</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Precio</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Estado Actual</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Destacado</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Días dest. / Vence</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Fecha</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600; text-align: center; white-space: nowrap; min-width: 320px;">Acciones</th>
              </tr>
            </thead>
            <tbody id="publicacionesTbody">
              <tr>
                <td colspan="10" style="padding: 3rem; text-align: center; color: var(--text-muted, #64748b);">
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

    // Delegación de eventos para clicks en guardar, aprobar o toggle destacado
    tbody?.addEventListener('click', async (e) => {
      // Ver la foto de portada en la fila (no se descarga hasta abrir la ficha)
      const imgCelda = e.target.closest('img[alt="Portada"]');
      if (imgCelda) {
        const fila = imgCelda.closest('tr');
        const idImagen = fila?.querySelector('.ver-preview-btn')?.dataset.id;
        if (idImagen) {
          e.stopPropagation();
          this.verVistaPrevia(idImagen, imgCelda);
        }
        return;
      }

      // Click en el título: ficha completa del inmueble (vista previa)
      const previewBtn = e.target.closest('.ver-preview-btn');
      if (previewBtn) {
        await this.verVistaPrevia(previewBtn.dataset.id);
        return;
      }

      const aprobarBtn = e.target.closest('.aprobar-btn');
      if (aprobarBtn) {
        const id = aprobarBtn.dataset.id;
        // Aprobar abre la vista previa: publicar un inmueble sin haberlo visto
        // es justo el error que este panel debe evitar.
        this.verVistaPrevia(id, null, async () => {
          showToast('Aprobando publicación...', 'info');
          await this.guardarEstado(id, 'publicado', aprobarBtn, null, 'publicado');
        });
        return;
      }

      const saveBtn = e.target.closest('.save-status-btn');
      if (saveBtn && !saveBtn.disabled) {
        const id = saveBtn.dataset.id;
        const selectEl = tbody.querySelector(`.estado-select[data-id="${id}"]`);
        if (!selectEl) return;

        const nuevoEstado = selectEl.value;
        await this.guardarEstado(id, nuevoEstado, saveBtn, selectEl);
        return;
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
          <td colspan="10" style="padding: 3.5rem 1rem; text-align: center; color: var(--text-muted, #64748b);">
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

      // El listado ya no trae fotos (son base64 de ~100KB y hacia que la
      // respuesta pesara mas de 1MB); el backend manda totalFotos y la portada
      // se pide al abrir la vista previa. Aqui solo el placeholder.
      const totalFotos = Number(pub.totalFotos) || 0;
      const estado = pub.estado || 'pendiente';
      // La collection guarda el destacado en `isFeatured` (no `destacado`): leer
      // `destacado` dejaba la estrella siempre vacia y el toggle nunca acertaba.
      const esDestacado = pub.isFeatured === true || Boolean(pub.destacado);

      // XSS: titulo, ciudad, distrito, nombre, telefono, tipo y destacadoInfo los
      // escribe CUALQUIER usuario de la app. Este componente era el UNICO que no
      // pasaba todo por this.escape (Usuarios/Soporte/Reportes/Verificaciones si lo
      // hacen), asi que un titulo como `"><img src=x onerror=...>` se ejecutaba con
      // la sesion del admin. Se escapan tambien los atributos (title/src/data-*).
      const e = (v) => this.escape(v == null ? '' : v);
      const escTitulo = e(pub.titulo || 'Sin título');
      const escCiudad = e(pub.ciudad || 'Sin ciudad');
      const escDistrito = e(pub.distrito);
      const escDuenio = e(propietarioNombre);
      const escTelefono = e(propietarioTel);
      const escOperacion = e(pub.operacion || 'alquiler');
      const escTipo = e(pub.tipo || 'departamento');
      const escEstado = e(estado);
      const escDestacadoTipo = e(pub.destacadoInfo?.tipo);
      const escFecha = e(formatDate ? formatDate(pub.creado || pub.createdAt) : (pub.creado || pub.createdAt || '-'));
      // El id lo usa el servidor como clave de Firestore: solo se admiten letras,
      // digitos y los separadores de ruta, para que no pueda romper el atributo.
      const escId = String(pubId).replace(/[^A-Za-z0-9_.:-]/g, '');

      return `
        <tr class="table-row-hover" style="border-bottom: 1px solid var(--border-color, #f1f5f9); vertical-align: middle;">
          
          <!-- Inmueble (portada + Título + Ubicación). El título abre la vista previa. -->
          <td style="padding: 0.875rem 1rem;">
            <div style="display: flex; align-items: center; gap: 0.875rem;">
              <div style="position: relative; flex-shrink: 0;">
                <img src="/assets/placeholder-house.svg" alt="Portada" style="width: 48px; height: 48px; border-radius: 8px; object-fit: cover; background: #e2e8f0; border: 1px solid #e2e8f0; display: block;">
                ${totalFotos > 0 ? `<span title="${totalFotos} foto(s) en la publicación" style="position: absolute; right: -4px; bottom: -4px; display: inline-flex; align-items: center; gap: 2px; background: #0f172a; color: #fff; font-size: 0.625rem; font-weight: 600; line-height: 1; padding: 3px 5px; border-radius: 999px; border: 1.5px solid var(--card-bg, #fff);">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:9px;height:9px;"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>${totalFotos}
                </span>` : ''}
              </div>
              <div style="min-width: 0;">
                <button type="button" class="ver-preview-btn" data-id="${escId}" title="Ver vista previa del inmueble" style="display: block; max-width: 220px; padding: 0; border: 0; background: none; text-align: left; cursor: pointer; font-size: 0.875rem; font-weight: 600; color: var(--text-color, #0f172a); text-overflow: ellipsis; overflow: hidden; white-space: nowrap; text-decoration: underline; text-decoration-style: dotted; text-underline-offset: 3px;">
                  ${escTitulo}
                </button>
                <small style="color: var(--text-muted, #64748b); font-size: 0.775rem; display: block; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;">
                  ${escCiudad} ${escDistrito ? '📍 ' + escDistrito : ''}
                </small>
              </div>
            </div>
          </td>

          <!-- Propietario -->
          <td style="padding: 0.875rem 1rem;">
            <div>
              <strong style="display: block; font-size: 0.85rem; font-weight: 500; color: var(--text-color, #1e293b);">${escDuenio}</strong>
              ${propietarioTel ? `<small style="color: var(--text-muted, #64748b); font-size: 0.75rem; display: flex; align-items: center; gap: 4px; margin-top: 2px;">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:12px;height:12px;"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg> ${escTelefono}
              </small>` : ''}
            </div>
          </td>

          <!-- Operación -->
          <td style="padding: 0.875rem 1rem; font-size: 0.85rem; color: var(--text-color, #1e293b); font-weight: 500; text-transform: capitalize; white-space: nowrap;">
            ${escOperacion}
          </td>

          <!-- Tipo Inmueble -->
          <td style="padding: 0.875rem 1rem; font-size: 0.85rem; color: var(--text-color, #1e293b); font-weight: 500; text-transform: capitalize;">
            ${escTipo}
          </td>

          <!-- Precio -->
          <td style="padding: 0.875rem 1rem; font-weight: 600; color: var(--text-color, #0f172a); font-size: 0.875rem; white-space: nowrap;">
            ${formatCurrency ? formatCurrency(pub.precio || 0) : 'S/ ' + (pub.precio || 0)}
          </td>

          <!-- Estado Actual (Badge) -->
          <td style="padding: 0.875rem 1rem;">
            <span class="badge badge-${this.getBadgeClass(estado)}" style="padding: 0.25rem 0.625rem; border-radius: 9999px; font-weight: 600; font-size: 0.7rem; letter-spacing: 0.025em; display: inline-flex; align-items: center; gap: 0.375rem; ${this.getBadgeStyle(estado)}">
              <span style="width: 6px; height: 6px; border-radius: 50%; background-color: currentColor;"></span>
              ${escEstado.toUpperCase()}
            </span>
          </td>

<!-- Destacado -->
            <td style="padding: 0.875rem 1rem; white-space: nowrap;">
              ${pub.destacadoInfo && pub.destacadoInfo.tipo 
                ? `<span style="background: #fef3c7; color: #b45309; border: 1px solid #fde68a; padding: 0.25rem 0.5rem; border-radius: 0.375rem; font-weight: 600; font-size: 0.725rem; display: inline-flex; align-items: center; gap: 0.25rem;">
                    <svg viewBox="0 0 24 24" fill="currentColor" style="width:12px;height:12px;"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg> ${escDestacadoTipo}
                  </span>`
                : (esDestacado 
                  ? `<span style="background: #fef3c7; color: #b45309; border: 1px solid #fde68a; padding: 0.25rem 0.5rem; border-radius: 0.375rem; font-weight: 600; font-size: 0.725rem; display: inline-flex; align-items: center; gap: 0.25rem;">
                      <svg viewBox="0 0 24 24" fill="currentColor" style="width:12px;height:12px;"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg> DESTACADO
                    </span>` 
                  : `<span style="color: var(--text-muted, #94a3b8); font-size: 0.8rem; font-weight: 400;">Estándar</span>`)
              }
            </td>

            <!-- Días dest. / Vence -->
            <td style="padding: 0.875rem 1rem; white-space: nowrap; font-size: 0.8rem;">
              ${esDestacado && pub.destacadoInfo 
                ? `<div style="display: flex; flex-direction: column; gap: 2px; line-height: 1.2;">
                    <span style="font-weight: 600; color: var(--text-color, #0f172a);">${pub.destacadoInfo.dias || pub.destacadoDias || 0} días</span>
                    <span style="font-size: 0.7rem; color: var(--text-muted, #64748b);">Vence: ${pub.destacadoInfo.vence ? formatDate(pub.destacadoInfo.vence) : (pub.featuredUntil ? formatDate(pub.featuredUntil) : '—')}</span>
                    <span style="font-size: 0.7rem; color: ${pub.destacadoInfo.diasRestantes <= 3 ? '#dc2626' : 'var(--text-muted, #64748b)'};">
                      ${pub.destacadoInfo.diasRestantes !== undefined ? `${pub.destacadoInfo.diasRestantes} días restantes` : (pub.destacadoDiasRestantes !== undefined ? `${pub.destacadoDiasRestantes} días restantes` : '—')}
                    </span>
                  </div>`
                : `<span style="color: var(--text-muted, #94a3b8); font-size: 0.8rem;">—</span>`
              }
            </td>

          <!-- Fecha -->
          <td style="padding: 0.875rem 1rem; font-size: 0.8rem; color: var(--text-muted, #64748b); white-space: nowrap;">
            ${escFecha}
          </td>

          <!-- Acciones -->
          <td style="padding: 0.875rem 1rem; text-align: center; white-space: nowrap; min-width: 360px;">
            <div style="display: flex; align-items: center; justify-content: center; gap: 0.25rem; flex-wrap: nowrap;">

              ${estado === 'under_review' ? `
                <button class="btn btn-sm btn-success aprobar-btn" data-id="${escId}" style="padding: 0.25rem 0.5rem; font-size: 0.725rem; border-radius: 0.375rem;" title="Aprobar y publicar esta publicación">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="width:12px;height:12px;"><polyline points="20 6 9 17 4 12"/></svg>
                  Aprobar
                </button>` : ''}

              <select class="form-select form-select-sm estado-select" data-id="${escId}" data-original="${escEstado}" style="width: auto; min-width: 120px; padding: 0.25rem 0.4rem; font-size: 0.75rem; border-radius: 0.375rem; border: 1px solid var(--border-color, #cbd5e1); background-color: var(--card-bg, #fff);">
                ${ESTADOS.map(e => `<option value="${e.valor}" ${estado === e.valor ? 'selected' : ''}>${e.etiqueta}</option>`).join('')}
                ${!ESTADOS.some(e => e.valor === estado) ? `<option value="${escEstado}" selected>${escEstado} (actual)</option>` : ''}
              </select>

              <button class="btn btn-sm btn-secondary save-status-btn" data-id="${escId}" style="opacity: 0.5; cursor: not-allowed; padding: 0.25rem 0.5rem; font-size: 0.725rem; border-radius: 0.375rem; font-weight: 500;" title="Guardar cambios" disabled>
                Guardar
              </button>

              <button class="btn btn-sm btn-secondary toggle-destacado-btn" data-id="${escId}" data-destacado="${esDestacado}" style="padding: 0.25rem 0.4rem; border-radius: 0.375rem; font-size: 0.75rem; display: inline-flex; align-items: center; justify-content: center; color: ${esDestacado ? '#d97706' : 'var(--text-muted, #64748b)'}; border: 1px solid var(--border-color, #cbd5e1);" title="${esDestacado ? 'Quitar destacado' : 'Marcar como destacado'}">
                <svg viewBox="0 0 24 24" fill="${esDestacado ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2" style="width: 13px; height: 13px;">
                  <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                </svg>
              </button>

            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  async guardarEstado(id, nuevoEstado, btn, selectEl, estadoForzado = null) {
    if (!id || id === 'undefined' || id === 'null') {
      showToast('Error: No se encontró un ID válido para esta publicación', 'error');
      return;
    }

    const estadoFinal = estadoForzado || (selectEl ? selectEl.value : nuevoEstado);
    const textoOriginal = btn ? btn.textContent : '';
    const eraBotonGuardar = btn?.classList.contains('save-status-btn');

    try {
      if (btn) {
        btn.disabled = true;
        btn.textContent = 'Guardando...';
      }

      await this.api.post(`/publicaciones/${id}/estado`, { estado: estadoFinal });

      showToast(
        `Estado actualizado a "${this.etiquetaEstado(estadoFinal)}" correctamente`,
        'success'
      );

      if (selectEl) {
        selectEl.dataset.original = estadoFinal;
        selectEl.value = estadoFinal;
      }
      if (btn && eraBotonGuardar) {
        btn.classList.add('btn-secondary');
        btn.classList.remove('btn-primary');
        btn.style.opacity = '0.5';
        btn.style.cursor = 'not-allowed';
        btn.disabled = true;
        btn.textContent = 'Guardar';
      }

      await this.loadData();
    } catch (err) {
      console.error('Error al guardar estado:', err);
      showToast('No se pudo guardar el cambio de estado: ' + (err.message || 'Error del servidor'), 'error');
      if (btn) {
        btn.disabled = eraBotonGuardar ? false : false;
        btn.textContent = textoOriginal;
      }
    }
  }

  async toggleDestacado(id, nuevoDestacado) {
    if (!id || id === 'undefined' || id === 'null') {
      showToast('Error: ID inválido para destacar', 'error');
      return;
    }

    try {
      await this.api.post(`/publicaciones/${id}/destacado`, { destacado: nuevoDestacado });

      showToast(nuevoDestacado ? 'Publicación destacada' : 'Se quitó el estado destacado', 'success');
      await this.loadData();
    } catch (err) {
      console.error('Error al cambiar destacado:', err);
      showToast('Error al actualizar la opción destacado: ' + (err.message || 'Error del servidor'), 'error');
    }
  }

  getBadgeClass(estado) {
    switch (estado?.toLowerCase()) {
      case 'publicado':
      case 'disponible':
        return 'success';
      case 'under_review':
        return 'warning';
      case 'finalizado':
        return 'grey';
      case 'pendiente':
        return 'pendiente';
      case 'pausada':
        return 'pausada';
      default: return 'info';
    }
  }

  getBadgeStyle(estado) {
    // Colores pedidos: en revision = amarillo, publicado = verde, finalizado = gris.
    switch (estado?.toLowerCase()) {
      case 'publicado':
      case 'disponible':
        return 'background-color: #DCFCE7; color: #15803D; border: 1px solid #86EFAC;';
      case 'under_review':
        return 'background-color: #FEF3C7; color: #B45309; border: 1px solid #FCD34D;';
      case 'finalizado':
        return 'background-color: #E2E8F0; color: #475569; border: 1px solid #CBD5E1;';
      case 'pendiente':
        return 'background-color: #FFEDD5; color: #C2410C; border: 1px solid #FDBA74;';
      case 'pausada':
        return 'background-color: #E0F2FE; color: #0369A1; border: 1px solid #7DD3FC;';
      case 'rechazada':
        return 'background-color: #FEE2E2; color: #B91C1C; border: 1px solid #FCA5A5;';
      default:
        return 'background-color: #F1F5F9; color: #334155; border: 1px solid #CBD5E1;';
    }
  }

  // Texto legible del estado (para el <option> desconocido y los toasts)
  etiquetaEstado(estado) {
    return ESTADOS.find(e => e.valor === estado)?.etiqueta || estado;
  }

  // ------------------------------------------------------------------
  // Vista previa del inmueble
  // ------------------------------------------------------------------
  // El listado se queda sin fotos (base64 de ~100KB) y las pide aqui, al abrir.
  // `imagenPinned` es el <img> de la fila cuando se pincha la portada: se
  // rellena con la foto real para no tener que esperar otra recarga.
  // `alAprobar` corre tras pulsar "Aprobar y publicar" dentro del modal.
  async verVistaPrevia(id, imagenPinned = null, alAprobar = null) {
    if (!id) return;
    const e = (v) => this.escape(v == null ? '' : v);

    const modal = openModal(
      '<div style="display: grid; place-items: center; min-height: 180px; color: var(--text-muted, #64748b); font-size: 0.875rem;">Cargando publicación...</div>',
      { title: 'Vista previa del inmueble' }
    );

    let pub;
    try {
      const res = await this.api.get(`/publicaciones/${encodeURIComponent(id)}`);
      pub = res?.data;
    } catch (err) {
      modal.close();
      showToast(`No se pudo cargar la publicación: ${err.message || 'error desconocido'}`, 'error');
      return;
    }
    if (!pub) {
      modal.close();
      showToast('La publicación no existe o ya fue eliminada', 'error');
      return;
    }

    // La foto puede venir como data: base64 o como https. El CSP del panel
    // (img-src 'self' data: https:) no permitiria ningun otro esquema, y el
    // backend ya filtra lo que no sea data:image o https.
    const fotos = (Array.isArray(pub.fotos) ? pub.fotos : []).filter(
      f => typeof f === 'string' && (f.startsWith('data:image') || f.startsWith('https://'))
    );
    if (fotos.length && imagenPinned) {
      imagenPinned.src = fotos[0];
      // Si la URL remota muere, vuelve al placeholder (no a la base64 rota).
      imagenPinned.onerror = () => { imagenPinned.src = '/assets/placeholder-house.svg'; };
    }

    const pill = this.getBadgeStyle(pub.estado);
    const precio = pub.precio == null
      ? '<span style="color: var(--text-muted, #64748b);">Sin precio</span>'
      : formatCurrency(pub.precio, pub.moneda || 'PEN');
    const ambientes = pub.ambientes == null ? '-' : pub.ambientes;
    const superficie = pub.superficieM2 ? `${pub.superficieM2} m²` : '-';
    const ubicacion = [pub.distrito, pub.barrio, pub.ciudad].filter(Boolean).join(' · ') || '-';
    const mapa = (pub.lat != null && pub.lng != null)
      ? `<a href="https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(pub.lat)},${encodeURIComponent(pub.lng)}" target="_blank" rel="noopener noreferrer" style="font-size: 0.8rem; color: #0369a1; text-decoration: underline;">Ver ubicación en Google Maps ↗</a>`
      : '<span style="font-size: 0.8rem; color: #b45309;">Sin coordenadas registradas</span>';

    const prop = pub.propietario;
    const propHtml = prop ? `
      <div style="display: flex; align-items: center; gap: 12px;">
        <div style="width: 44px; height: 44px; border-radius: 50%; background: var(--border-color, #e2e8f0); display: grid; place-items: center; font-weight: 700; color: var(--text-muted, #64748b); font-size: 0.95rem; flex-shrink: 0;">${e((prop.nombre || '?').trim().charAt(0).toUpperCase())}</div>
        <div style="min-width: 0;">
          <div style="font-size: 0.9rem; font-weight: 600; color: var(--text-color, #0f172a);">${e(prop.nombre)}</div>
          <div style="font-size: 0.8rem; color: var(--text-muted, #64748b);">
            ${prop.telefono ? `Tel. ${e(prop.telefono)}` : 'Sin teléfono'}
            ${prop.email ? ` · ${e(prop.email)}` : ''}
          </div>
          <div style="font-size: 0.8rem; margin-top: 3px;">
            ${prop.verificado
              ? '<span style="color: #047857; font-weight: 600;">✓ Identidad verificada</span>'
              : '<span style="color: #b45309; font-weight: 600;">⚠ Identidad sin verificar</span>'}
          </div>
        </div>
      </div>` : '<div style="font-size: 0.85rem; color: #b45309; font-weight: 600;">⚠ Sin ficha de propietario (id borrado o sin registro)</div>';

    const galeria = fotos.length ? `
      <div style="margin-bottom: 16px;">
        <div class="vp-galeria" style="display: flex; gap: 8px; overflow-x: auto; padding-bottom: 6px;">
          ${fotos.map((f, i) => `
            <img class="vp-foto" data-src="${e(f)}" src="${e(f)}" alt="Foto ${i + 1} del inmueble"
                 style="width: 108px; height: 82px; border-radius: 8px; object-fit: cover; border: 1px solid var(--border-color, #e2e8f0); background: #e2e8f0; cursor: zoom-in; flex-shrink: 0; display: block;">`).join('')}
        </div>
        <div style="font-size: 0.75rem; color: var(--text-muted, #64748b); margin-top: 4px;">${pub.totalFotos > fotos.length ? `Mostrando ${fotos.length} de ${pub.totalFotos} fotos · ` : `${fotos.length} foto(s) · `}pincha cualquiera para ampliarla</div>
      </div>`
      : '<div style="padding: 28px; text-align: center; background: var(--bg-color, #f8fafc); border: 1px dashed var(--border-color, #cbd5e1); border-radius: 8px; margin-bottom: 16px; color: var(--text-muted, #64748b); font-size: 0.875rem;">Sin fotos en esta publicación</div>';

    const fila = (label, valor) => `
      <div>
        <div style="font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--text-muted, #94a3b8); font-weight: 600;">${label}</div>
        <div style="font-size: 0.875rem; color: var(--text-color, #334155); font-weight: 500;">${valor}</div>
      </div>`;

    modal.overlay.querySelector('.modal-title').textContent = pub.titulo || 'Vista previa del inmueble';
    modal.overlay.querySelector('.modal-body').innerHTML = `
      ${galeria}
      <div style="display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-bottom: 4px;">
        <span style="font-size: 1.35rem; font-weight: 700; color: var(--text-color, #0f172a);">${precio}</span>
        <span style="font-size: 0.8rem; color: var(--text-muted, #64748b); text-transform: uppercase; letter-spacing: 0.03em;">${e(pub.operacion)}</span>
      </div>
      <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 14px; flex-wrap: wrap;">
        <span class="pill" style="${pill}">${e(this.etiquetaEstado(pub.estado))}</span>
        <span style="font-size: 0.8rem; color: var(--text-muted, #64748b);">${e(pub.tipo)}</span>
        ${pub.isFeatured ? '<span style="font-size: 0.8rem; color: #b45309; font-weight: 600;">★ Destacado</span>' : ''}
        ${pub.estadoCambiadoAdmin ? '<span style="font-size: 0.8rem; color: var(--text-muted, #64748b);">editado por admin</span>' : ''}
      </div>

      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 12px; padding: 14px; background: var(--bg-color, #f8fafc); border: 1px solid var(--border-color, #e2e8f0); border-radius: 8px; margin-bottom: 14px;">
        ${fila('Ambientes', ambientes)}
        ${fila('Superficie', superficie)}
        ${fila('Barrio / Ciudad', e(ubicacion))}
        ${fila('Publicado', pub.publicadoEn ? e(formatDate(pub.publicadoEn)) : '-')}
      </div>

      <div style="font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--text-muted, #94a3b8); font-weight: 600; margin-bottom: 6px;">Dirección</div>
      <div style="font-size: 0.875rem; color: var(--text-color, #334155); margin-bottom: 4px;">${e(pub.direccion || '-')}</div>
      <div style="margin-bottom: 14px;">${mapa}</div>

      ${pub.descripcion ? `
        <div style="font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--text-muted, #94a3b8); font-weight: 600; margin-bottom: 6px;">Descripción del propietario</div>
        <div style="font-size: 0.875rem; color: var(--text-color, #334155); white-space: pre-wrap; margin-bottom: 14px;">${e(pub.descripcion)}</div>` : ''}

      ${pub.comodidades?.length ? `
        <div style="font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--text-muted, #94a3b8); font-weight: 600; margin-bottom: 6px;">Comodidades</div>
        <div style="display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 14px;">
          ${pub.comodidades.map(c => `<span style="font-size: 0.775rem; background: var(--bg-color, #f1f5f9); border: 1px solid var(--border-color, #e2e8f0); border-radius: 999px; padding: 3px 10px; color: var(--text-color, #334155);">${e(c)}</span>`).join('')}
        </div>` : ''}

      <div style="border-top: 1px solid var(--border-color, #e2e8f0); padding-top: 14px;">
        <div style="font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--text-muted, #94a3b8); font-weight: 600; margin-bottom: 8px;">Propietario</div>
        ${propHtml}
      </div>`;

    // Pie con la decisión. Aprobar vive aquí y no solo en la fila: revisar es
    // el paso obligatorio antes de publicar.
    const footer = document.createElement('div');
    footer.className = 'modal-footer';
    footer.style.cssText = 'display: flex; gap: 0.5rem; justify-content: flex-end; flex-wrap: wrap;';
    footer.innerHTML = `
      <button class="btn btn-secondary" data-cerrar style="padding: 0.5rem 1rem; font-size: 0.875rem;">Cerrar</button>
      ${pub.estado === 'under_review' ? '<button class="btn btn-success" data-aprobar style="padding: 0.5rem 1rem; font-size: 0.875rem;">Aprobar y publicar</button>' : ''}`;
    modal.overlay.querySelector('.modal').appendChild(footer);

    footer.querySelector('[data-cerrar]')?.addEventListener('click', () => modal.close());
    footer.querySelector('[data-aprobar]')?.addEventListener('click', async (ev) => {
      const btn = ev.currentTarget;
      btn.disabled = true;
      btn.textContent = 'Aprobando...';
      modal.close();
      if (typeof alAprobar === 'function') await alAprobar();
    });

    // Lupa de fotos: capa fija encima del modal (no dentro de .modal-body, para
    // que el scroll del modal no la mueva).
    const sel = modal.overlay.querySelector('.vp-galeria');
    if (sel) {
      sel.addEventListener('click', (ev) => {
        const img = ev.target.closest('.vp-foto');
        if (!img) return;
        const box = document.createElement('div');
        box.style.cssText = 'position: fixed; inset: 0; background: rgba(15, 23, 42, 0.88); display: grid; place-items: center; z-index: 9999; cursor: zoom-out; padding: 24px;';
        const grande = document.createElement('img');
        grande.src = img.dataset.src;
        grande.alt = 'Foto ampliada';
        grande.style.cssText = 'max-width: 92vw; max-height: 88vh; border-radius: 10px; box-shadow: 0 20px 50px rgba(0,0,0,0.5); background: #0f172a;';
        const quitar = () => box.remove();
        box.addEventListener('click', quitar);
        document.addEventListener('keydown', function esc(ev2) {
          if (ev2.key === 'Escape') { quitar(); document.removeEventListener('keydown', esc); }
        });
        box.appendChild(grande);
        document.body.appendChild(box);
      });
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

  // Escapar texto/valores antes de meterlos en el HTML. Este componente es el
  // UNICO que no lo tenia definido (los demas lo declaran en su clase) y sin el
  // render() reventaba con "this.escape is not a function" y la tabla salia
  // vacia. Misma implementacion que Reportes/Soporte/Usuarios/Verificaciones.
  escape(str) {
    if (!str) return '-';
    return String(str).replace(/[&<>"']/g, c => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    })[c]);
  }
}