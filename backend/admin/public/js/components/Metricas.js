// ==========================================================================
// AlkilApp Admin — Métricas de negocio y calidad
// ==========================================================================
//
// Vista de analítica del dashboard: salud de las colas de moderación, tiempo de
// respuesta de los dueños, facturación por destacados, distribución de precios
// con outliers, completitud de las publicaciones y embudo de conversión.
//
// Regla de la casa: si un dato no existe se pinta "—", nunca 0. Un cero
// parece una medición y una falta de datos es otra cosa muy distinta.

const VALOR = '—';

export default class Metricas {
  constructor() {
    this.m = null;
  }

  /** @param {object} m respuesta de GET /api/metricas */
  render(contenedor, m) {
    this.m = m || {};
    if (!contenedor) return;
    contenedor.innerHTML = [
      this.seccionOperacion(),
      this.seccionRespuesta(),
      this.seccionIngresos(),
      this.seccionPrecios(),
      this.seccionCalidad(),
      this.seccionFunnel(),
    ].join('');
  }

  // ---- helpers -----------------------------------------------------------

  esc(v) {
    return String(v == null ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /**
   * Un número o "—". El `titulo` explica por qué está vacío, para que el admin
   * sepa si es "no ha pasado" o "no se está midiendo".
   */
  num(v, decimales = 0, titulo = '') {
    if (v === null || v === undefined || !Number.isFinite(Number(v))) {
      return `<span title="${this.esc(titulo || 'sin datos para calcularlo')}" style="color: var(--text-muted);">${VALOR}</span>`;
    }
    const n = Number(v);
    return decimales ? n.toLocaleString('es-PE', { minimumFractionDigits: decimales, maximumFractionDigits: decimales }) : n.toLocaleString('es-PE');
  }

  /** Texto de "hace Xh", en el formato corto que se lee de un vistazo. */
  desdeHace(horas) {
    if (horas === null || horas === undefined || !Number.isFinite(Number(horas))) return VALOR;
    const h = Number(horas);
    if (h < 1) return `${Math.max(1, Math.round(h * 60))} min`;
    if (h < 48) return `${Math.round(h)} h`;
    return `${Math.round(h / 24)} d`;
  }

  tarjeta(titulo, cuerpo, extra = '') {
    return `
      <div class="card" style="padding: 1.1rem 1.25rem; border-radius: 12px; ${extra}">
        <h4 style="margin: 0 0 0.9rem 0; font-size: 0.78rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted);">${titulo}</h4>
        ${cuerpo}
      </div>`;
  }

  /** Fila etiqueta / valor, el patrón base de todas las sub-tarjetas. */
  fila(etiqueta, valor, color = '') {
    return `
      <div style="display: flex; justify-content: space-between; align-items: baseline; gap: 0.75rem; padding: 0.3rem 0;">
        <span style="font-size: 0.8rem; color: var(--text-muted);">${etiqueta}</span>
        <span style="font-size: 0.85rem; font-weight: 600; color: ${color || 'var(--text-color)'}; font-variant-numeric: tabular-nums;">${valor}</span>
      </div>`;
  }

  barra(pct, color) {
    const ancho = Math.max(0, Math.min(100, Number(pct) || 0));
    return `
      <div style="height: 6px; border-radius: 999px; background: var(--border-color, #e2e8f0); overflow: hidden; margin-top: 0.3rem;">
        <div style="height: 100%; width: ${ancho}%; background: ${color}; border-radius: 999px;"></div>
      </div>`;
  }

  bloque(titulo, subtitulo, contenido, span = '') {
    return `
      <section class="card" style="padding: 1.5rem; border-radius: 12px; ${span}">
        <div style="margin-bottom: 1.1rem;">
          <h3 class="card-title" style="font-size: 1.05rem; font-weight: 700; margin: 0 0 0.2rem 0;">${titulo}</h3>
          ${subtitulo ? `<p style="margin: 0; font-size: 0.8rem; color: var(--text-muted);">${subtitulo}</p>` : ''}
        </div>
        ${contenido}
      </section>`;
  }

  // ---- 1. Salud de las colas --------------------------------------------

  seccionOperacion() {
    const o = this.m.operacion || {};
    const cola = (etiqueta, c, color) => {
      if (!c) return this.tarjeta(etiqueta, `<div style="color: var(--text-muted); font-size: 0.85rem;">${VALOR}</div>`);
      // >48h sin resolver se pinta rojo: es el caso que requiere acción ya.
      const viejo = c.masViejoHoras;
      const colorViejo = viejo === null || viejo === undefined ? 'var(--text-muted)'
        : viejo > 48 ? 'var(--danger)' : viejo > 24 ? 'var(--warning)' : 'var(--text-color)';
      return this.tarjeta(etiqueta, `
        ${this.fila('Pendientes', `<span style="color: ${c.pendientes > 0 ? color : 'var(--text-color)'};">${c.pendientes}</span>`, c.pendientes > 0 ? color : '')}
        ${this.fila('Resueltos', c.resueltos)}
        <div style="border-top: 1px solid var(--border-color, #e2e8f0); margin: 0.5rem 0 0.3rem 0;"></div>
        ${this.fila('Más viejo sin resolver', this.desdeHace(viejo), colorViejo)}
        ${this.fila('Resolución (mediana)', this.num(c.resolucionMedianaHoras, 1, 'Ninguna fue resuelta todavía') + (c.resolucionMedianaHoras !== null ? ' h' : ''))}
        ${this.fila('Resueltas en <24 h', c.sla24hPct === null ? this.num(null, 0, 'Sin casos resueltos') : this.num(c.sla24hPct) + ' %')}
      `);
    };

    return this.bloque(
      'Salud de las colas',
      'Tiempo real de resolución y antigüedad de lo que está sin atender.',
      `<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 1rem;">
        ${cola('Verificaciones', o.verificaciones, '#3b82f6')}
        ${cola('Denuncias', o.reportes, 'var(--danger)')}
        ${cola('Soporte', o.soporte, '#14b8a6')}
      </div>`
    );
  }

  // ---- 2. Respuesta del dueño -------------------------------------------

  seccionRespuesta() {
    const r = this.m.respuesta || {};
    const t = r.tramos || {};
    const totalTramos = (t.menos1h || 0) + (t.h1a24 || 0) + (t.mas24h || 0);
    const pctRespuesta = r.pctConRespuesta;

    const tramoFila = (etiqueta, n, color) => {
      const pct = totalTramos ? Math.round((n / totalTramos) * 100) : 0;
      return `
        <div style="margin-bottom: 0.55rem;">
          <div style="display: flex; justify-content: space-between; font-size: 0.8rem;">
            <span style="color: var(--text-muted);">${etiqueta}</span>
            <span style="font-weight: 600; font-variant-numeric: tabular-nums;">${n} · ${pct} %</span>
          </div>
          ${this.barra(pct, color)}
        </div>`;
    };

    const lentos = (r.masLentos || []).slice(0, 5).map((x) => `
      <tr style="border-bottom: 1px solid var(--border-color, #f1f5f9);">
        <td style="padding: 0.4rem 0.5rem 0.4rem 0; font-size: 0.8rem; color: var(--text-color); max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${this.esc(x.titulo || x.listingId)}</td>
        <td style="padding: 0.4rem 0; text-align: right; font-size: 0.8rem; font-weight: 600; font-variant-numeric: tabular-nums;">${this.desdeHace(x.horas)}</td>
      </tr>`).join('');

    return this.bloque(
      'Respuesta de los dueños',
      'Cuánto tardan en contestar un chat, medido desde el primer mensaje.',
      `<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 1.25rem;">
        ${this.tarjeta('General', `
          ${this.fila('Chats medidos', r.chats !== undefined ? r.chats : VALOR)}
          ${this.fila('Con respuesta del dueño', pctRespuesta === null || pctRespuesta === undefined
            ? this.num(null, 0, 'Ningún chat tiene historial') : this.num(pctRespuesta) + ' %',
            pctRespuesta !== null && pctRespuesta < 50 ? 'var(--danger)' : '')}
          ${this.fila('Sin respuesta', r.sinRespuesta)}
          ${this.fila('Mediana', this.num(r.primeraRespuestaMedianaHoras, 1, 'Sin respuestas medidas') + (r.primeraRespuestaMedianaHoras !== null ? ' h' : ''))}
        `)}
        ${this.tarjeta('Primera respuesta', totalTramos ? `
          ${tramoFila('Menos de 1 hora', t.menos1h || 0, 'var(--success)')}
          ${tramoFila('Entre 1 y 24 horas', t.h1a24 || 0, 'var(--warning)')}
          ${tramoFila('Más de 24 horas', t.mas24h || 0, 'var(--danger)')}
        ` : `<div style="color: var(--text-muted); font-size: 0.85rem;">Sin chats con historial de mensajes.</div>`)}
        <div>
          <h5 style="margin: 0 0 0.5rem 0; font-size: 0.78rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted);">Inmuebles más lentos</h5>
          ${lentos ? `<table style="width: 100%; border-collapse: collapse;">${lentos}</table>`
            : `<div style="color: var(--text-muted); font-size: 0.85rem;">${VALOR}</div>`}
        </div>
      </div>`
    );
  }

  // ---- 3. Ingresos -------------------------------------------------------

  seccionIngresos() {
    const g = this.m.ingresos || {};
    const mes = Object.entries(g.porMes || {}).sort((a, b) => b[0].localeCompare(a[0])).slice(0, 6);
    const maxMes = mes.length ? Math.max(...mes.map(([, v]) => Number(v) || 0)) : 0;

    const plazo = (g.porPlazo || []).map((p) => `
      <div style="display: flex; justify-content: space-between; font-size: 0.8rem; padding: 0.25rem 0;">
        <span style="color: var(--text-muted);">${p.dias ? p.dias + ' días' : 'Sin plazo'}</span>
        <span style="font-weight: 600; font-variant-numeric: tabular-nums;">${p.conteo} · S/ ${this.num(p.importe, 2)}</span>
      </div>`).join('');

    return this.bloque(
      'Facturación por destacados',
      'Solo se cuenta lo que tiene precio cobrado. Los destacados puestos sin cobro salen aparte, no como caja.',
      `<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1.25rem;">
        ${this.tarjeta('Caja', `
          ${this.fila('Total facturado', 'S/ ' + this.num(g.total, 2, 'Ningún destacado cobrado'))}
          ${this.fila('Últimos 30 días', 'S/ ' + this.num(g.total30d, 2))}
          ${this.fila('Ticket promedio', 'S/ ' + this.num(g.ticketPromedio, 2, 'Sin cobros'))}
          ${this.fila('Destacados activos', g.destacadosActivos)}
        `)}
        ${this.tarjeta('Por plazo comprado', plazo || `<div style="color: var(--text-muted); font-size: 0.85rem;">${VALOR}</div>`)}
        ${this.tarjeta('Últimos meses', mes.length ? mes.map(([k, v]) => {
          const pct = maxMes ? Math.round((v / maxMes) * 100) : 0;
          return `
            <div style="margin-bottom: 0.55rem;">
              <div style="display: flex; justify-content: space-between; font-size: 0.8rem;">
                <span style="color: var(--text-muted);">${k}</span>
                <span style="font-weight: 600; font-variant-numeric: tabular-nums;">S/ ${this.num(v, 2)}</span>
              </div>
              ${this.barra(pct, 'var(--success)')}
            </div>`;
        }).join('') : `<div style="color: var(--text-muted); font-size: 0.85rem;">${VALOR}</div>`)}
      </div>
      ${(g.destacadosSinPrecio || 0) > 0 ? `
        <div style="margin-top: 1rem; padding: 0.75rem 1rem; border-radius: 10px; background: var(--warning-bg); border: 1px solid var(--warning-border); font-size: 0.82rem; color: #92400e;">
          <strong>${g.destacadosSinPrecio} destacado(s) sin precio</strong>: están publicados como destacados pero no se les registró cobro
          ${g.sinPrecioDetalle ? `— ${g.sinPrecioDetalle.map((d) => this.esc(d.titulo)).join(', ')}` : ''}. Cuentan como visibles, no como ingresos.
        </div>` : ''}`
    );
  }

  // ---- 4. Precios --------------------------------------------------------

  seccionPrecios() {
    const p = this.m.precios || {};
    const grupos = p.porTipo || [];
    const maxMediana = grupos.length ? Math.max(...grupos.map((g) => Number(g.mediana) || 0)) : 0;

    const filasTipo = grupos.map((g) => `
      <tr style="border-bottom: 1px solid var(--border-color, #f1f5f9);">
        <td style="padding: 0.45rem 0.5rem 0.45rem 0; font-size: 0.82rem; text-transform: capitalize;">${this.esc(g.clave)}</td>
        <td style="padding: 0.45rem 0.5rem; text-align: right; font-size: 0.82rem; font-variant-numeric: tabular-nums; color: var(--text-muted);">${g.n}</td>
        <td style="padding: 0.45rem 0; text-align: right; font-size: 0.85rem; font-weight: 700; font-variant-numeric: tabular-nums;">S/ ${this.num(g.mediana)}</td>
      </tr>`).join('');

    const MOTIVOS = {
      precio_ausente: 'sin precio',
      precio_no_positivo: 'precio 0 o negativo',
      muy_bajo_para_su_tipo: 'muy barato para su tipo',
      muy_alto_para_su_tipo: 'muy caro para su tipo',
      precio_sospechoso: 'precio sospechoso',
    };

    const outs = (p.outliers || []).map((o) => `
      <tr style="border-bottom: 1px solid var(--border-color, #f1f5f9);">
        <td style="padding: 0.45rem 0.5rem 0.45rem 0; font-size: 0.82rem; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${this.esc(o.titulo || o.id)}</td>
        <td style="padding: 0.45rem 0.5rem; font-size: 0.75rem; color: var(--text-muted);">${this.esc(MOTIVOS[o.motivo] || o.motivo)}</td>
        <td style="padding: 0.45rem 0; text-align: right; font-size: 0.85rem; font-weight: 700; font-variant-numeric: tabular-nums; color: var(--danger);">${o.moneda === 'USD' ? 'US$ ' : 'S/ '}${this.num(o.precio)}</td>
      </tr>`).join('');

    return this.bloque(
      'Precios',
      'Mediana por tipo (un solo alquiler caro no debe mover el centro) y precios sospechosos.',
      `<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1.25rem;">
        <div>
          <h5 style="margin: 0 0 0.5rem 0; font-size: 0.78rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted);">Mediana por tipo</h5>
          ${filasTipo ? `<table style="width: 100%; border-collapse: collapse;">${filasTipo}</table>
            <div style="margin-top: 0.6rem; font-size: 0.75rem; color: var(--text-muted);">
              Global: mediana S/ ${this.num((p.global || {}).mediana)} · mínimo S/ ${this.num((p.global || {}).min)} · máximo S/ ${this.num((p.global || {}).max)}
            </div>` : `<div style="color: var(--text-muted); font-size: 0.85rem;">${VALOR}</div>`}
        </div>
        <div>
          <h5 style="margin: 0 0 0.5rem 0; font-size: 0.78rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted);">Revisar precio</h5>
          ${outs ? `<table style="width: 100%; border-collapse: collapse;">${outs}</table>`
            : `<div style="color: var(--text-muted); font-size: 0.85rem;">Ningún precio fuera de rango.</div>`}
        </div>
      </div>`
      , maxMediana ? '' : ''
    );
  }

  // ---- 5. Calidad de los datos ------------------------------------------

  seccionCalidad() {
    const c = this.m.calidad || {};
    const items = c.items || [];
    // El score se pinta en rojo por debajo de 70: es el punto en el que un
    // anuncio incompleto ya no se puede tomar en serio.
    const score = c.score;
    const color = score === null || score === undefined ? 'var(--text-muted)'
      : score >= 85 ? 'var(--success)' : score >= 70 ? 'var(--warning)' : 'var(--danger)';

    const filas = items.map((i) => {
      const col = i.pct >= 90 ? 'var(--success)' : i.pct >= 70 ? 'var(--warning)' : 'var(--danger)';
      return `
        <div style="margin-bottom: 0.6rem;">
          <div style="display: flex; justify-content: space-between; gap: 0.5rem; font-size: 0.8rem;">
            <span style="color: var(--text-color);">${this.esc(i.etiqueta)}</span>
            <span style="color: var(--text-muted); font-variant-numeric: tabular-nums; white-space: nowrap;">${i.completos}/${c.total}${i.faltan ? ` · faltan ${i.faltan}` : ''}</span>
          </div>
          ${this.barra(i.pct, col)}
        </div>`;
    }).join('');

    const peores = items.filter((i) => i.faltan > 0).slice(0, 3).map((i) => `
      <li style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 0.3rem;">
        <strong style="color: var(--text-color);">${i.faltan}</strong> sin ${this.esc(i.etiqueta.toLowerCase())}
        ${i.ejemplos && i.ejemplos.length ? `<span style="display: block; font-size: 0.75rem; opacity: 0.85;">${i.ejemplos.map((e) => this.esc(e.titulo)).join(' · ')}</span>` : ''}
      </li>`).join('');

    return this.bloque(
      'Calidad de las publicaciones',
      'Los anuncios incompletos son los que nadie alquila. Se ordena de peor a mejor.',
      `<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1.25rem;">
        ${this.tarjeta('Completitud media', `
          <div style="font-size: 2.2rem; font-weight: 800; line-height: 1; color: ${color};">${this.num(score)}<span style="font-size: 1rem; font-weight: 600; color: var(--text-muted);">/100</span></div>
          <div style="font-size: 0.8rem; color: var(--text-muted); margin-top: 0.35rem;">${c.total || 0} publicaciones revisadas</div>
        `)}
        <div>${filas || `<div style="color: var(--text-muted); font-size: 0.85rem;">${VALOR}</div>`}</div>
        ${this.tarjeta('Lo que más falta', peores ? `<ul style="margin: 0; padding-left: 1.1rem;">${peores}</ul>`
          : `<div style="color: var(--text-muted); font-size: 0.85rem;">Todos los campos completos.</div>`)}
      </div>`
    );
  }

  // ---- 6. Embudo de conversión ------------------------------------------

  seccionFunnel() {
    const f = this.m.funnel || {};
    // Sin eventos NO se pinta un embudo en ceros: se dice que falta la
    // instrumentación, que es información accionable.
    if (!f.activo) {
      return this.bloque(
        'Embudo de conversión',
        'Vistas → contacto → alquiler.',
        `<div style="padding: 1.25rem; border-radius: 10px; background: var(--bg-surface-secondary, #f8fafc); border: 1px dashed var(--border-color, #cbd5e1);">
          <div style="font-size: 0.9rem; font-weight: 600; color: var(--text-color); margin-bottom: 0.35rem;">La app todavía no envía eventos</div>
          <div style="font-size: 0.82rem; color: var(--text-muted); line-height: 1.5;">
            Mientras la app no registre las vistas de ficha, las búsquedas y los chats, no se puede medir cuántas personas
            ven un anuncio ni cuántas escriben al dueño. Todo lo demás del panel sí se calcula con datos reales.
          </div>
        </div>`
      );
    }

    const max = Math.max(...f.fases.map((x) => x.total || 0), 1);
    const fases = f.fases.map((x, i) => {
      const paso = (f.pasos || [])[i];
      const conversion = paso && paso.pct !== null && paso.pct !== undefined
        ? `<span style="font-size: 0.72rem; color: var(--text-muted);">← ${paso.pct} % desde "${this.esc(paso.desde)}"</span>` : '';
      return `
        <div style="margin-bottom: 0.8rem;">
          <div style="display: flex; justify-content: space-between; gap: 0.5rem; font-size: 0.82rem;">
            <span style="color: var(--text-color);">${this.esc(x.etiqueta)}</span>
            <span style="font-weight: 700; font-variant-numeric: tabular-nums;">${this.num(x.total)}</span>
          </div>
          ${this.barra(Math.round((x.total / max) * 100), '#6366f1')}
          ${conversion ? `<div style="margin-top: 0.2rem;">${conversion}</div>` : ''}
        </div>`;
    }).join('');

    return this.bloque(
      'Embudo de conversión',
      `${f.eventosTotales || 0} eventos registrados (${f.ultimos7d || 0} en los últimos 7 días).`,
      `<div>${fases}</div>`
    );
  }
}
