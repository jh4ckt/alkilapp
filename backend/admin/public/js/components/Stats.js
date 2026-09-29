// ==========================================================================
// AlkilApp Admin - Stats Component
// ==========================================================================
import { formatCurrency, formatNumber, formatDate, showToast } from '../utils/helpers.js';

// Etiquetas legibles de los periodos que devuelve /api/stats?period=
const RANGOS = {
  '7d': 'Últimos 7 días',
  '30d': 'Últimos 30 días',
  '90d': 'Últimos 3 meses',
  '1y': 'Este año'
};

export default class Stats {
  constructor(api) {
    this.api = api;
    this.period = '30d'; // Período por defecto: 30 días
    this.charts = {}; // Almacena las instancias activas de Chart.js
    this.data = null;
  }

  /**
   * Carga din?mica de Chart.js si no se encuentra globalmente en window.
   * Se sirve desde /vendor/chart.umd.js (Chart.js v4.4.7, version fijada y
   * vendorizada en el repo) en vez de la CDN: la CSP del panel es
   * script-src 'self' y bloqueaba cdn.jsdelivr.net, dejando la vista de
   * estadisticas sin initializing. Servirlo desde el propio origen mantiene el
   * CSP estricto y elimina la dependencia de terceros en runtime.
   */
  async ensureChartJsLoaded() {
    if (window.Chart) return true;

    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = '/vendor/chart.umd.js';
      script.async = true;
      script.onload = () => resolve(true);
      script.onerror = () => reject(new Error('Error al cargar Chart.js (/vendor/chart.umd.js).'));
      document.head.appendChild(script);
    });
  }

  async render(container) {
    this.container = container;
    this.container.innerHTML = this.getTemplate();
    this.bindEvents();

    try {
      await this.ensureChartJsLoaded();
      await this.loadData();
    } catch (err) {
      console.error('Error al inicializar las estadísticas:', err);
      showToast('Error al cargar la librería de gráficos o datos', 'error');
    }
  }

  getTemplate() {
    return `
      <header class="page-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; flex-wrap: wrap; gap: 1rem;">
        <div>
          <h1 class="page-title" style="font-size: 1.5rem; font-weight: 700; color: var(--text-color, #0f172a); margin: 0 0 0.25rem 0;">Estadísticas y Analíticas</h1>
          <p class="page-subtitle" style="font-size: 0.875rem; color: var(--text-muted, #64748b); margin: 0;">Rendimiento, métricas de usuarios y actividad global de AlkilApp</p>
        </div>
        <div class="page-actions" style="display: flex; gap: 0.75rem; align-items: center; flex-wrap: wrap;">
          <div class="filter-group" style="margin: 0;">
            <select id="periodFilter" class="form-select" style="padding: 0.5rem 0.875rem; border: 1px solid var(--border-color, #cbd5e1); border-radius: 0.5rem; font-size: 0.875rem; background-color: var(--card-bg, #ffffff); color: var(--text-color, #0f172a); font-weight: 500; cursor: pointer; outline: none;">
              <option value="7d" ${this.period === '7d' ? 'selected' : ''}>Últimos 7 días</option>
              <option value="30d" ${this.period === '30d' ? 'selected' : ''}>Últimos 30 días</option>
              <option value="90d" ${this.period === '90d' ? 'selected' : ''}>Últimos 3 meses</option>
              <option value="1y" ${this.period === '1y' ? 'selected' : ''}>Este año</option>
            </select>
          </div>
          <button class="btn btn-secondary" id="refreshStatsBtn" style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.5rem 1rem; border-radius: 0.5rem; font-weight: 500; cursor: pointer;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px;">
              <path d="M23 4v6h-6M1 20v-6h6"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            Actualizar
          </button>
        </div>
      </header>

      <!-- KPI Summary Cards Grid -->
      <div class="stats-grid" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 1.25rem; margin-bottom: 1.75rem;">
        
        <!-- KPI 1: Usuarios -->
        <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.875rem; padding: 1.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.04); transition: transform 0.2s, box-shadow 0.2s;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
            <span style="color: var(--text-muted, #64748b); font-size: 0.8125rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;">Nuevos Usuarios</span>
            <div style="width: 38px; height: 38px; border-radius: 0.5rem; background-color: rgba(59, 130, 246, 0.1); color: #3b82f6; display: flex; align-items: center; justify-content: center;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            </div>
          </div>
          <h2 id="kpiUsuarios" style="font-size: 1.875rem; font-weight: 700; color: var(--text-color, #0f172a); margin: 0;">-</h2>
          <p id="notaUsuarios" style="font-size: 0.75rem; color: var(--text-muted, #64748b); margin: 0.2rem 0 0;">-</p>
        </div>

        <!-- KPI 2: Publicaciones -->
        <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.875rem; padding: 1.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.04); transition: transform 0.2s, box-shadow 0.2s;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
            <span style="color: var(--text-muted, #64748b); font-size: 0.8125rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;">Publicaciones Creadas</span>
            <div style="width: 38px; height: 38px; border-radius: 0.5rem; background-color: rgba(16, 185, 129, 0.1); color: #10b981; display: flex; align-items: center; justify-content: center;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
            </div>
          </div>
          <h2 id="kpiPropiedades" style="font-size: 1.875rem; font-weight: 700; color: var(--text-color, #0f172a); margin: 0;">-</h2>
          <p id="notaPropiedades" style="font-size: 0.75rem; color: var(--text-muted, #64748b); margin: 0.2rem 0 0;">-</p>
        </div>

        <!-- KPI 3: Verificaciones -->
        <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.875rem; padding: 1.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.04); transition: transform 0.2s, box-shadow 0.2s;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
            <span style="color: var(--text-muted, #64748b); font-size: 0.8125rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;">Verificaciones</span>
            <div style="width: 38px; height: 38px; border-radius: 0.5rem; background-color: rgba(245, 158, 11, 0.1); color: #f59e0b; display: flex; align-items: center; justify-content: center;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>
            </div>
          </div>
          <h2 id="kpiVerificaciones" style="font-size: 1.875rem; font-weight: 700; color: var(--text-color, #0f172a); margin: 0;">-</h2>
          <p id="notaVerificaciones" style="font-size: 0.75rem; color: var(--text-muted, #64748b); margin: 0.2rem 0 0;">-</p>
        </div>

        <!-- KPI 4: Denuncias -->
        <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.875rem; padding: 1.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.04); transition: transform 0.2s, box-shadow 0.2s;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
            <span style="color: var(--text-muted, #64748b); font-size: 0.8125rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;">Denuncias / Reportes</span>
            <div style="width: 38px; height: 38px; border-radius: 0.5rem; background-color: rgba(239, 68, 68, 0.1); color: #ef4444; display: flex; align-items: center; justify-content: center;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            </div>
          </div>
          <h2 id="kpiReportes" style="font-size: 1.875rem; font-weight: 700; color: var(--text-color, #0f172a); margin: 0;">-</h2>
          <p id="notaReportes" style="font-size: 0.75rem; color: var(--text-muted, #64748b); margin: 0.2rem 0 0;">-</p>
        </div>

      </div>

      <!-- Charts Grid -->
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(420px, 1fr)); gap: 1.5rem; margin-bottom: 1.5rem;">
        
        <!-- Gráfico 1: Crecimiento de Registro de Usuarios -->
        <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.875rem; padding: 1.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.04);">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
            <h3 style="font-size: 1rem; font-weight: 600; color: var(--text-color, #0f172a); margin: 0;">Crecimiento de Usuarios</h3>
            <span style="font-size: 0.75rem; color: var(--text-muted, #64748b); background: #f1f5f9; padding: 0.2rem 0.5rem; border-radius: 0.375rem; font-weight: 500;">Tendencia</span>
          </div>
          <div style="position: relative; height: 280px; width: 100%;">
            <canvas id="chartUsers"></canvas>
          </div>
        </div>

        <!-- Gráfico 2: Publicaciones por Estado -->
        <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.875rem; padding: 1.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.04);">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
            <h3 style="font-size: 1rem; font-weight: 600; color: var(--text-color, #0f172a); margin: 0;">Distribución de Publicaciones</h3>
            <span style="font-size: 0.75rem; color: var(--text-muted, #64748b); background: #f1f5f9; padding: 0.2rem 0.5rem; border-radius: 0.375rem; font-weight: 500;">Estado</span>
          </div>
          <div style="position: relative; height: 280px; width: 100%;">
            <canvas id="chartProperties"></canvas>
          </div>
        </div>

        <!-- Gráfico 3: Actividad de la Plataforma -->
        <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.875rem; padding: 1.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.04);">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
            <h3 style="font-size: 1rem; font-weight: 600; color: var(--text-color, #0f172a); margin: 0;">Altas por Período</h3>
            <span style="font-size: 0.75rem; color: var(--text-muted, #64748b); background: #f1f5f9; padding: 0.2rem 0.5rem; border-radius: 0.375rem; font-weight: 500;">Publicaciones / Usuarios</span>
          </div>
          <div style="position: relative; height: 280px; width: 100%;">
            <canvas id="chartActivity"></canvas>
          </div>
        </div>

        <!-- Gráfico 4: Estado de Verificaciones -->
        <div class="card" style="background: var(--card-bg, #ffffff); border: 1px solid var(--border-color, #e2e8f0); border-radius: 0.875rem; padding: 1.25rem; box-shadow: 0 1px 3px rgba(0,0,0,0.04);">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
            <h3 style="font-size: 1rem; font-weight: 600; color: var(--text-color, #0f172a); margin: 0;">Estatus de Verificaciones DNI/CE</h3>
            <span style="font-size: 0.75rem; color: var(--text-muted, #64748b); background: #f1f5f9; padding: 0.2rem 0.5rem; border-radius: 0.375rem; font-weight: 500;">Identidad</span>
          </div>
          <div style="position: relative; height: 280px; width: 100%;">
            <canvas id="chartVerifications"></canvas>
          </div>
        </div>

      </div>
    `;
  }

  bindEvents() {
    const periodFilter = this.container.querySelector('#periodFilter');
    periodFilter?.addEventListener('change', (e) => {
      this.period = e.target.value;
      this.loadData();
    });

    const refreshBtn = this.container.querySelector('#refreshStatsBtn');
    refreshBtn?.addEventListener('click', () => {
      this.loadData();
    });
  }

  async loadData() {
    try {
      // /stats trae las altas del periodo y las series de las graficas;
      // /resumen trae los totales (verificaciones, reportes). Se necesitan
      // ambos: antes solo se pedia /stats y updateKPIs leia claves que ese
      // endpoint nunca devolvio, asi que los 4 KPI quedaban en 0.
      const [stats, resumen] = await Promise.all([
        this.api.get(`/stats?period=${this.period}`),
        this.api.get('/resumen').catch(() => ({})),
      ]);

      this.data = stats;
      this.updateKPIs(stats, resumen);
      this.renderCharts(stats);
    } catch (err) {
      console.error('Error al cargar datos de estadísticas:', err);
      showToast('Error al sincronizar estadísticas', 'error');
    }
  }

  updateKPIs(stats = {}, resumen = {}) {
    const rango = RANGOS[stats.period] || 'en el período';
    const set = (idValor, idNota, valor, nota) => {
      const el = this.container.querySelector(idValor);
      const elNota = this.container.querySelector(idNota);
      if (el) el.textContent = formatNumber(valor || 0);
      if (elNota) elNota.textContent = nota;
    };

    const totalUsuarios = resumen.usuarios?.total;
    const totalProps = resumen.propiedades?.total;
    set('#kpiUsuarios', '#notaUsuarios', stats.newUsuarios,
      totalUsuarios != null ? `${formatNumber(totalUsuarios)} en total · ${rango.toLowerCase()}` : rango);
    set('#kpiPropiedades', '#notaPropiedades', stats.newPropiedades,
      totalProps != null ? `${formatNumber(totalProps)} en total · ${rango.toLowerCase()}` : rango);
    // Verificaciones y reportes no tienen serie por periodo: se muestran los
    // totales reales, con los pendientes de review.
    set('#kpiVerificaciones', '#notaVerificaciones', resumen.verificaciones?.total,
      resumen.verificaciones?.pendientes ? `${resumen.verificaciones.pendientes} pendientes de revisión` : 'total registradas');
    set('#kpiReportes', '#notaReportes', resumen.reportes?.total,
      resumen.reportes?.pendientes ? `${resumen.reportes.pendientes} pendientes de revisar` : 'total registradas');
  }

  renderCharts(data) {
    this.destroyCharts();

    const chartDefaults = {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          labels: {
            usePointStyle: true,
            boxWidth: 8,
            font: { family: "system-ui, -apple-system, sans-serif", size: 12 }
          }
        }
      }
    };

    // 1. Gráfico de Usuarios (Línea suave con área de gradiente)
    const ctxUsers = this.container.querySelector('#chartUsers')?.getContext('2d');
    if (ctxUsers) {
      const userTrend = data.usuariosTrend || { labels: [], values: [] };

      const gradient = ctxUsers.createLinearGradient(0, 0, 0, 260);
      gradient.addColorStop(0, 'rgba(59, 130, 246, 0.25)');
      gradient.addColorStop(1, 'rgba(59, 130, 246, 0.0)');

      this.charts.users = new window.Chart(ctxUsers, {
        type: 'line',
        data: {
          labels: userTrend.labels,
          datasets: [{
            label: 'Nuevos Registros',
            data: userTrend.values,
            borderColor: '#3b82f6',
            backgroundColor: gradient,
            borderWidth: 2.5,
            fill: true,
            tension: 0.38,
            pointBackgroundColor: '#ffffff',
            pointBorderColor: '#3b82f6',
            pointBorderWidth: 2,
            pointRadius: 4,
            pointHoverRadius: 6
          }]
        },
        options: {
          ...chartDefaults,
          plugins: { legend: { display: false } },
          scales: {
            y: { 
              beginAtZero: true, 
              grid: { color: 'rgba(226, 232, 240, 0.6)' },
              ticks: { font: { size: 11 }, color: '#64748b' }
            },
            x: { 
              grid: { display: false },
              ticks: { font: { size: 11 }, color: '#64748b' }
            }
          }
        }
      });
    }

    // 2. Gráfico de Propiedades (Dona estilizada)
    const ctxProps = this.container.querySelector('#chartProperties')?.getContext('2d');
    if (ctxProps) {
      // Estados reales devueltos por /api/stats. Sin inventar cifras: si la API
      // no responde, la dona queda en cero en vez de mostrar numeros de ejemplo.
      const d = data.propiedadesDist || {};

      this.charts.properties = new window.Chart(ctxProps, {
        type: 'doughnut',
        data: {
          labels: ['Disponibles', 'En revisión', 'Pausadas', 'Finalizadas'],
          datasets: [{
            data: [d.disponible || 0, d.revision || 0, d.pausada || 0, d.finalizado || 0],
            backgroundColor: ['#10b981', '#f59e0b', '#ef4444', '#64748b'],
            borderWidth: 2,
            borderColor: '#ffffff',
            hoverOffset: 4
          }]
        },
        options: {
          ...chartDefaults,
          cutout: '72%',
          plugins: {
            legend: { position: 'bottom' }
          }
        }
      });
    }

    // 3. Gráfico de Actividad de la Plataforma (Barras con esquinas redondeadas)
    const ctxAct = this.container.querySelector('#chartActivity')?.getContext('2d');
    if (ctxAct) {
      // Antes este bloque pintaba "Búsquedas y Visitas" (120, 150, 180...) y
      // "Contactos/Mensajes" inventados: la base no registra ninguna visita, asi
      // que se cambio por las altas reales (publicaciones y usuarios nuevos por
      // tramo) que si devuelve /api/stats.
      const propTrend = data.propiedadesTrend || { labels: [], values: [] };
      const userTrendAlt = data.usuariosTrend || { labels: [], values: [] };

      this.charts.activity = new window.Chart(ctxAct, {
        type: 'bar',
        data: {
          labels: propTrend.labels,
          datasets: [
            {
              label: 'Publicaciones nuevas',
              data: propTrend.values,
              backgroundColor: '#6366f1',
              borderRadius: 6,
              maxBarThickness: 18
            },
            {
              label: 'Usuarios nuevos',
              data: userTrendAlt.values,
              backgroundColor: '#10b981',
              borderRadius: 6,
              maxBarThickness: 18
            }
          ]
        },
        options: {
          ...chartDefaults,
          plugins: { legend: { position: 'bottom' } },
          scales: {
            y: { 
              beginAtZero: true, 
              grid: { color: 'rgba(226, 232, 240, 0.6)' },
              ticks: { font: { size: 11 }, color: '#64748b' }
            },
            x: { 
              grid: { display: false },
              ticks: { font: { size: 11 }, color: '#64748b' }
            }
          }
        }
      });
    }

    // 4. Gráfico de Verificaciones (Doughnut/Pie pulido)
    const ctxVerif = this.container.querySelector('#chartVerifications')?.getContext('2d');
    if (ctxVerif) {
      const verifData = data.verificacionesDist || { aprobadas: 0, pendientes: 0, rechazadas: 0 };

      this.charts.verifications = new window.Chart(ctxVerif, {
        type: 'doughnut',
        data: {
          labels: ['Aprobadas', 'Pendientes', 'Rechazadas'],
          datasets: [{
            data: [verifData.aprobadas || 0, verifData.pendientes || 0, verifData.rechazadas || 0],
            backgroundColor: ['#10b981', '#f59e0b', '#64748b'],
            borderWidth: 2,
            borderColor: '#ffffff',
            hoverOffset: 4
          }]
        },
        options: {
          ...chartDefaults,
          cutout: '65%',
          plugins: { legend: { position: 'bottom' } }
        }
      });
    }
  }

  destroyCharts() {
    Object.keys(this.charts).forEach(key => {
      if (this.charts[key] && typeof this.charts[key].destroy === 'function') {
        this.charts[key].destroy();
      }
    });
    this.charts = {};
  }

  destroy() {
    this.destroyCharts();
    this.container = null;
  }
}