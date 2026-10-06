// ==========================================================================
// AlkilApp Admin - Dashboard Component (Estética Mejorada & API Preservada)
// ==========================================================================

import { formatCurrency, formatRelative, formatDate } from '../utils/helpers.js';
import Metricas from './Metricas.js';

// Un "+0 hoy" en verde se lee como si la plataforma creciera cuando en realidad
// no registro nada; el gris lo deja como dato sin ritmo, que es lo que es.
const verdeSi = (n) => ((n || 0) > 0 ? 'var(--success)' : 'var(--text-muted)');

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
            <div class="stat-trend" style="font-size: 0.75rem; margin-top: 2px; display: flex; flex-wrap: wrap; gap: 0.35rem 0.75rem;">
              <span id="trend-propiedades" style="color: var(--success); font-weight: 600;">+0 hoy</span>
              <span id="trend-prop-activas" style="color: var(--text-muted); font-weight: 500;">Activas: 0</span>
              <span id="trend-prop-destacadas" style="color: var(--warning); font-weight: 600;">Destacadas: 0</span>
            </div>
          </div>
        </article>

        <!-- Usuarios Registrados -->
        <article class="stat-card card" style="padding: 1.25rem; border-radius: 12px; display: flex; align-items: center; gap: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
          <div class="stat-icon success" style="width: 48px; height: 48px; border-radius: 10px; background: rgba(16, 185, 129, 0.12); color: var(--success); display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
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
            <div class="stat-trend" style="font-size: 0.75rem; margin-top: 2px; display: flex; flex-wrap: wrap; gap: 0.35rem 0.75rem;">
              <span id="trend-usuarios" style="color: var(--success); font-weight: 600;">+0 hoy</span>
              <span id="trend-usuarios-verif" style="color: #3b82f6; font-weight: 600;">Verificados: 0</span>
            </div>
          </div>
        </article>

        <!-- Verificaciones Pendientes -->
        <article class="stat-card card" style="padding: 1.25rem; border-radius: 12px; display: flex; align-items: center; gap: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
          <div class="stat-icon warning" style="width: 48px; height: 48px; border-radius: 10px; background: rgba(245, 158, 11, 0.12); color: var(--warning); display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 24px; height: 24px;">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="9" y1="15" x2="15" y2="15"/>
            </svg>
          </div>
          <div class="stat-info" style="flex: 1;">
            <div class="stat-value" id="stat-verif-pendientes" style="font-size: 1.65rem; font-weight: 700; color: var(--text-color); line-height: 1.2;">-</div>
            <div class="stat-label" style="font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Verificaciones DNI</div>
            <div class="stat-trend" style="font-size: 0.75rem; margin-top: 2px;">
              <span id="trend-verif-hoy" style="color: var(--text-muted); font-weight: 500;">+0 hoy</span>
            </div>
          </div>
        </article>

        <!-- Denuncias Pendientes -->
        <article class="stat-card card" style="padding: 1.25rem; border-radius: 12px; display: flex; align-items: center; gap: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
          <div class="stat-icon danger" style="width: 48px; height: 48px; border-radius: 10px; background: rgba(239, 68, 68, 0.12); color: var(--danger); display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 24px; height: 24px;">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
          </div>
          <div class="stat-info" style="flex: 1;">
            <div class="stat-value" id="stat-reportes-pendientes" style="font-size: 1.65rem; font-weight: 700; color: var(--text-color); line-height: 1.2;">-</div>
            <div class="stat-label" style="font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Denuncias pendientes</div>
            <div class="stat-trend" style="font-size: 0.75rem; margin-top: 2px;">
              <span id="trend-reportes-hoy" style="color: var(--text-muted); font-weight: 500;">+0 hoy</span>
            </div>
          </div>
        </article>

        <!-- Soporte Pendiente -->
        <article class="stat-card card" style="padding: 1.25rem; border-radius: 12px; display: flex; align-items: center; gap: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
          <div class="stat-icon" style="width: 48px; height: 48px; border-radius: 10px; background: rgba(20, 184, 166, 0.12); color: #14b8a6; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width: 24px; height: 24px;">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <div class="stat-info" style="flex: 1;">
            <div class="stat-value" id="stat-soporte-pendientes" style="font-size: 1.65rem; font-weight: 700; color: var(--text-color); line-height: 1.2;">-</div>
            <div class="stat-label" style="font-size: 0.85rem; color: var(--text-muted); font-weight: 500;">Tickets pendientes</div>
            <div class="stat-trend" style="font-size: 0.75rem; margin-top: 2px; display: flex; flex-wrap: wrap; gap: 0.35rem 0.75rem;">
              <span id="trend-soporte-hoy" style="color: var(--text-muted); font-weight: 500;">+0 hoy</span>
              <span id="trend-chats-24h" style="color: #6366f1; font-weight: 600;">Chats 24h: 0</span>
            </div>
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
          <svg viewBox="0 0 24 24" fill="none" stroke="var(--success)" stroke-width="2" style="width: 22px; height: 22px;"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
          <span>Gestionar publicaciones</span>
        </a>
        <a href="/reportes" class="quick-action card" data-page="reportes" style="padding: 1rem 1.25rem; border-radius: 10px; text-decoration: none; color: var(--text-color); display: flex; align-items: center; gap: 0.75rem; font-weight: 600; transition: transform 0.2s, box-shadow 0.2s;">
          <svg viewBox="0 0 24 24" fill="none" stroke="var(--danger)" stroke-width="2" style="width: 22px; height: 22px;"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <span>Revisar denuncias</span>
        </a>
        <a href="/usuarios" class="quick-action card" data-page="usuarios" style="padding: 1rem 1.25rem; border-radius: 10px; text-decoration: none; color: var(--text-color); display: flex; align-items: center; gap: 0.75rem; font-weight: 600; transition: transform 0.2s, box-shadow 0.2s;">
          <svg viewBox="0 0 24 24" fill="none" stroke="#8b5cf6" stroke-width="2" style="width: 22px; height: 22px;"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
          <span>Gestionar usuarios</span>
        </a>
        <a href="/soporte" class="quick-action card" data-page="soporte" style="padding: 1rem 1.25rem; border-radius: 10px; text-decoration: none; color: var(--text-color); display: flex; align-items: center; gap: 0.75rem; font-weight: 600; transition: transform 0.2s, box-shadow 0.2s;">
          <svg viewBox="0 0 24 24" fill="none" stroke="#14b8a6" stroke-width="2" style="width: 22px; height: 22px;"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          <span>Atender soporte</span>
        </a>
      </section>

      <!-- KPIs por dimension: como se reparten las publicaciones reales.
           Se alimenta de resumen.distribuciones, que el backend calcula en una
           sola pasada sobre la coleccion propiedades. Cada barra lleva su
           porcentaje sobre el total para que se lea la proporcion sin tener que
           dividir a mano. Sin acentos ni comillas invertidas en este comentario:
           el template los interpretaria y cerraria la cadena. -->
      <section class="card" style="padding: 1.5rem; margin-bottom: 2rem; border-radius: 12px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.25rem; gap: 1rem; flex-wrap: wrap;">
          <h3 class="card-title" style="font-size: 1.1rem; font-weight: 700;">Reparto de publicaciones</h3>
          <a href="/publicaciones" class="btn-link" style="font-size: 0.85rem; font-weight: 600;">Filtrar en Publicaciones &rarr;</a>
        </div>
        <p style="font-size: 0.8rem; color: var(--text-muted); margin: 0 0 1.25rem 0;">
          Cuentas reales de la colección <code>propiedades</code>. El porcentaje es sobre el total de publicaciones.
        </p>
        <div id="dashboardDistribuciones" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 1.5rem;">
          <div style="grid-column: 1 / -1; color: var(--text-muted); font-size: 0.85rem;">Cargando reparto…</div>
        </div>
      </section>

      <!-- Las vistas de reparto y de analitica (Metricas) viven en su propio
           componente para no dejar este archivo con 900 lineas de plantillas.
           Aqui solo se monta el contenedor y se le pasa la respuesta.
           OJO: esto es HTML, no JavaScript. Un "//" aqui se pinta como texto en
           pantalla, porque dentro de un template literal no es un comentario. -->
      <div id="dashboardMetricas"></div>

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
      // Mantiene exactamente la comunicación original con la base de datos.
      // /api/metricas va aparte y con catch propio: lee los mensajes de los
      // chats, así que es la petición más pesada. Si falla, el dashboard
      // tiene que seguir mostrando las estadísticas y el reparto.
      const [resumen, stats30d] = await Promise.all([
        this.api.get('/resumen'),
        this.api.get('/stats', { days: 30 }),
      ]);

      this.data = { resumen, stats30d };
      this.renderStats(resumen);
      this.renderDistribuciones(resumen);
      await this.renderRecentActivity();
      await this.renderMetricas();
    } catch (err) {
      console.error('Error cargando dashboard:', err);
    }
  }

  /** Monta el bloque de analítica (salas de operación, ingresos, precios...). */
  async renderMetricas() {
    const cont = document.getElementById('dashboardMetricas');
    if (!cont) return;
    try {
      const m = await this.api.get('/metricas');
      this.metricas = m;
      new Metricas().render(cont, m);
    } catch (err) {
      console.error('Error cargando métricas:', err);
      cont.innerHTML = `
        <section class="card" style="padding: 1.25rem; border-radius: 12px; margin-bottom: 2rem;">
          <div style="font-size: 0.85rem; color: var(--text-muted);">
            No se pudieron cargar las métricas de negocio
            (${this.escape(err && err.message ? err.message : 'error desconocido')}).
          </div>
        </section>`;
    }
  }

  renderStats(resumen) {
    const props = resumen?.propiedades || { total: 0, pendientes: 0 };
    const usuarios = resumen?.usuarios || { total: 0 };
    const verif = resumen?.verificaciones || { total: 0, pendientes: 0 };
    const reportes = resumen?.reportes || { total: 0, pendientes: 0 };
    const soporte = resumen?.soporte || { total: 0, pendientes: 0 };

    this.setText('stat-propiedades', props.total);
    this.setText('stat-usuarios', usuarios.total);
    this.setText('stat-verif-pendientes', verif.pendientes);
    this.setText('stat-reportes-pendientes', reportes.pendientes);
    this.setText('stat-soporte-pendientes', soporte.pendientes);

    // "+N hoy": el color se apaga cuando es 0 para que un cero no se lea como
    // una cifra sana. Los totales S.I. un "hoy" comparativo mas util.
    this.setTrend('trend-propiedades', `+${props.nuevosHoy || 0} hoy`, verdeSi(props.nuevosHoy));
    this.setTrend('trend-usuarios', `+${usuarios.nuevosHoy || 0} hoy`, verdeSi(usuarios.nuevosHoy));
    this.setTrend('trend-verif-hoy', `+${verif.nuevosHoy || 0} hoy`, verdeSi(verif.nuevosHoy));
    this.setTrend('trend-reportes-hoy', `+${reportes.nuevosHoy || 0} hoy`, verdeSi(reportes.nuevosHoy));
    this.setTrend('trend-soporte-hoy', `+${soporte.nuevosHoy || 0} hoy`, verdeSi(soporte.nuevosHoy));

    // KPIs de contexto
    this.setTrend('trend-prop-activas', `Activas: ${props.activas || 0}`, 'var(--text-muted)');
    this.setTrend('trend-prop-destacadas', `Destacadas: ${props.destacadasActivas || 0}`, (props.destacadasActivas || 0) > 0 ? 'var(--warning)' : 'var(--text-muted)');
    this.setTrend('trend-usuarios-verif', `Verificados: ${usuarios.verificados || 0}`, (usuarios.verificados || 0) > 0 ? '#3b82f6' : 'var(--text-muted)');
    this.setTrend('trend-chats-24h', `Chats 24h: ${resumen?.chats24h || 0}`, (resumen?.chats24h || 0) > 0 ? '#6366f1' : 'var(--text-muted)');
  }

  setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = typeof value === 'number' ? value.toLocaleString('es-PE') : value;
  }

  /**
   * Pinta el reparto de publicaciones por dimension (departamento, ciudad,
   * tipo, operacion, estado, moneda y tramo de precio).
   *
   * Solo se muestran las dimensiones que el backend ya calculo en
   * `resumen.distribuciones`: si alguna falta, se omite en vez de inventar un
   * cero o una lista vacia. El porcentaje es sobre el total de cada dimension,
   * no sobre `props.total`, para que cuadre aunque se filtren publicaciones.
   */
  renderDistribuciones(resumen) {
    const cont = document.getElementById('dashboardDistribuciones');
    if (!cont) return;

    const dist = resumen?.distribuciones;
    if (!dist || typeof dist !== 'object') {
      cont.innerHTML = '<div style="grid-column: 1 / -1; color: var(--text-muted); font-size: 0.85rem;">Sin datos de reparto.</div>';
      return;
    }

    // El orden es el de interes para leer el negocio, no el alfabetico del
    // backend: primero donde se publica, luego que se publica y en que condiciones.
    const ORDEN = [
      ['departamento', 'Departamento', '#3b82f6'],
      ['ciudad', 'Ciudad', '#6366f1'],
      ['tipo', 'Tipo de inmueble', '#14b8a6'],
      ['operacion', 'Operación', '#8b5cf6'],
      ['tramoPrecio', 'Rango de precio', 'var(--warning)'],
      ['estado', 'Estado', 'var(--danger)'],
      ['moneda', 'Moneda', 'var(--text-soft)'],
    ];

    const bloques = [];
    for (const [campo, titulo, color] of ORDEN) {
      const items = dist[campo];
      if (!Array.isArray(items) || items.length === 0) continue;
      const total = items.reduce((a, it) => a + (it.total || 0), 0);
      if (!total) continue;

      // El valor mayoritario se lleva el color; el resto, tonos apagados del
      // mismo. Asi se ve el contraste sin tener que leer los numeros.
      const max = Math.max(...items.map((it) => it.total || 0));

      const filas = items.map((it) => {
        const n = it.total || 0;
        const pct = total ? Math.round((n / total) * 100) : 0;
        const ancho = max ? Math.max(4, Math.round((n / max) * 100)) : 0;
        const colorBarra = n === max ? color : `color-mix(in srgb, ${color} 45%, white)`;
        return `
          <div style="display: grid; grid-template-columns: 1fr auto; gap: 0.25rem 0.75rem; align-items: center; margin-bottom: 0.6rem;">
            <span style="font-size: 0.8rem; color: var(--text-color); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${this.escape(it.etiqueta || it.clave)}</span>
            <span style="font-size: 0.75rem; color: var(--text-muted); font-variant-numeric: tabular-nums;">${n} · ${pct}%</span>
            <div style="grid-column: 1 / -1; height: 6px; border-radius: 999px; background: var(--border-color, #e2e8f0); overflow: hidden;">
              <div style="height: 100%; width: ${ancho}%; border-radius: 999px; background: ${colorBarra};"></div>
            </div>
          </div>`;
      }).join('');

      bloques.push(`
        <div style="border: 1px solid var(--border-color, #e2e8f0); border-radius: 10px; padding: 1rem 1.1rem;">
          <h4 style="margin: 0 0 0.85rem 0; font-size: 0.8rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted);">
            ${titulo} <span style="font-weight: 500; text-transform: none; letter-spacing: 0;">(${total})</span>
          </h4>
          ${filas}
        </div>`);
    }

    cont.innerHTML = bloques.length
      ? bloques.join('')
      : '<div style="grid-column: 1 / -1; color: var(--text-muted); font-size: 0.85rem;">Todavía no hay publicaciones para desglosar.</div>';
  }

  setTrend(id, texto, color) {
    const el = document.getElementById(id);
    if (el) {
      el.textContent = texto;
      el.style.color = color || 'var(--text-muted)';
    }
  }

  /**
   * Escapa texto antes de meterlo en innerHTML. Necesario desde que este
   * componente pinta datos que escribe CUALQUIER usuario de la app (el reparto
   * por dimension viene de `departamento`, `ciudad`, `tipo`... de las
   * publicaciones). Los demas componentes ya lo tenian; aqui faltaba y por eso
   * se pintura sin escapar.
   */
  escape(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
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
        
        // Mismos estados reales que el panel de Publicaciones:
        // publicado/disponible = verde, en revisión = amarillo, finalizado = gris
        let pillClass = 'pill new';
        if (p.estado === 'publicado' || p.estado === 'disponible') pillClass = 'pill ok';
        else if (p.estado === 'under_review' || p.estado === 'pendiente') pillClass = 'pill pend';
        else if (p.estado === 'finalizado' || p.estado === 'pausada') pillClass = 'pill grey';
        else if (p.estado === 'rechazada') pillClass = 'pill bad';

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

    // /api/stats devuelve las series como {propiedadesTrend, usuariosTrend} =
    // {labels, values}. Antes se leian stats30d.labels / .propiedades /
    // .usuarios, que no existen: el grafico caia siempre en los fallbacks de
    // abajo y pintaba una curva inventada (Sem 1: 12, Sem 2: 19...) en vez de
    // los datos reales. Se usa el primer dataset no vacio para las etiquetas.
    const propTrend = this.data?.stats30d?.propiedadesTrend;
    const userTrend = this.data?.stats30d?.usuariosTrend;
    const labels = propTrend?.labels || userTrend?.labels || [];
    const valuesProps = propTrend?.values || [];
    const valuesUsers = userTrend?.values || [];

    // Sin series que dibujar es mejor un canvas vacio que una curva Mentirosa:
    // un panel de administracion no puede inventar actividad.
    if (!labels.length) {
      canvas.insertAdjacentHTML('afterend',
        '<p style="font-size:0.85rem;color:var(--text-muted);margin-top:0.75rem;">Sin actividad registrada en el periodo.</p>');
      return;
    }

    const ctx = canvas.getContext('2d');
    this.chartInstance = new window.Chart(ctx, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [
          {
            label: 'Nuevos Inmuebles',
            data: valuesProps,
            borderColor: 'var(--success)',
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