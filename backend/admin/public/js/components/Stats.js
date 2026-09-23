// ==========================================================================
// AlkilApp Admin - Stats Component
// ==========================================================================

import { debounce } from '../utils/helpers.js';

export default class Stats {
  constructor(api) {
    this.api = api;
    this.days = 30;
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
          <h1 class="page-title">EstadÃ­sticas</h1>
          <p class="page-subtitle">MÃ©tricas y tendencias de la plataforma</p>
        </div>
        <div class="page-actions">
          <select id="periodSelect" class="form-select" style="width:auto">
            <option value="7"${this.days === 7 ? ' selected' : ''}>Ãšltimos 7 dÃ­as</option>
            <option value="30"${this.days === 30 ? ' selected' : ''}>Ãšltimos 30 dÃ­as</option>
            <option value="90"${this.days === 90 ? ' selected' : ''}>Ãšltimos 90 dÃ­as</option>
          </select>
          <button class="btn btn-secondary" id="exportBtn"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg> Exportar</button>
        </div>
      </header>

      <section class="stats-grid" id="statsGrid">
        <article class="stat-card">
          <div class="stat-icon primary"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 13.73V21a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2"/><path d="M7 3v4"/><path d="M17 3v4"/><path d="M3 8h18"/><path d="M12 17v5"/></svg></div>
          <div class="stat-info">
            <div class="stat-value" id="stat-props">-</div>
            <div class="stat-label">Nuevos inmuebles</div>
          </div>
        </article>
        <article class="stat-card">
          <div class="stat-icon success"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg></div>
          <div class="stat-info">
            <div class="stat-value" id="stat-users">-</div>
            <div class="stat-label">Nuevos usuarios</div>
          </div>
        </article>
        <article class="stat-card">
          <div class="stat-icon warning"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg></div>
          <div class="stat-info">
            <div class="stat-value" id="stat-chats">-</div>
            <div class="stat-label">Nuevos chats</div>
          </div>
        </article>
        <article class="stat-card">
          <div class="stat-icon gold"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
          <div class="stat-info">
            <div class="stat-value" id="stat-verif">-</div>
            <div class="stat-label">Verificaciones</div>
          </div>
        </article>
      </section>

      <section style="display:grid;grid-template-columns:repeat(auto-fit,minmax(400px,1fr));gap:16px;margin-top:24px">
        <div class="card">
          <div class="card-header"><h3 class="card-title">EvoluciÃ³n de inmuebles</h3></div>
          <div class="card-body">
            <div class="chart-placeholder" id="chartProps">GrÃ¡fico de inmuebles por dÃ­a</div>
          </div>
        </div>
        <div class="card">
          <div class="card-header"><h3 class="card-title">EvoluciÃ³n de usuarios</h3></div>
          <div class="card-body">
            <div class="chart-placeholder" id="chartUsers">GrÃ¡fico de usuarios por dÃ­a</div>
          </div>
        </div>
      </section>

      <section style="margin-top:24px">
        <div class="card">
          <div class="card-header">
            <h3 class="card-title">Top zonas por inmuebles</h3>
          </div>
          <div class="card-body">
            <div class="table-container">
              <table class="table">
                <thead><tr><th>Zona</th><th>Inmuebles</th><th>% Total</th></tr></thead>
                <tbody id="topZonesBody"></tbody>
              </table>
            </div>
          </div>
        </div>
      </section>
    `;
  }

  bindEvents() {
    document.getElementById('periodSelect')?.addEventListener('change', e => {
      this.days = parseInt(e.target.value);
      this.loadData();
    });
    document.getElementById('exportBtn')?.addEventListener('click', () => this.exportCSV());
  }

  async loadData() {
    try {
      const [stats, topZones] = await Promise.all([
        this.api.get('/stats', { days: this.days }),
        this.getTopZones(),
      ]);

      this.setText('stat-props', stats.newPropiedades);
      this.setText('stat-users', stats.newUsuarios);
      this.setText('stat-chats', stats.newChats);
      this.setText('stat-verif', stats.newVerif || 0);

      this.renderTopZones(topZones);
      this.initCharts(stats);
    } catch (err) {
      console.error('Error cargando stats:', err);
    }
  }

  async getTopZones() {
    // Get properties and group by zona
    const res = await this.api.get('/publicaciones', { limit: 1000 });
    const counts = {};
    res.data.forEach(p => {
      const zona = p.ciudad || p.barrio || 'Sin zona';
      counts[zona] = (counts[zona] || 0) + 1;
    });
    return Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 10);
  }

  renderTopZones(zones) {
    const tbody = document.getElementById('topZonesBody');
    if (!tbody) return;
    const total = zones.reduce((sum, [, count]) => sum + count, 0);
    tbody.innerHTML = zones.map(([zona, count]) => `
      <tr>
        <td>${this.escape(zona)}</td>
        <td>${count}</td>
        <td>${total ? ((count / total * 100).toFixed(1)) + '%' : '0%'}</td>
      </tr>
    `).join('');
  }

  setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
  }

  initCharts(stats) {
    // Placeholder for Chart.js integration
    // Can be extended with actual charts
  }

  async exportCSV() {
    try {
      const stats = await this.api.get('/stats', { days: this.days });
      const csv = `MÃ©trica,Valor\nInmuebles nuevos,${stats.newPropiedades}\nUsuarios nuevos,${stats.newUsuarios}\nChats nuevos,${stats.newChats}\nVerificaciones,${stats.newVerif || 0}\nPerÃ­odo,${this.days} dÃ­as`;
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = `stats_${this.days}d_${new Date().toISOString().slice(0,10)}.csv`;
      a.click(); URL.revokeObjectURL(url);
    } catch (err) { toastError('Error al exportar'); }
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

