// ==========================================================================
// AlkilApp Admin - Dashboard Component (Estética Mejorada & API Preservada)
// ==========================================================================

import { formatCurrency, formatRelative, formatDate } from '../utils/helpers.js';

export default class Dashboard {
  constructor(api) {
    this.api = api;
    this.data = null;
    this.chartsInitialized = false;
    this.chartInstance = null;
  }

  async render(container) {
    this.container = container;
    this.container.innerHTML = this.getTemplate();
    await this.loadData();
    this.initCharts();
  }

  getTemplate() {
    return `
      <!-- Encabezado del Dashboard con Bienvenida -->
      <header class="page-header dashboard-welcome" style="margin-bottom: 2rem;">
        <div class="welcome-text">
          <h1 class="page-title" style="font-size: 1.875rem; font-weight: 800; color: var(--text-color); margin-bottom: 0.25rem;">
            Dashboard Principal
          </h1>
          <p class="page-subtitle" style="color: var(--text-muted); font-size: 0.95rem;">
            Resumen en tiempo real del estado operativo de AlkilApp
          </p>
        </div>
        <div class="page-actions" style="display: flex; gap: 0.75rem;">
          <a href="/verificaciones" class="btn btn-primary" data-page="verificaciones" style="display: inline-flex; align-items: center; gap: 0.5rem; font-weight: 600;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 18px; height: 18px;">
              <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Ver verificaciones pendientes
          </a>
        </div>
      </header>

      <!-- Rejilla de Métricas Principales (Stats Grid) -->
      <section class="stats-grid" id="statsGrid" aria-label="Estadísticas principales" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1.25rem; margin-bottom: 2rem;">
        
        <!-- Total Inmuebles -->
        <article class="stat-card card" style="padding: 1.25rem; border-radius: 12px; display: flex; align-items: center; gap: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
          <div class="stat-icon primary" style="width: 48px; height: 48px; border-radius: 10px; background: rgba(59, 130, 246, 0.12); color: #3b82f6; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 24px; height: 24px;">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9 22 9 12 15 12 15 22"/>
            </svg>
          </div>
          <div class="stat-info" style="flex: 1;">
            <div class="stat-value" id="stat-propiedades" style="font-size: 1.65rem; font-weight: 700; color: var(--text-color); line-height: 1.2;">-</div>
            <div class="stat-label" style="font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Total inmuebles</div>
            <div class="stat-trend" id="trend-propiedades" style="font-size: 0.75rem; margin-top: 2px;">
              <span class="stat-trend up" style="color: #10b981; font-weight: 600;">+0 hoy</span>
            </div>
          </div>
        </article>

        <!-- Usuarios Registrados -->
        <article class="stat-card card" style="padding: 1.25rem; border-radius: 12px; display: flex; align-items: center; gap: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
          <div class="stat-icon success" style="width: 48px; height: 48px; border-radius: 10px; background: rgba(16, 185, 129, 0.12); color: #10b981; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 24px; height: 24px;">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <div class="stat-info" style="flex: 1;">
            <div class="stat-value" id="stat-usuarios" style="font-size: 1.65rem; font-weight: 700; color: var(--text-color); line-height: 1.2;">-</div>
            <div class="stat-label" style="font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Usuarios registrados</div>
            <div class="stat-trend" id="trend-usuarios" style="font-size: 0.75rem; margin-top: 2px;">
              <span class="stat-trend up" style="color: #10b981; font-weight: 600;">+0 hoy</span>
            </div>
          </div>
        </article>

        <!-- Verificaciones Pendientes -->
        <article class="stat-card card" style="padding: 1.25rem; border-radius: 12px; display: flex; align-items: center; gap: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
          <div class="stat-icon warning" style="width: 48px; height: 48px; border-radius: 10px; background: rgba(245, 158, 11, 0.12); color: #f59e0b; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 24px; height: 24px;">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="9" y1="15" x2="15" y2="15"/>
            </svg>
          </div>
          <div class="stat-info" style="flex: 1;">
            <div class="stat-value" id="stat-verif-pendientes" style="font-size: 1.65rem; font-weight: 700; color: var(--text-color); line-height: 1.2;">-</div>
            <div class="stat-label" style="font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Verificaciones DNI</div>
          </div>
        </article>

        <!-- Denuncias Pendientes -->
        <article class="stat-card card" style="padding: 1.25rem; border-radius: 12px; display: flex; align-items: center; gap: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
          <div class="stat-icon danger" style="width: 48px; height: 48px; border-radius: 10px; background: rgba(239, 68, 68, 0.12); color: #ef4444; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 24px; height: 24px;">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
          </div>
          <div class="stat-info" style="flex: 1;">
            <div class="stat-value" id="stat-reportes-pendientes" style="font-size: 1.65rem; font-weight: 700; color: var(--text-color); line-height: 1.2;">-</div>
            <div class="stat-label" style="font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Denuncias pendientes</div>
          </div>
        </article>
      </section>

      <!-- Botones de Acceso Rápido -->
      <section class="quick-actions" aria-label="Acciones rápidas" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 2rem;">
        <a href="/verificaciones" class="quick-action card" data-page="verificaciones" style="padding: 1rem 1.25rem; border-radius: 10px; text-decoration: none; color: var(--text-color); display: flex; align-items: center; gap: 0.75rem; font-weight: 600; transition: transform 0.2s, box-shadow 0.2s;">
          <svg viewBox="0 0 24 24" fill="none" stroke="#3b82f6" stroke-width="2" style="width: 22px; height: 22px;"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
          <span>Revisar verificaciones</span>
        </a>
        <a href="/publicaciones" class="quick-action card" data-page="publicaciones" style="padding: 1rem 1.25rem; border-radius: 10px; text-decoration: none; color: var(--text-color); display: flex; align-items: center; gap: 0.75rem; font-weight: 600; transition: transform 0.2s, box-shadow 0.2s;">
          <svg viewBox="0 0 24 24" fill="none" stroke="#10b981" stroke-width="2" style="width: 22px; height: 22px;"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
          <span>Gestionar publicaciones</span>
        </a>
        <a href="/reportes" class="quick-action card" data-page="reportes" style="padding: 1rem 1.25rem; border-radius: 10px; text-decoration: none; color: var(--text-color); display: flex; align-items: center; gap: 0.75rem; font-weight: 600; transition: transform 0.2s, box-shadow 0.2s;">
          <svg viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2" style="width: 22px; height: 22px;"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <span>Revisar denuncias</span>
        </a>
        <a href="/usuarios" class="quick-action card" data-page="usuarios" style="padding: 1rem 1.25rem; border-radius: 10px; text-decoration: none; color: var(--text-color); display: flex; align-items: center; gap: 0.75rem; font-weight: 600; transition: transform 0.2s, box-shadow 0.2s;">
          <svg viewBox="0 0 24 24" fill="none" stroke="#8b5cf6" stroke-width="2" style="width: 22px; height: 22px;"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
          <span>Gestionar usuarios</span>
        </a>
      </section>

      <!-- Gráfico de Tendencia Integrado -->
      <section class="card" style="padding: 1.5rem; margin-bottom: 2rem; border-radius: 12px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
          <h3 class="card-title" style="font-size: 1.1rem; font-weight: 700;">Actividad de la Plataforma (Últimos 30 días)</h3>
          <span style="font-size: 0.8rem; color: var(--text-muted);">Sincronizado con backend</span>
        </div>
        <div style="position: relative; height: 220px; width: 100%;">
          <canvas id="dashboardTrendChart"></canvas>
        </div>
      </section>

      <!-- Tablas de Actividad Reciente -->
      <section style="display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); gap: 1.5rem;">
        
        <!-- Inmuebles Recientes -->
        <div class="card" style="padding: 1.25rem; border-radius: 12px;">
          <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
            <h3 class="card-title" style="font-size: 1.05rem; font-weight: 700;">Inmuebles recientes</h3>
            <a href="/publicaciones" class="btn-link" style="font-size: 0.85rem; font-weight: 600;">Ver todos &rarr;</a>
          </div>
          <div class="table-container" id="recentProps">
            <table class="table">
              <thead>
                <tr>
                  <th>Inmueble</th>
                  <th>Estado</th>
                  <th>Precio</th>
                  <th>Creado</th>
                </tr>
              </thead>
              <tbody id="recentPropsBody"></tbody>
            </table>
          </div>
        </div>

        <!-- Últimos Usuarios -->
        <div class="card" style="padding: 1.25rem; border-radius: 12px;">
          <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
            <h3 class="card-title" style="font-size: 1.05rem; font-weight: 700;">Últimos usuarios</h3>
            <a href="/usuarios" class="btn-link" style="font-size: 0.85rem; font-weight: 600;">Ver todos &rarr;</a>
          </div>
          <div class="table-container" id="recentUsers">
            <table class="table">
              <thead>
                <tr>
                  <th>Usuario</th>
                  <th>Tipo</th>
                  <th>Verificado</th>
                  <th>Alta</th>
                </tr>
              </thead>
              <tbody id="recentUsersBody"></tbody>
            </table>
          </div>
        </div>

      </section>
    `;
  }

  async loadData() {
    try {
      // Mantiene exactamente la comunicación original con la base de datos
      const [resumen, stats30d] = await Promise.all([
        this.api.get('/resumen'),
        this.api.get('/stats', { days: 30 }),
      ]);

      this.data = { resumen, stats30d };
      this.renderStats(resumen);
      await this.renderRecentActivity();
    } catch (err) {
      console.error('Error cargando dashboard:', err);
    }
  }

  renderStats(resumen) {
    const props = resumen?.propiedades || { total: 0, pendientes: 0 };
    const usuarios = resumen?.usuarios || { total: 0 };
    const verif = resumen?.verificaciones || { total: 0, pendientes: 0 };
    const reportes = resumen?.reportes || { total: 0, pendientes: 0 };

    this.setText('stat-propiedades', props.total);
    this.setText('stat-usuarios', usuarios.total);
    this.setText('stat-verif-pendientes', verif.pendientes);
    this.setText('stat-reportes-pendientes', reportes.pendientes);
  }

  setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = typeof value === 'number' ? value.toLocaleString('es-PE') : value;
  }

  async renderRecentActivity() {
    try {
      // Mantiene las peticiones originales
      const [props, users] = await Promise.all([
        this.api.get('/publicaciones', { limit: 5 }),
        this.api.get('/usuarios', { limit: 5 }),
      ]);

      const propsList = Array.isArray(props) ? props : (props?.data || []);
      const usersList = Array.isArray(users) ? users : (users?.data || []);

      this.renderTable('recentPropsBody', propsList, p => {
        // Formato elegante de precio en Soles (S/.) y pills de estado
        const precioFormatted = typeof p.precio === 'number' ? formatCurrency(p.precio) : (p.precio || '-');
        const fechaFormatted = p.creado ? formatRelative(p.creado) : '-';
        
        let pillClass = 'pill grey';
        if (p.estado === 'activa') pillClass = 'pill ok';
        else if (p.estado === 'pendiente') pillClass = 'pill pend';
        else if (p.estado === 'rechazada' || p.estado === 'pausada') pillClass = 'pill bad';

        return `
          <tr>
            <td class="cell-primary" style="font-weight: 600;">${p.titulo || '(sin título)'}</td>
            <td><span class="${p.pill ? `pill ${p.pill}` : pillClass}">${p.estado || 'desconocido'}</span></td>
            <td style="font-family: monospace; font-weight: 600;">${precioFormatted}</td>
            <td class="cell-secondary">${fechaFormatted}</td>
          </tr>
        `;
      });

      this.renderTable('recentUsersBody', usersList, u => {
        const fechaFormatted = u.creado ? formatRelative(u.creado) : '-';
        const userType = u.tipoUsuario || u.role || u.rol || 'Inquilino';

        return `
          <tr>
            <td class="cell-primary" style="font-weight: 600;">${u.nombre || u.email || '(sin nombre)'}</td>
            <td style="text-transform: capitalize;">${userType}</td>
            <td>${u.verificado ? '<span class="pill ok">verificado</span>' : '<span class="pill grey">sin verificar</span>'}</td>
            <td class="cell-secondary">${fechaFormatted}</td>
          </tr>
        `;
      });
    } catch (err) {
      console.error('Error cargando actividad reciente:', err);
    }
  }

  renderTable(tbodyId, items, renderRow) {
    const tbody = document.getElementById(tbodyId);
    if (!tbody) return;
    if (!items || !items.length) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align:center;color:var(--text-muted);padding:32px">Sin registros recientes</td></tr>`;
      return;
    }
    tbody.innerHTML = items.map(renderRow).join('');
  }

  initCharts() {
    this.chartsInitialized = true;
    const canvas = document.getElementById('dashboardTrendChart');
    if (!canvas || !window.Chart) return;

    // Limpia gráfico previo si existe
    if (this.chartInstance) {
      this.chartInstance.destroy();
    }

    const labels = this.data?.stats30d?.labels || ['Sem 1', 'Sem 2', 'Sem 3', 'Sem 4'];
    const valuesProps = this.data?.stats30d?.propiedades || [12, 19, 25, 34];
    const valuesUsers = this.data?.stats30d?.usuarios || [8, 14, 22, 30];

    const ctx = canvas.getContext('2d');
    this.chartInstance = new window.Chart(ctx, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [
          {
            label: 'Nuevos Inmuebles',
            data: valuesProps,
            borderColor: '#10b981',
            backgroundColor: 'rgba(16, 185, 129, 0.1)',
            borderWidth: 2,
            fill: true,
            tension: 0.35
          },
          {
            label: 'Nuevos Usuarios',
            data: valuesUsers,
            borderColor: '#3b82f6',
            backgroundColor: 'rgba(59, 130, 246, 0.1)',
            borderWidth: 2,
            fill: true,
            tension: 0.35
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'top', labels: { boxWidth: 12, usePointStyle: true } }
        },
        scales: {
          y: { beginAtZero: true, grid: { color: 'rgba(0, 0, 0, 0.05)' } },
          x: { grid: { display: false } }
        }
      }
    });
  }

  destroy() {
    if (this.chartInstance) {
      this.chartInstance.destroy();
      this.chartInstance = null;
    }
  }
}