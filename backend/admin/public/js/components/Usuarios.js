// ==========================================================================
// AlkilApp Admin - Usuarios Component
// ==========================================================================

import { formatDate, openModal, confirmModal, showToast, debounce } from '../utils/helpers.js';

const ESTADO_LABELS = {
  activo: 'Activo',
  suspendido: 'Suspendido',
  desactivado: 'Desactivado'
};

const ESTADO_PILLS = {
  activo: 'ok',
  suspendido: 'pend',
  desactivado: 'bad'
};

export default class Usuarios {
  constructor(api) {
    this.api = api;
    this.currentPage = 1;
    this.limit = 20;
    this.filters = { q: '', estado: '', rol: '' };
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
          <h1 class="page-title" style="font-size: 1.5rem; font-weight: 700; color: var(--text-color, #0f172a); margin: 0 0 0.25rem 0;">Gestión de Usuarios</h1>
          <p class="page-subtitle" style="font-size: 0.875rem; color: var(--text-muted, #64748b); margin: 0;">Administra cuentas, verificaciones y moderación de estados por reportes</p>
        </div>
        <div class="page-actions" style="display: flex; gap: 0.75rem;">
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
      <div class="filters-bar" style="display: flex; gap: 1rem; flex-wrap: wrap; align-items: flex-end; margin-bottom: 1.5rem; background: var(--card-bg, #ffffff); padding: 1rem; border-radius: 0.75rem; border: 1px solid var(--border-color, #e2e8f0);">
        <div class="filter-group" style="flex: 1; min-width: 260px;">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Buscar usuario</label>
          <input type="search" id="searchInput" class="form-input" placeholder="Nombre, email, teléfono, DNI..." value="${this.escape(this.filters.q)}" style="width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.375rem; font-size: 0.875rem;">
        </div>

        <div class="filter-group" style="min-width: 160px;">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Estado</label>
          <select id="estadoFilter" class="form-select" style="width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.375rem; font-size: 0.875rem; background-color: var(--card-bg, #fff);">
            <option value="">Todos los estados</option>
            <option value="activo" ${this.filters.estado === 'activo' ? 'selected' : ''}>Activos</option>
            <option value="suspendido" ${this.filters.estado === 'suspendido' ? 'selected' : ''}>Suspendidos</option>
            <option value="desactivado" ${this.filters.estado === 'desactivado' ? 'selected' : ''}>Desactivados</option>
          </select>
        </div>

        <div class="filter-group" style="min-width: 160px;">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Rol</label>
          <select id="rolFilter" class="form-select" style="width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.375rem; font-size: 0.875rem; background-color: var(--card-bg, #fff);">
            <option value="">Todos los roles</option>
            <option value="propietario" ${this.filters.rol === 'propietario' ? 'selected' : ''}>Propietarios</option>
            <option value="inquilino" ${this.filters.rol === 'inquilino' ? 'selected' : ''}>Inquilinos</option>
            <option value="admin" ${this.filters.rol === 'admin' ? 'selected' : ''}>Administradores</option>
          </select>
        </div>

        <div class="filter-actions" style="display: flex; align-items: flex-end;">
          <button class="btn btn-secondary" id="clearFilters" style="display: ${this.filters.q || this.filters.estado || this.filters.rol ? 'inline-flex' : 'none'}; padding: 0.5rem 0.875rem; border-radius: 0.375rem; font-size: 0.875rem;">Limpiar</button>
        </div>
      </div>

      <!-- Tabla de Usuarios -->
      <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.75rem; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.04);">
        <div class="table-container" style="overflow-x: auto;">
          <table class="table" id="usuariosTable" style="width: 100%; border-collapse: collapse; text-align: left;">
            <thead>
              <tr style="background: var(--bg-surface-secondary, #f8fafc); border-bottom: 1px solid var(--border-color, #e2e8f0); font-size: 0.75rem; text-transform: uppercase; color: var(--text-muted, #64748b);">
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Usuario</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Contacto</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Rol</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Email verificado</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Identidad verificada (DNI)</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Estado</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600;">Registro</th>
                <th style="padding: 0.875rem 1rem; font-weight: 600; text-align: center;">Acciones</th>
              </tr>
            </thead>
            <tbody id="usuariosBody">
              <tr>
                <td colspan="8" style="padding: 2.5rem; text-align: center; color: var(--text-muted, #64748b);">
                  Cargando usuarios...
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

    this.container.querySelector('#rolFilter')?.addEventListener('change', e => {
      this.filters.rol = e.target.value;
      this.currentPage = 1;
      this.toggleClearFiltersBtn();
      this.loadData();
    });

    // Limpieza de filtros corregida (sin volver a vincular eventos de nuevo)
    this.container.querySelector('#clearFilters')?.addEventListener('click', () => {
      this.filters = { q: '', estado: '', rol: '' };
      this.currentPage = 1;

      const inputQ = this.container.querySelector('#searchInput');
      const selectEstado = this.container.querySelector('#estadoFilter');
      const selectRol = this.container.querySelector('#rolFilter');

      if (inputQ) inputQ.value = '';
      if (selectEstado) selectEstado.value = '';
      if (selectRol) selectRol.value = '';

      this.toggleClearFiltersBtn();
      this.loadData();
    });

    this.container.querySelector('#refreshBtn')?.addEventListener('click', () => {
      this.loadData();
    });

    // Delegación de eventos en la Tabla
    const tbody = this.container.querySelector('#usuariosBody');

    // Detectar cambios en el selector rápido de estado
    tbody?.addEventListener('change', e => {
      if (e.target.classList.contains('estado-user-select')) {
        const id = e.target.dataset.id;
        const newEstado = e.target.value;
        const originalEstado = e.target.dataset.original;

        const saveBtn = tbody.querySelector(`.save-user-btn[data-id="${id}"]`);
        if (saveBtn) {
          if (newEstado !== originalEstado) {
            saveBtn.classList.remove('btn-secondary');
            saveBtn.classList.add('btn-primary');
            saveBtn.style.opacity = '1';
            saveBtn.disabled = false;
          } else {
            saveBtn.classList.add('btn-secondary');
            saveBtn.classList.remove('btn-primary');
            saveBtn.style.opacity = '0.6';
            saveBtn.disabled = true;
          }
        }
      }
    });

    // Clicks en acciones de la tabla
    tbody?.addEventListener('click', async e => {
      const btn = e.target.closest('button[data-action]');
      if (!btn) return;

      const id = btn.dataset.id;
      const action = btn.dataset.action;

      if (action === 'guardar-estado') {
        const selectEl = tbody.querySelector(`.estado-user-select[data-id="${id}"]`);
        if (selectEl) {
          const nuevoEstado = selectEl.value;
          this.solicitarCambioEstado(id, nuevoEstado);
        }
      } else if (action === 'verificar-email') {
        this.verificarEmailManual(id);
      } else if (action === 'ver') {
        this.verDetalleUsuario(id);
      }
    });

    // Paginación
    this.container.querySelector('#pagination')?.addEventListener('click', e => {
      const btn = e.target.closest('.page-btn');
      if (btn && !btn.disabled) {
        this.currentPage = parseInt(btn.dataset.page, 10);
        this.loadData();
      }
    });
  }

  toggleClearFiltersBtn() {
    const clearBtn = this.container.querySelector('#clearFilters');
    if (clearBtn) {
      const hasFilters = Boolean(this.filters.q || this.filters.estado || this.filters.rol);
      clearBtn.style.display = hasFilters ? 'inline-flex' : 'none';
    }
  }

  async loadData() {
    try {
      const params = {
        q: this.filters.q,
        estado: this.filters.estado,
        rol: this.filters.rol,
        page: this.currentPage,
        limit: this.limit
      };

      const res = await this.api.get('/usuarios', params);
      this.items = res.items || res.data || [];
      this.total = res.total || this.items.length;

      this.renderTable();
      this.renderPagination(this.total, res.page || this.currentPage, res.totalPages || Math.ceil(this.total / this.limit));
    } catch (err) {
      console.error('Error cargando usuarios:', err);
      showToast('Error al cargar la lista de usuarios', 'danger');
    }
  }

  getUserId(u) {
    return u.id || u._id || u.docId || u.userId;
  }

  renderTable() {
    const tbody = this.container.querySelector('#usuariosBody');
    if (!tbody) return;

    if (!this.items.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="7" style="text-align: center; padding: 2.5rem; color: var(--text-muted, #64748b);">
            No se encontraron usuarios registrados
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = this.items.map(u => {
const userId = this.getUserId(u);
      const estado = u.estado || 'activo';
      const pillClass = ESTADO_PILLS[estado] || 'grey';
      const estadoLabel = this.escape(ESTADO_LABELS[estado] || estado);
      const avatarUrl = u.fotoPerfil || '/assets/avatar-placeholder.svg';
      
      // Separate email verification from DNI/identity verification
      const v = u.verification || {};
      const emailVerificado = v.emailVerified === true;
      const identidadVerificada = v.identityVerified === true || v.status === 'aprobado';

      return `
        <tr data-id="${userId}" style="border-bottom: 1px solid var(--border-color, #e2e8f0); vertical-align: middle;">
          <!-- Usuario -->
          <td style="padding: 0.75rem 1rem;">
            <div style="display: flex; align-items: center; gap: 0.75rem;">
              <img src="${this.escape(avatarUrl)}" data-fallback="/assets/avatar-placeholder.svg" alt="Avatar" style="width: 40px; height: 40px; border-radius: 50%; object-fit: cover; background: #e2e8f0;">
              <div>
                <strong style="display: block; font-size: 0.875rem; color: var(--text-color, #0f172a);">${this.escape(u.nombre || 'Sin nombre')}</strong>
                <small style="color: var(--text-muted, #64748b); font-size: 0.75rem;">ID: ${userId}</small>
              </div>
            </div>
          </td>

          <!-- Contacto -->
          <td style="padding: 0.75rem 1rem;">
            <span style="display: block; font-size: 0.875rem; color: var(--text-color, #0f172a);">${this.escape(u.email || '-')}</span>
            <small style="color: var(--text-muted, #64748b); font-size: 0.75rem;">${this.escape(u.telefono || '-')}</small>
          </td>

          <!-- Rol -->
          <td style="padding: 0.75rem 1rem;">
            <span class="badge" style="text-transform: capitalize; padding: 0.25rem 0.5rem; border-radius: 0.375rem; font-size: 0.75rem; font-weight: 600; background: var(--bg-surface-secondary, #f1f5f9); color: var(--text-color, #334155);">${this.escape(u.rol || 'inquilino')}</span>
          </td>

          <!-- Email Verificado -->
          <td style="padding: 0.75rem 1rem;">
            ${emailVerificado 
              ? `<span class="pill ok" style="font-size: 0.75rem; padding: 0.2rem 0.5rem; border-radius: 1rem; background: rgba(16,185,129,0.1); color: #10b981; font-weight: 600;">✓ Email verificado</span>` 
              : `<span class="pill pend" style="font-size: 0.75rem; padding: 0.2rem 0.5rem; border-radius: 1rem; background: rgba(245,158,11,0.1); color: #f59e0b; font-weight: 600;">Email pendiente</span>`}
          </td>

          <!-- Identidad Verificada (DNI) -->
          <td style="padding: 0.75rem 1rem;">
            ${identidadVerificada 
              ? `<span class="pill ok" style="font-size: 0.75rem; padding: 0.2rem 0.5rem; border-radius: 1rem; background: rgba(16,185,129,0.1); color: #10b981; font-weight: 600;">✓ DNI verificado</span>` 
              : `<span class="pill pend" style="font-size: 0.75rem; padding: 0.2rem 0.5rem; border-radius: 1rem; background: rgba(245,158,11,0.1); color: #f59e0b; font-weight: 600;">DNI pendiente</span>`}
          </td>

          <!-- Estado Badge -->
          <td style="padding: 0.75rem 1rem;">
            <span class="pill ${pillClass}">
              ${estadoLabel}
            </span>
          </td>

          <!-- Fecha -->
          <td style="padding: 0.75rem 1rem; font-size: 0.85rem; color: var(--text-muted, #64748b);">
            ${u.creado ? formatDate(u.creado) : '-'}
          </td>

          <!-- Acciones: Cambiar Estado + Ver + Verificar Email -->
          <td style="padding: 0.75rem 1rem; text-align: center;">
            <div style="display: flex; align-items: center; justify-content: center; gap: 6px;">
              
              <!-- Dropdown Cambio de Estado -->
              <select class="form-select form-select-sm estado-user-select" data-id="${userId}" data-original="${estado}" style="width: auto; padding: 4px 8px; font-size: 0.825rem; border-radius: 0.375rem; border: 1px solid var(--border-color, #cbd5e1);">
                <option value="activo" ${estado === 'activo' ? 'selected' : ''}>Activo</option>
                <option value="suspendido" ${estado === 'suspendido' ? 'selected' : ''}>Suspender</option>
                <option value="desactivado" ${estado === 'desactivado' ? 'selected' : ''}>Desactivar</option>
              </select>

              <!-- Botón Guardar -->
              <button class="btn btn-sm btn-secondary save-user-btn" data-action="guardar-estado" data-id="${userId}" disabled style="opacity: 0.6; padding: 4px 10px; font-size: 0.8rem; cursor: pointer;" title="Guardar cambio de estado">
                Guardar
              </button>

              ${!v.emailVerified ? `
                <!-- Botón Verificar Email -->
                <button class="btn btn-sm btn-primary verify-email-btn" data-action="verificar-email" data-id="${userId}" title="Verificar email manualmente" style="padding: 4px 10px; font-size: 0.8rem; cursor: pointer;" title="Marcar email como verificado manualmente">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 6a10 10 0 1 0-3.64 9.36"/><path d="M22 21 15 15"/></svg>
                  <span style="margin-left: 4px;">Verificar email</span>
                </button>
              ` : ''}

              <!-- Botón Ver Detalle -->
              <button class="btn btn-sm btn-ghost" data-action="ver" data-id="${userId}" title="Ver expediente/reportes" style="padding: 4px 8px; cursor: pointer;">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              </button>

            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  async solicitarCambioEstado(id, nuevoEstado) {
    const usuario = this.items.find(u => String(this.getUserId(u)) === String(id));
    const nombre = usuario ? usuario.nombre : 'el usuario';

    if (nuevoEstado === 'activo') {
      const confirm = await confirmModal(`¿Deseas activar nuevamente a ${nombre}?`, {
        title: 'Reactivar Usuario',
        confirmText: 'Activar'
      });
      if (confirm) {
        await this.ejecutarCambioEstado(id, 'activo', 'Reactivado por administración');
      } else {
        await this.loadData();
      }
      return;
    }

    // Modal para capturar motivo o vinculación a reporte en caso de Suspender o Desactivar
    const modalHtml = `
      <div style="display: grid; gap: 14px;">
        <p style="margin: 0; font-size: 0.9rem; color: var(--text-color, #0f172a);">
          Vas a cambiar el estado de <strong>${this.escape(nombre)}</strong> a 
          <span class="pill ${ESTADO_PILLS[nuevoEstado]}">${ESTADO_LABELS[nuevoEstado].toUpperCase()}</span>.
        </p>

        <div class="form-group">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">Motivo o razón de la medida (Obligatorio)</label>
          <textarea id="motivoEstado" class="form-input" rows="3" placeholder="Ej: Reporte #1042 - Incumplimiento de políticas de publicación o fraude." style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.375rem; font-size: 0.875rem;"></textarea>
        </div>

        <div class="form-group">
          <label class="filter-label" style="display: block; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted, #64748b); margin-bottom: 0.375rem;">ID de Reporte / Denuncia (Opcional)</label>
          <input type="text" id="reporteIdRef" class="form-input" placeholder="Ej: REP-8921" style="width: 100%; padding: 0.5rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.375rem; font-size: 0.875rem;">
        </div>
      </div>
    `;

    const footer = `
      <button class="btn btn-secondary" id="cancelEstadoBtn" data-dismiss="modal" style="padding: 0.5rem 1rem; font-size: 0.875rem; cursor: pointer;">Cancelar</button>
      <button class="btn btn-danger" id="confirmEstadoBtn" style="padding: 0.5rem 1rem; font-size: 0.875rem; cursor: pointer;">Confirmar Medida</button>
    `;

    await openModal(modalHtml, {
      title: `Confirmar ${ESTADO_LABELS[nuevoEstado]} Usuario`,
      footer
    });

    const closeCurrentModal = () => {
      const modalEl = document.querySelector('.modal-backdrop') || document.querySelector('.modal');
      modalEl?.remove();
    };

    document.getElementById('cancelEstadoBtn')?.addEventListener('click', () => {
      this.loadData();
    });

    document.getElementById('confirmEstadoBtn')?.addEventListener('click', async (e) => {
      const btn = e.currentTarget;
      const motivo = document.getElementById('motivoEstado')?.value.trim();
      const reporteId = document.getElementById('reporteIdRef')?.value.trim();

      if (!motivo) {
        showToast('Debes ingresar un motivo para registrar la sanción', 'danger');
        return;
      }

      btn.disabled = true;
      btn.textContent = 'Procesando...';

      closeCurrentModal();
      await this.ejecutarCambioEstado(id, nuevoEstado, motivo, reporteId);
    });
  }

  async verificarEmailManual(id) {
    if (!id || id === 'undefined' || id === 'null') {
      showToast('Error: ID de usuario inválido', 'danger');
      return;
    }

    const usuario = this.items.find(u => String(this.getUserId(u)) === String(id));
    const nombre = usuario ? usuario.nombre : 'el usuario';

    const confirm = await confirmModal(
      `¿Verificar manualmente el email de <strong>${this.escape(nombre)}</strong>?<br><br>Esto marcará el email como verificado sin requerir el código de 6 dígitos.`,
      { title: 'Verificar email manualmente', confirmText: 'Verificar', danger: false, html: true }
    );

    if (!confirm) return;

    try {
      await this.api.post(`/usuarios/${id}/verificar-email`, {});
      showToast(`Email de ${nombre} verificado manualmente`, 'success');
      await this.loadData();
    } catch (err) {
      console.error('Error al verificar email:', err);
      showToast(`Error: ${err.message || 'No se pudo verificar el email'}`, 'danger');
    }
  }

  async ejecutarCambioEstado(id, estado, motivo, reporteId = null) {
    if (!id || id === 'undefined' || id === 'null') {
      showToast('Error: No se encontró un ID de usuario válido', 'danger');
      console.error('ID inválido recibido en ejecutarCambioEstado:', id);
      return;
    }

    const payload = { estado, motivo, reporteId };

    try {
      await this.api.post(`/usuarios/${id}/estado`, payload);
      showToast(`Estado de usuario cambiado a "${ESTADO_LABELS[estado] || estado}"`, 'success');
    } catch (err) {
      console.error('Error al actualizar estado del usuario:', err);
      showToast(`Error: ${err.message || 'No se pudo actualizar el estado'}`, 'danger');
    } finally {
      await this.loadData();
    }
  }

  async verDetalleUsuario(id) {
    const u = this.items.find(item => String(this.getUserId(item)) === String(id));
    if (!u) return;

    const html = `
      <div style="display: grid; gap: 16px;">
        <div style="display: flex; align-items: center; gap: 16px;">
            <img src="${this.escape(u.fotoPerfil || '/assets/avatar-placeholder.svg')}" data-fallback="/assets/avatar-placeholder.svg" style="width: 60px; height: 60px; border-radius: 50%; object-fit: cover;">
          <div>
            <h3 style="margin: 0; font-size: 1.125rem; font-weight: 700; color: var(--text-color, #0f172a);">${this.escape(u.nombre)}</h3>
            <p style="margin: 0; color: var(--text-muted, #64748b); font-size: 0.875rem;">${this.escape(u.email)}</p>
            <span class="pill ${ESTADO_PILLS[u.estado || 'activo']}" style="margin-top: 6px; inline-block;">${ESTADO_LABELS[u.estado || 'activo']}</span>
          </div>
        </div>

        <hr style="border: 0; border-top: 1px solid var(--border-color, #e2e8f0); margin: 4px 0;">

        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; font-size: 0.875rem; color: var(--text-color, #334155);">
          <div><strong>Teléfono:</strong> ${this.escape(u.telefono || '-')}</div>
          <div><strong>Documento (DNI/CE):</strong> ${this.escape(u.numeroDocumento || '-')}</div>
          <div><strong>Rol:</strong> ${this.escape(u.rol || 'inquilino')}</div>
          <div><strong>Verificación Identidad:</strong> ${u.verificado ? 'Sí' : 'No'}</div>
          <div><strong>Fecha Registro:</strong> ${u.creado ? formatDate(u.creado) : '-'}</div>
        </div>

        ${u.historialSanciones && u.historialSanciones.length ? `
          <div style="margin-top: 8px;">
            <strong style="font-size: 0.875rem; color: var(--text-color, #0f172a);">Historial de Medidas / Reportes:</strong>
            <ul style="padding-left: 20px; margin-top: 6px; font-size: 0.825rem; color: var(--text-muted, #64748b); max-height: 150px; overflow-y: auto;">
              ${u.historialSanciones.map(h => `
                <li style="margin-bottom: 4px;">
                  [${formatDate(h.fecha)}] <strong>${(h.estado || '').toUpperCase()}:</strong> ${this.escape(h.motivo)} ${h.reporteId ? `(Reporte: ${this.escape(h.reporteId)})` : ''}
                </li>
              `).join('')}
            </ul>
          </div>
        ` : ''}
      </div>
    `;

    await openModal(html, {
      title: 'Expediente de Usuario',
      footer: `<button class="btn btn-secondary" data-dismiss="modal" style="padding: 0.5rem 1rem; font-size: 0.875rem; cursor: pointer;">Cerrar</button>`
    });
  }

  renderPagination(total, page, totalPages) {
    const info = this.container.querySelector('#paginationInfo');
    if (info) {
      const start = (this.currentPage - 1) * this.limit + 1;
      const end = Math.min(this.currentPage * this.limit, total);
      info.textContent = `Mostrando ${total > 0 ? start : 0} - ${end} de ${total} usuarios`;
    }

    const container = this.container.querySelector('#pagination');
    if (!container) return;

    if (totalPages <= 1) {
      container.innerHTML = '';
      return;
    }

    let html = '';
    html += `<button class="page-btn" data-page="${page - 1}" ${page === 1 ? 'disabled' : ''} style="padding: 0.375rem 0.625rem; border-radius: 0.375rem; border: 1px solid var(--border-color, #cbd5e1); background: var(--card-bg, #fff); cursor: pointer;"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg></button>`;

    for (let i = 1; i <= totalPages; i++) {
      html += `<button class="page-btn ${i === page ? 'active' : ''}" data-page="${i}" style="padding: 0.375rem 0.625rem; border-radius: 0.375rem; border: 1px solid var(--border-color, #cbd5e1); background: ${i === page ? 'var(--primary-color, #3b82f6)' : 'var(--card-bg, #fff)'}; color: ${i === page ? '#fff' : 'inherit'}; cursor: pointer;">${i}</button>`;
    }

    html += `<button class="page-btn" data-page="${page + 1}" ${page === totalPages ? 'disabled' : ''} style="padding: 0.375rem 0.625rem; border-radius: 0.375rem; border: 1px solid var(--border-color, #cbd5e1); background: var(--card-bg, #fff); cursor: pointer;"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg></button>`;

    container.innerHTML = html;
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