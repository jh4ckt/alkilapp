// ==========================================================================
// AlkilApp Admin - Dashboard Component
// ==========================================================================

import { formatCurrency, formatRelative } from '../utils/helpers.js';

export default class Dashboard {
  constructor(api) {
    this.api = api;
    this.data = null;
    this.chartsInitialized = false;
  }

  async render(container) {
    this.container = container;
    container.innerHTML = this.getTemplate();
    await this.loadData();
    this.initCharts();
  }

  getTemplate() {
    return `
      <header class="page-header dashboard-welcome">
        <div class="welcome-text">
          <h1>Dashboard</h1>
          <p>Resumen general del estado de AlkilApp</p>
        </div>
        <div class="page-actions">
          <a href="/verificaciones" class="btn btn-primary">Ver verificaciones pendientes</a>
        </div>
      </header>

      <!-- Stats Grid -->
      <section class="stats-grid" id="statsGrid" aria-label="Estadísticas principales">
        <article class="stat-card">
          <div class="stat-icon primary"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 13.73V21a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2"/><path d="M7 3v4"/><path d="M17 3v4"/><path d="M3 8h18"/><path d="M12 17v5"/></svg></div>
          <div class="stat-info">
            <div class="stat-value" id="stat-propiedades">-</div>
            <div class="stat-label">Total inmuebles</div>
            <div class="stat-trend" id="trend-propiedades"><span class="stat-trend up">+0 hoy</span></div>
          </div>
        </article>
        <article class="stat-card">
          <div class="stat-icon success"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg></div>
          <div class="stat-info">
            <div class="stat-value" id="stat-usuarios">-</div>
            <div class="stat-label">Usuarios registrados</div>
            <div class="stat-trend" id="trend-usuarios"><span class="stat-trend up">+0 hoy</span></div>
          </div>
        </article>
        <article class="stat-card">
          <div class="stat-icon warning"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg></div>
          <div class="stat-info">
            <div class="stat-value" id="stat-verif-pendientes">-</div>
            <div class="stat-label">Verificaciones pendientes</div>
          </div>
        </article>
        <article class="stat-card">
          <div class="stat-icon danger"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
          <div class="stat-info">
            <div class="stat-value" id="stat-reportes-pendientes">-</div>
            <div class="stat-label">Denuncias pendientes</div>
          </div>
        </article>
      </section>

      <!-- Quick Actions -->
      <section class="quick-actions" aria-label="Acciones rápidas">
        <a href="/verificaciones" class="quick-action" data-page="verificaciones">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
          <span>Revisar verificaciones</span>
        </a>
        <a href="/publicaciones" class="quick-action" data-page="publicaciones">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 13.73V21a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2"/><path d="M7 3v4"/><path d="M17 3v4"/><path d="M3 8h18"/><path d="M12 17v5"/></svg>
          <span>Gestionar publicaciones</span>
        </a>
        <a href="/reportes" class="quick-action" data-page="reportes">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <span>Revisar denuncias</span>
        </a>
        <a href="/usuarios" class="quick-action" data-page="usuarios">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
          <span>Gestionar usuarios</span>
        </a>
      </section>

      <!-- Charts / Recent Activity -->
      <section style="display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:24px">
        <div class="card">
          <div class="card-header"><h3 class="card-title">Inmuebles recientes</h3></div>
          <div class="table-container" id="recentProps">
            <table class="table"><thead><tr><th>Inmueble</th><th>Estado</th><th>Precio</th><th>Creado</th></tr></thead><tbody id="recentPropsBody"></tbody></table>
          </div>
        </div>
        <div class="card">
          <div class="card-header"><h3 class="card-title">Últimos usuarios</h3></div>
          <div class="table-container" id="recentUsers">
            <table class="table"><thead><tr><th>Usuario</th><th>Tipo</th><th>Verificado</th><th>Alta</th></tr></tbody><tbody id="recentUsersBody"></tbody></table>
          </div>
        </div>
      </section>
    `;
  }

  async loadData() {
    try {
      const [resumen, stats30d] = await Promise.all([
        this.api.get('/resumen'),
        this.api.get('/stats', { days: 30 }),
      ]);

      this.data = { resumen, stats30d };
      this.renderStats(resumen);
      this.renderRecentActivity();
    } catch (err) {
      console.error('Error cargando dashboard:', err);
    }
  }

  renderStats(resumen) {
    const props = resumen.propiedades || { total: 0, pendientes: 0 };
    const usuarios = resumen.usuarios || { total: 0 };
    const verif = resumen.verificaciones || { total: 0, pendientes: 0 };
    const reportes = resumen.reportes || { total: 0, pendientes: 0 };

    this.setText('stat-propiedades', props.total);
    this.setText('stat-usuarios', usuarios.total);
    this.setText('stat-verif-pendientes', verif.pendientes);
    this.setText('stat-reportes-pendientes', reportes.pendientes);
  }

  setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
  }

  async renderRecentActivity() {
    try {
      const [props, users] = await Promise.all([
        this.api.get('/publicaciones', { limit: 5 }),
        this.api.get('/usuarios', { limit: 5 }),
      ]);

      this.renderTable('recentPropsBody', props.data, p => `
        <tr>
          <td class="cell-primary">${p.titulo || '(sin título)'}</td>
          <td><span class="pill ${p.pill}">${p.estado}</span></td>
          <td>${p.precio != null ? p.precio : '-'}</td>
          <td class="cell-secondary">${p.creado}</td>
        </tr>
      `);

      this.renderTable('recentUsersBody', users.data, u => `
        <tr>
          <td class="cell-primary">${u.nombre || '(sin nombre)'}</td>
          <td>${u.tipoUsuario || u.role || '-'}</td>
          <td>${u.verificado ? '<span class="pill ok">verificado</span>' : '<span class="pill grey">sin verificar</span>'}</td>
          <td class="cell-secondary">${u.creado}</td>
        </tr>
      `);
    } catch (err) {
      console.error('Error cargando actividad reciente:', err);
    }
  }

  renderTable(tbodyId, items, renderRow) {
    const tbody = document.getElementById(tbodyId);
    if (!tbody) return;
    if (!items.length) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align:center;color:var(--text-muted);padding:32px">Sin datos</td></tr>`;
      return;
    }
    tbody.innerHTML = items.map(renderRow).join('');
  }

  initCharts() {
    // Placeholder for charts - can be extended with Chart.js or similar
    this.chartsInitialized = true;
  }

  destroy() {
    // Cleanup if needed
  }
}