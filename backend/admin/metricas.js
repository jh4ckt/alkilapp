/**
 * AlkilApp Admin — Métricas de negocio y calidad.
 *
 * Todo lo que hay aquí se calcula a partir de datos que YA existen en Firestore.
 * Nada se estima ni se inventa: si un dato no está, se devuelve null o la lista
 * vacía, y el front lo dice explícitamente en vez de pintar un cero que parece
 * una medición real.
 *
 * Bloques:
 *   operacion  — colas de moderación: pendientes, antigüedad del más viejo y
 *                tiempo de resolución real (revisadoEn - createdAt).
 *   respuesta  — cuánto tarda el dueño de un inmueble en contestar un chat, y
 *                cuántos chats se quedaron sin respuesta.
 *   ingresos   — facturación real por destacados (destacadoPrecio + orderId).
 *   precios    — distribución de precios por tipo y zona, con outliers.
 *   calidad    — completitud de los campos de cada publicación.
 *   funnel     — eventos de la app; vacío hasta que la app envíe.
 */

const DIAS_MS = 86400000;

/** Timestamp | Date | ISO string | null -> Date | null */
function aFecha(v) {
    if (!v) return null;
    if (typeof v.toDate === 'function') return v.toDate();
    const d = new Date(v);
    return isNaN(d.getTime()) ? null : d;
}

function horasEntre(desde, hasta) {
    const a = aFecha(desde), b = aFecha(hasta);
    if (!a || !b) return null;
    return (b.getTime() - a.getTime()) / 3600000;
}

/** Mediana (no la media): un alquiler de 5,000 PEN no debe desplazar el centro. */
function mediana(nums) {
    const v = nums.filter((n) => Number.isFinite(n)).sort((a, b) => a - b);
    if (!v.length) return null;
    const m = Math.floor(v.length / 2);
    return v.length % 2 ? v[m] : (v[m - 1] + v[m]) / 2;
}

function media(nums) {
    const v = nums.filter((n) => Number.isFinite(n));
    return v.length ? v.reduce((a, b) => a + b, 0) / v.length : null;
}

function redondear(n, d = 2) {
    return Number.isFinite(n) ? Number(n.toFixed(d)) : null;
}

/** Redondea a 1 decimal si hay decimales, para pintar "6.9" y no "6.90000001". */
function limpio(n) {
    if (!Number.isFinite(n)) return null;
    return Number.isInteger(n) ? n : Number(n.toFixed(1));
}

// ---------------------------------------------------------------------------
// OPERACION: colas de moderacion y SLA
// ---------------------------------------------------------------------------

/**
 * Estado de una cola (verificaciones / denuncias / soporte).
 *
 * @param docs    documentos de la cola, ya aplanados con `id`
 * @param fCrear  nombre del campo de fecha de creación (NO es el mismo en todas)
 * @param fCerrar nombre del campo de fecha de resolución
 * @param abierto valor de `estado` que cuenta como pendiente
 * @param cerrado valor de `estado` que cuenta como resuelto
 */
function resumenCola(docs, fCrear, fCerrar, abierto, cerrado) {
    const pendientes = [];
    const resueltos = [];
    for (const d of docs) {
        if ((d.estado || abierto) === abierto) pendientes.push(d);
        else resueltos.push(d);
    }

    // Antigüedad de lo que está SIN resolver: es el número que dice si la cola
    // se está.atascando. En minutos si acaba de entrar, en horas si lleva días.
    const antiguedad = pendientes
        .map((d) => (Date.now() - (aFecha(d[fCrear]) || new Date()).getTime()))
        .filter((n) => Number.isFinite(n))
        .sort((a, b) => b - a); // de más viejo a más nuevo

    const oldestMs = antiguedad[0] ?? null;

    // Tiempo de resolución de lo ya cerrado: media y mediana en horas.
    const resueltosHoras = resueltos
        .map((d) => horasEntre(d[fCrear], d[fCerrar]))
        .filter((h) => h !== null && h >= 0);

    // SLA de 24h sobre lo resuelto: porcentaje, para ver si la cola responde.
    const dentro24h = resueltosHoras.filter((h) => h <= 24).length;

    return {
        total: docs.length,
        pendientes: pendientes.length,
        resueltos: resueltos.length,
        // Si el mas viejo lleva mas de 48h, el numero se pinta en rojo en el front.
        masViejoHoras: oldestMs === null ? null : Math.round((oldestMs / 3600000) * 10) / 10,
        masViejoId: pendientes.length
            ? (pendientes.sort((a, b) => (aFecha(a[fCrear]) || 0) - (aFecha(b[fCrear]) || 0))[0].id)
            : null,
        resolucionMediaHoras: redondear(media(resueltosHoras)),
        resolucionMedianaHoras: redondear(mediana(resueltosHoras)),
        sla24hPct: resueltosHoras.length ? Math.round((dentro24h / resueltosHoras.length) * 100) : null,
        // El mas viejo de los pendientes, con su asunto/titulo para que el admin
        // pueda ir directo a el desde el panel.
        masViejoResumen: pendientes.length
            ? (() => {
                const d = pendientes.slice().sort((a, b) => (aFecha(a[fCrear]) || 0) - (aFecha(b[fCrear]) || 0))[0];
                return {
                    id: d.id,
                    titulo: d.asunto || d.nombre || d.motivo || d.id,
                    fecha: aFecha(d[fCrear]) ? aFecha(d[fCrear]).toISOString() : null,
                };
            })()
            : null,
    };
}

// ---------------------------------------------------------------------------
// RESPUESTA: cuanto tarda el dueno en contestar
// ---------------------------------------------------------------------------

/**
 * Tiempo hasta la primera respuesta del PROPIETARIO del inmueble.
 *
 * "Propietario" se resuelve por `propiedades/{listingId}.idPropietario`, no por
 * suponer que es el que creó el chat: el chat lo crea quien tenga interés, que
 * casi siempre es el otro. Si el inmueble no existe ya, no hay dueño conocido y
 * el chat queda fuera de la métrica en vez de contarlo con el dueño equivocado.
 */
function resumenRespuesta(chats, mensajesPorChat, duenosPorListing) {
    let conChat = 0, sinMensajes = 0, respondidos = 0, sinRespuesta = 0, sinDueno = 0;
    const horas = [];
    const porListing = [];

    for (const c of chats) {
        conChat++;
        const dueno = duenosPorListing.get(c.listingId) || null;
        if (!dueno) { sinDueno++; continue; }

        const msgs = mensajesPorChat.get(c.id) || [];
        if (!msgs.length) { sinMensajes++; continue; }

        // El primer mensaje del dueño, ordenados por fecha real.
        const delDueno = msgs
            .filter((m) => m.senderId === dueno)
            .map((m) => ({ m, t: aFecha(m.sentAt) }))
            .filter((x) => x.t)
            .sort((a, b) => a.t - b.t);

        if (!delDueno.length) { sinRespuesta++; porListing.push({ listingId: c.listingId, titulo: c.listingTitle, horas: null }); continue; }

        const primero = msgs
            .map((m) => ({ m, t: aFecha(m.sentAt) }))
            .filter((x) => x.t)
            .sort((a, b) => a.t - b.t)[0];
        const h = (delDueno[0].t - primero.t) / 3600000;
        if (h < 0) { sinRespuesta++; continue; }
        respondidos++;
        horas.push(h);
        porListing.push({ listingId: c.listingId, titulo: c.listingTitle, horas: Math.round(h * 10) / 10 });
    }

    // Tramos: es mas util que un promedio cuando hay dos poblaciones (los que
    // contestan en minutos y los que nunca contestan).
    const tramos = {
        menos1h: horas.filter((h) => h < 1).length,
        h1a24: horas.filter((h) => h >= 1 && h < 24).length,
        mas24h: horas.filter((h) => h >= 24).length,
    };

    return {
        chats: conChat,
        conMensajes: conChat - sinMensajes - sinDueno,
        respondidos,
        sinRespuesta,
        vacios: sinMensajes,
        sinDuenoConocido: sinDueno,
        pctConRespuesta: respondidos + sinRespuesta > 0
            ? Math.round((respondidos / (respondidos + sinRespuesta)) * 100) : null,
        primeraRespuestaMediaHoras: redondear(media(horas)),
        primeraRespuestaMedianaHoras: redondear(mediana(horas)),
        tramos,
        // Los 10 mas lentos, para ver si el problema es de un anuncio concreto.
        masLentos: porListing
            .filter((x) => x.horas !== null)
            .sort((a, b) => b.horas - a.horas)
            .slice(0, 10),
    };
}

// ---------------------------------------------------------------------------
// INGRESOS: destacados de pago
// ---------------------------------------------------------------------------

/**
 * Facturación real.
 *
 * Un destacado cuenta como pagado solo si trae `destacadoPrecio` Y `orderId`
 * (o un precio > 0). Hay destacados puestos a mano por el admin o hydrocarbons
 * como la fila con 30 días y sin precio): esos NO son ingresos y
 * sumarlos inflaría la caja.
 */
function resumenIngresos(props, ahora) {
    const conPrecio = props
        .filter((p) => Number.isFinite(Number(p.destacadoPrecio)) && Number(p.destacadoPrecio) > 0)
        .map((p) => ({
            precio: Number(p.destacadoPrecio),
            dias: Number(p.destacadoDias) || null,
            fecha: aFecha(p.destacadoAprobadoEn) || aFecha(p.solicitudDestacarEn) || aFecha(p.publicadoEn),
            orden: p.orderId || null,
        }));

    const suma = (arr) => arr.reduce((a, b) => a + b, 0);
    const enVentana = (dias) => conPrecio.filter((x) => x.fecha && (ahora - x.fecha.getTime()) <= dias * DIAS_MS);
    const ult30 = enVentana(30);
    const ult90 = enVentana(90);

    // Por plazo comprado (7/15/30 días): cuántos y por cuánto.
    const porPlazo = {};
    for (const x of conPrecio) {
        const k = x.dias ? String(x.dias) : 'sin_plazo';
        if (!porPlazo[k]) porPlazo[k] = { dias: x.dias, conteo: 0, importe: 0 };
        porPlazo[k].conteo++;
        porPlazo[k].importe = limpio(porPlazo[k].importe + x.precio);
    }

    // Serie mensual, para ver la evolucion en vez de un total aislado.
    const porMes = {};
    for (const x of conPrecio) {
        if (!x.fecha) continue;
        const k = x.fecha.toISOString().slice(0, 7);
        porMes[k] = limpio((porMes[k] || 0) + x.precio);
    }

    const destacados = props.filter((p) => p.isFeatured === true);
    const activos = destacados.filter((p) => {
        const f = aFecha(p.featuredUntil);
        return !f || f.getTime() > ahora;
    });
    // Destacados sin precio: no son ingresos, pero delatan que alguien coloco
    // un destacado sin cobro. Se cuentan aparte para que no se pierdan de vista.
    const sinPrecio = destacados.filter((p) => !(Number(p.destacadoPrecio) > 0));

    return {
        total: limpio(suma(conPrecio.map((x) => x.precio))),
        total30d: limpio(suma(ult30.map((x) => x.precio))),
        total90d: limpio(suma(ult90.map((x) => x.precio))),
        cobros: conPrecio.length,
        cobros30d: ult30.length,
        ticketPromedio: conPrecio.length ? redondear(suma(conPrecio.map((x) => x.precio)) / conPrecio.length) : null,
        conOrderId: conPrecio.filter((x) => x.orden).length,
        destacadosActivos: activos.length,
        destacadosVencidos: destacados.length - activos.length,
        destacadosSinPrecio: sinPrecio.length,
        sinPrecioDetalle: sinPrecio.slice(0, 10).map((p) => ({ id: p.id, titulo: p.titulo, dias: p.destacadoDias || null })),
        porPlazo: Object.values(porPlazo).sort((a, b) => (a.dias || 0) - (b.dias || 0)),
        porMes,
    };
}

// ---------------------------------------------------------------------------
// PRECIOS: distribucion y outliers
// ---------------------------------------------------------------------------

const CAMBIOS_PEN = 3.75; // referencial, solo para comparar rentabilidades

function aSoles(p) {
    const n = Number(p.precio);
    if (!Number.isFinite(n)) return null;
    return String(p.moneda || 'PEN').toUpperCase() === 'USD' ? n * CAMBIOS_PEN : n;
}

/** Estadisticas de un grupo de precios (en soles). */
function estadisticas(nums) {
    const v = nums.filter((n) => Number.isFinite(n));
    if (!v.length) return null;
    return {
        n: v.length,
        mediana: limpio(mediana(v)),
        media: redondear(media(v)),
        min: limpio(Math.min(...v)),
        max: limpio(Math.max(...v)),
    };
}

/**
 * Outliers de precio.
 *
 * Se comparan contra la mediana del MISMO tipo, no contra un rango fijo: un
 * alquiler en Lima y uno en Huánuco no valen lo mismo, pero dos alquileres del
 * mismo tipo que difieren 5x entre sí sí son sospechosos (un error de tecleo en
 * el precio, o un alquiler en dolares rotulado como soles).
 *
 * Se usa mediana y no media justamente para que los propios outliers no
 * contaminen el centro de comparacion.
 */
function outliersPrecio(props) {
    const porTipo = new Map();
    for (const p of props) {
        const s = aSoles(p);
        if (s === null) continue;
        const k = p.tipo || 'sin_tipo';
        if (!porTipo.has(k)) porTipo.set(k, []);
        porTipo.get(k).push(s);
    }
    const medianas = new Map([...porTipo.entries()].map(([k, v]) => [k, mediana(v)]));

    const lista = [];
    for (const p of props) {
        const s = aSoles(p);
        const med = medianas.get(p.tipo || 'sin_tipo');
        const precio = Number(p.precio);
        let motivo = null;

        if (s === null) motivo = 'precio_ausente';
        else if (precio <= 0) motivo = 'precio_no_positivo';
        else if (!med) motivo = null;
        else if (s < med * 0.2) motivo = 'muy_bajo_para_su_tipo';
        else if (s > med * 5) motivo = 'muy_alto_para_su_tipo';
        else if (s > 60000) motivo = 'precio_sospechoso';

        if (motivo) {
            lista.push({
                id: p.id, titulo: p.titulo, motivo,
                precio: Number.isFinite(precio) ? precio : null,
                moneda: p.moneda || null,
                soles: s === null ? null : limpio(s),
                medianaTipo: med === null ? null : limpio(med),
                tipo: p.tipo || null, estado: p.estado || null,
            });
        }
    }
    return lista.sort((a, b) => (a.soles || 0) - (b.soles || 0));
}

/** Resumen de precios por dimension (tipo, departamento, ciudad, operacion). */
function resumenPrecios(props) {
    const grupo = (campo) => {
        const mapa = new Map();
        for (const p of props) {
            const k = p[campo] || 'sin_dato';
            if (!mapa.has(k)) mapa.set(k, []);
            mapa.get(k).push(aSoles(p));
        }
        return [...mapa.entries()]
            .map(([k, v]) => ({ clave: k, ...estadisticas(v) }))
            .filter((x) => x.n > 0)
            .sort((a, b) => (b.mediana || 0) - (a.mediana || 0));
    };

    return {
        porTipo: grupo('tipo'),
        porDepartamento: grupo('departamento'),
        porOperacion: grupo('operacion'),
        global: estadisticas(props.map((p) => aSoles(p))),
        outliers: outliersPrecio(props),
    };
}

// ---------------------------------------------------------------------------
// CALIDAD: que campos les falta a las publicaciones
// ---------------------------------------------------------------------------

/**
 * Cada comprobacion es binaria: el anuncio tiene el dato o no lo tiene.
 * El panel lo ordena por "peor primero", porque los anuncios incompletos son
 * justo los que nadie alquila yappears como cerrados sin motivo claro.
 */
function resumenCalidad(props) {
    const total = props.length;
    const hay = (p, f) => {
        const v = p[f];
        return !(v === null || v === undefined || v === '' || (Array.isArray(v) && v.length === 0));
    };
    const numPositivo = (p, f) => Number(p[f]) > 0;

    const checks = [
        { campo: 'precio', etiqueta: 'Precio', ok: (p) => numPositivo(p, 'precio') },
        { campo: 'departamento', etiqueta: 'Departamento', ok: (p) => hay(p, 'departamento') },
        { campo: 'ciudad', etiqueta: 'Ciudad', ok: (p) => hay(p, 'ciudad') },
        { campo: 'barrio', etiqueta: 'Barrio', ok: (p) => hay(p, 'barrio') },
        { campo: 'fotos', etiqueta: 'Al menos una foto', ok: (p) => hay(p, 'fotos') || hay(p, 'imagenUrl') },
        { campo: 'descripcion', etiqueta: 'Descripción de 40+ caracteres', ok: (p) => String(p.descripcion || '').trim().length >= 40 },
        { campo: 'superficieM2', etiqueta: 'Superficie en m²', ok: (p) => numPositivo(p, 'superficieM2') },
        { campo: 'ambientes', etiqueta: 'Ambientes', ok: (p) => numPositivo(p, 'ambientes') },
        { campo: 'lat', etiqueta: 'Coordenadas', ok: (p) => Number.isFinite(Number(p.lat)) && Number.isFinite(Number(p.lng)) },
        { campo: 'operacion', etiqueta: 'Operación', ok: (p) => hay(p, 'operacion') },
    ];

    const items = checks.map((c) => {
        const faltan = props.filter((p) => !c.ok(p));
        return {
            campo: c.campo,
            etiqueta: c.etiqueta,
            completos: total - faltan.length,
            faltan: faltan.length,
            pct: total ? Math.round(((total - faltan.length) / total) * 100) : null,
            // El % de lo que FALTA es lo que importa: es el trabajo pendiente.
            ejemplos: faltan.slice(0, 5).map((p) => ({ id: p.id, titulo: p.titulo || '(sin título)' })),
        };
    });

    // Puntuacion 0-100 de completitud media de la publicacion.
    const media = items.reduce((a, c) => a + (c.pct || 0), 0) / (items.length || 1);

    return {
        total,
        items: items.sort((a, b) => (a.pct || 0) - (b.pct || 0)),
        score: Math.round(media),
    };
}

// ---------------------------------------------------------------------------
// FUNNEL: eventos de la app
// ---------------------------------------------------------------------------

// Orden del embudo. Es el orden REAL del uso: se publica, se ve, se contacta,
// y se alquila. Saltarse una fase no es un error del usuario, es una razon
// para mirar la conversión de esa fase.
const FASES_FUNNEL = [
    { tipo: 'listing_created', etiqueta: 'Publicaciones creadas' },
    { tipo: 'listing_viewed', etiqueta: 'Vistas de ficha' },
    { tipo: 'search_performed', etiqueta: 'Busquedas' },
    { tipo: 'chat_opened', etiqueta: 'Chats abiertos' },
    { tipo: 'listing_finalized', etiqueta: 'Inmuebles alquilados' },
];

/**
 * Resumen del embudo. Si no hay eventos, lo dice con `activo: false` para que el
 * front muestre "la app aún no envía eventos" y no un embudo en ceros que
 * parecería "nadie está viendo los anuncios".
 */
function resumenFunnel(eventos) {
    const cuenta = new Map();
    const recientes = [];
    for (const e of eventos) {
        const t = e.tipo;
        cuenta.set(t, (cuenta.get(t) || 0) + 1);
        const f = aFecha(e.ts || e.createdAt);
        if (f && f.getTime() > Date.now() - 7 * DIAS_MS) recientes.push(t);
    }

    const fases = FASES_FUNNEL.map((f) => {
        const total = cuenta.get(f.tipo) || 0;
        const h7 = recientes.filter((t) => t === f.tipo).length;
        return { ...f, total, ultimos7d: h7 };
    });

    // Conversion entre fases consecutivas, solo donde tiene sentido medirse.
    const pasos = [];
    for (let i = 0; i < fases.length - 1; i++) {
        const a = fases[i].total, b = fases[i + 1].total;
        // Publicar -> ver no es comparable (ver es por*S listing), asi que solo
        // se calcula la conversion donde el denominador tiene sentido.
        const comparable = !['search_performed', 'listing_viewed'].includes(fases[i].tipo);
        pasos.push({
            desde: fases[i].etiqueta,
            hacia: fases[i + 1].etiqueta,
            pct: comparable && a > 0 ? Math.round((b / a) * 100) : null,
        });
    }

    const otros = [...cuenta.entries()]
        .filter(([t]) => !FASES_FUNNEL.some((f) => f.tipo === t))
        .map(([tipo, total]) => ({ tipo, total }))
        .sort((a, b) => b.total - a.total);

    return {
        activo: eventos.length > 0,
        eventosTotales: eventos.length,
        fases,
        pasos,
        otros,
        ultimos7d: recientes.length,
    };
}

module.exports = {
    resumenCola,
    resumenRespuesta,
    resumenIngresos,
    resumenPrecios,
    resumenCalidad,
    resumenFunnel,
    // Utiles para las pruebas:
    mediana,
    media,
    aSoles,
    aFecha,
    FASES_FUNNEL,
};
