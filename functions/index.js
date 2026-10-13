/**
 * Cloud Functions de AlkilApp
 *
 * notificarNuevoTicketSoporte:
 *   Se dispara cuando se crea un documento en la colección "soporte" de Firestore
 *   (evento onCreate) y envía un email HTML de notificación a alkilapp2026@gmail.com.
 *
 * notificarNuevoInmueblePorAprobar:
 *   Se dispara cuando se crea un documento en "propiedades" y nace en estado
 *   "under_review" (que es como la app guarda una publicación nueva). Avisa para
 *   que el anuncio se apruebe en el panel antes de verse en la app. Editar un
 *   anuncio NO lo devuelve a "under_review", asi que no hay que volver a avisar.
 *
 * Proveedor de correo (en orden de preferencia, se elige el que tenga credenciales):
 *
 *   1) Resend (RECOMENDADO) - API HTTP, sin app password ni dominio propio:
 *        RESEND_API_KEY=re_xxxxxxxxx
 *        RESEND_FROM="AlkilApp Soporte <onboarding@resend.dev>"
 *        https://resend.com -> API Keys -> Create API Key (plan gratis: 3000/mes)
 *        Nota: el remitente `onboarding@resend.dev` solo puede enviar al correo
 *        con el que se registró la cuenta de Resend.
 *
 *   2) Nodemailer + Gmail (fallback) - requiere "contraseña de aplicación",
 *      que Google ya no ofrece en la mayoría de cuentas personales:
 *        GMAIL_USER=alkilapp2026@gmail.com
 *        GMAIL_APP_PASSWORD=abcdabcdabcdabcd
 *
 *   Destinatario configurable con SOPORTE_DESTINO (por defecto alkilapp2026@gmail.com).
 */
const functions = require('firebase-functions/v1');
const { getApps, initializeApp, getApp } = require('firebase-admin/app');
// getFirestore vive en firebase-admin/firestore, NO en firebase-admin/app.
const { getFirestore } = require('firebase-admin/firestore');

if (!getApps().length) initializeApp();

// OJO IMPORTANTE: hay que abrir la base CON NOMBRE "alkilappdb".
//   - Un getFirestore() a secas abre "(default)", que en este proyecto NO existe:
//     todos los datos de AlkilApp viven en la base con nombre.
//   - initializeFirestore(app, { databaseId }) NO sirve: en firebase-admin 12.7.0
//     se IGNORA en silencio y acaba apuntando a "(default)".
// La forma que funciona en todas las versiones es pasar el id como 2º argumento.
const db = getFirestore(getApp(), process.env.FIRESTORE_DB_ID || 'alkilappdb');

// Destinatario de las notificaciones de soporte
const DESTINO = process.env.SOPORTE_DESTINO || 'alkilapp2026@gmail.com';

// functions.config() fue eliminado en firebase-functions v7: la configuracion
// sale solo de variables de entorno (SOPORTE_DESTINO, RESEND_*, GMAIL_*).
const cfg = {};
const RESEND_API_KEY = process.env.RESEND_API_KEY || '';
const RESEND_FROM = process.env.RESEND_FROM || 'AlkilApp Soporte <onboarding@resend.dev>';
const GMAIL_USER = process.env.GMAIL_USER || (cfg.gmail && cfg.gmail.user) || DESTINO;
const GMAIL_PASS = process.env.GMAIL_APP_PASSWORD || (cfg.gmail && cfg.gmail.pass) || '';

// Transportador perezoso de Nodemailer: solo se construye si hay contraseña
let transporter = null;
function getTransporter() {
  if (transporter) return transporter;
  if (!GMAIL_PASS) return null;
  // Requerimiento perezoso: si algún día se quita nodemailer del package.json,
  // la función sigue funcionando con Resend sin tocar nada.
  const nodemailer = require('nodemailer');
  transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: { user: GMAIL_USER, pass: GMAIL_PASS },
  });
  return transporter;
}

// Envío vía Resend (API REST, sin dependencias: fetch global de Node 18+)
async function enviarConResend({ replyTo, subject, html, to }) {
  const res = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${RESEND_API_KEY}`,
    },
    body: JSON.stringify({
      from: RESEND_FROM,
      to: [to || DESTINO],
      subject,
      html,
      ...(replyTo ? { reply_to: replyTo } : {}),
    }),
  });

  const texto = await res.text();
  if (!res.ok) {
    throw new Error(`Resend HTTP ${res.status}: ${texto}`);
  }
  try {
    return JSON.parse(texto);
  } catch (_) {
    return { raw: texto };
  }
}

// Escape HTML: el mensaje lo escribe el usuario, nunca debe inyectar markup
const esc = (s) => String(s == null ? '' : s)
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;');

// Formato de fecha tolerando Timestamp de Firestore o string
const formatFecha = (v) => {
  try {
    if (v && typeof v.toDate === 'function') return v.toDate().toLocaleString('es-PE', { timeZone: 'America/Lima' });
    if (v) return new Date(v).toLocaleString('es-PE', { timeZone: 'America/Lima' });
  } catch (_) { /* fecha inválida */ }
  return new Date().toLocaleString('es-PE', { timeZone: 'America/Lima' });
};

// HTML del correo
function construirHtml({ ticketId, usuarioId, emailContacto, asunto, mensaje, estado, fecha }) {
  return `<!DOCTYPE html>
<html lang="es">
<head><meta charset="utf-8"></head>
<body style="margin:0;padding:0;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:24px 0;">
    <tr><td align="center">
      <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;">
        <tr>
          <td style="background:#FF6B5E;padding:20px 24px;">
            <h1 style="margin:0;color:#ffffff;font-size:20px;">Nuevo ticket de soporte - AlkilApp</h1>
          </td>
        </tr>
        <tr><td style="padding:24px;">
          <p style="margin:0 0 16px 0;color:#334155;font-size:14px;">Se ha creado un nuevo ticket en el m&oacute;dulo de soporte.</p>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border:1px solid #e2e8f0;border-radius:8px;">
            <tr style="background:#f8fafc;">
              <td style="padding:10px 14px;font-size:12px;font-weight:bold;color:#64748b;text-transform:uppercase;width:170px;">ID Ticket</td>
              <td style="padding:10px 14px;font-size:13px;color:#0f172a;font-family:monospace;">${esc(ticketId)}</td>
            </tr>
            <tr>
              <td style="padding:10px 14px;font-size:12px;font-weight:bold;color:#64748b;text-transform:uppercase;border-top:1px solid #e2e8f0;">ID Usuario</td>
              <td style="padding:10px 14px;font-size:13px;color:#0f172a;font-family:monospace;border-top:1px solid #e2e8f0;">${esc(usuarioId)}</td>
            </tr>
            <tr style="background:#f8fafc;">
              <td style="padding:10px 14px;font-size:12px;font-weight:bold;color:#64748b;text-transform:uppercase;border-top:1px solid #e2e8f0;">Email de contacto</td>
              <td style="padding:10px 14px;font-size:13px;color:#0f172a;border-top:1px solid #e2e8f0;">${esc(emailContacto)}</td>
            </tr>
            <tr>
              <td style="padding:10px 14px;font-size:12px;font-weight:bold;color:#64748b;text-transform:uppercase;border-top:1px solid #e2e8f0;">Asunto</td>
              <td style="padding:10px 14px;font-size:13px;color:#0f172a;border-top:1px solid #e2e8f0;">${esc(asunto)}</td>
            </tr>
            <tr style="background:#f8fafc;">
              <td style="padding:10px 14px;font-size:12px;font-weight:bold;color:#64748b;text-transform:uppercase;border-top:1px solid #e2e8f0;">Estado</td>
              <td style="padding:10px 14px;font-size:13px;border-top:1px solid #e2e8f0;">
                <span style="display:inline-block;background:#fffbeb;color:#b45309;border:1px solid #fde68a;border-radius:999px;padding:2px 10px;font-size:12px;font-weight:bold;">${esc(estado || 'pendiente')}</span>
              </td>
            </tr>
            <tr>
              <td style="padding:10px 14px;font-size:12px;font-weight:bold;color:#64748b;text-transform:uppercase;border-top:1px solid #e2e8f0;">Fecha</td>
              <td style="padding:10px 14px;font-size:13px;color:#0f172a;border-top:1px solid #e2e8f0;">${esc(formatFecha(fecha))}</td>
            </tr>
          </table>
          <p style="margin:20px 0 6px 0;font-size:12px;font-weight:bold;color:#64748b;text-transform:uppercase;">Mensaje</p>
          <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:14px;font-size:14px;line-height:1.6;color:#334155;white-space:pre-wrap;word-break:break-word;">${esc(mensaje)}</div>
        </td></tr>
        <tr>
          <td style="background:#f1f5f9;padding:14px 24px;font-size:12px;color:#94a3b8;text-align:center;">
            AlkilApp - Sistema autom&aacute;tico de soporte &bull; Ticket ${esc(ticketId)}
          </td>
        </tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`;
}

/**
 * Trigger: soporte/{ticketId} onCreate -> email a alkilapp2026@gmail.com
 */
exports.notificarNuevoTicketSoporte = functions.firestore
  .document('soporte/{ticketId}')
  .onCreate(async (snap, context) => {
    const ticketId = context.params.ticketId;
    const data = snap.data() || {};

    const asunto = data.asunto || '(sin asunto)';
    const mensaje = {
      to: DESTINO,
      replyTo: data.emailContacto || undefined,
      subject: `[Soporte AlkilApp] ${asunto} - Ticket: ${ticketId}`,
      html: construirHtml({
        ticketId,
        usuarioId: data.usuarioId || 'N/A',
        emailContacto: data.emailContacto || 'N/A',
        asunto,
        mensaje: data.mensaje || '(sin mensaje)',
        estado: data.estado || 'pendiente',
        fecha: data.fechaCreacion,
      }),
    };

    // Sin credenciales: el ticket ya quedó registrado en Firestore y se ve en
    // el panel admin, así que solo logueamos y salimos (sin reintentos).
    if (!RESEND_API_KEY && !GMAIL_PASS) {
      console.error(
        `[Soporte] Email NO enviado para ticket ${ticketId}: falta RESEND_API_KEY` +
        ' (o GMAIL_APP_PASSWORD). El ticket sigue visible en el panel admin.'
      );
      return null;
    }

    try {
      if (RESEND_API_KEY) {
        const r = await enviarConResend(mensaje);
        console.log(`[Soporte] Email enviado (Resend) para ticket ${ticketId} -> id=${(r && r.id) || 'n/a'}`);
      } else {
        const t = getTransporter();
        await t.sendMail({
          from: `"AlkilApp Soporte" <${GMAIL_USER}>`,
          to: mensaje.to,
          replyTo: mensaje.replyTo,
          subject: mensaje.subject,
          html: mensaje.html,
        });
        console.log(`[Soporte] Email enviado (Gmail) para ticket ${ticketId}`);
      }
    } catch (error) {
      // No lanzamos el error: un fallo de correo no debe romper el trigger ni
      // reintentar infinitamente (el ticket sigue vivo en la colección).
      console.error(`[Soporte] Error enviando email del ticket ${ticketId}:`, error.message);
    }
    return null;
  });

// ===========================================================================
// Avisos de inmueble pendiente de aprobar
// ===========================================================================

// Destinatario de los avisos de inmueble pendiente de aprobar. Por defecto el
// mismo correo de soporte.
const DESTINO_INMUEBLES = process.env.INMUEBLES_DESTINO || DESTINO;

// Panel admin, para el boton del correo. El panel no admite deep-link a una
// ficha concreta (no lee parametros de URL), asi que el enlace lleva a la lista
// de publicaciones y no a una URL inventada que daria 404.
const PANEL_URL = process.env.PANEL_URL
  || 'https://alkilapp-admin-288429280561.us-central1.run.app';

// La app escribe "under_review" al crear una publicación
// (RegistrarPropiedadActivity: datosNueva["estado"] = "under_review").
const ESTADOS_PENDIENTES = ['under_review', 'pendiente'];

function precioLegible(p) {
  const n = Number(p.precio);
  if (!Number.isFinite(n) || n <= 0) return 'Sin precio';
  const moneda = String(p.moneda || 'PEN').toUpperCase() === 'USD' ? 'US$' : 'S/';
  return `${moneda} ${n.toLocaleString('es-PE', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
}

function construirHtmlInmueble({ id, titulo, tipo, operacion, precio, ubicacion, ambientes, superficie, propietario, publicadoEn }) {
  const fila = (etiqueta, valor) => `
            <tr>
              <td style="padding:10px 14px;font-size:12px;font-weight:bold;color:#64748b;text-transform:uppercase;border-top:1px solid #e2e8f0;width:170px;">${etiqueta}</td>
              <td style="padding:10px 14px;font-size:13px;color:#0f172a;border-top:1px solid #e2e8f0;">${valor}</td>
            </tr>`;

  return `<!DOCTYPE html>
<html lang="es">
<head><meta charset="utf-8"></head>
<body style="margin:0;padding:0;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:24px 0;">
    <tr><td align="center">
      <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;">
        <tr>
          <td style="background:#0B2447;padding:20px 24px;">
            <h1 style="margin:0;color:#ffffff;font-size:20px;">Nuevo inmueble por aprobar</h1>
          </td>
        </tr>
        <tr><td style="padding:24px;">
          <p style="margin:0 0 16px 0;color:#334155;font-size:14px;">
            Se ha publicado un inmueble y espera aprobaci&oacute;n en el panel antes de verse en la app.
          </p>
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border:1px solid #e2e8f0;border-radius:8px;">
            <tr style="background:#f8fafc;">
              <td style="padding:10px 14px;font-size:12px;font-weight:bold;color:#64748b;text-transform:uppercase;width:170px;">Inmueble</td>
              <td style="padding:10px 14px;font-size:13px;color:#0f172a;">${esc(titulo)}</td>
            </tr>
            ${fila('ID', esc(id))}
            ${fila('Propietario', esc(propietario))}
            ${fila('Tipo', esc(tipo))}
            ${fila('Operaci&oacute;n', esc(operacion))}
            ${fila('Precio', esc(precio))}
            ${fila('Ubicaci&oacute;n', esc(ubicacion))}
            ${fila('Ambientes / Superficie', esc(ambientes))}
            ${fila('Publicado', esc(formatFecha(publicadoEn)))}
          </table>
          <table role="presentation" cellpadding="0" cellspacing="0" style="margin:22px 0 0 0;">
            <tr><td align="center" style="border-radius:8px;background:#FF6B5E;">
              <a href="${PANEL_URL}/publicaciones" style="display:inline-block;padding:13px 26px;font-size:15px;font-weight:bold;color:#ffffff;text-decoration:none;">Revisar y aprobar en el panel</a>
            </td></tr>
          </table>
          <p style="margin:18px 0 0 0;font-size:12px;color:#94a3b8;line-height:1.5;">
            El anuncio no aparece en la app hasta que se apruebe desde el panel de administraci&oacute;n.
          </p>
        </td></tr>
        <tr>
          <td style="background:#f1f5f9;padding:14px 24px;font-size:12px;color:#94a3b8;text-align:center;">
            AlkilApp - Aviso autom&aacute;tico de publicaci&oacute;n &bull; ${esc(id)}
          </td>
        </tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`;
}

/**
 * Trigger: propiedades/{propiedadId} onCreate -> email cuando nace en
 * "under_review", que es como la app guarda una publicación nueva.
 *
 * SOLO onCreate, a proposito:
 *   - Editar un anuncio NO lo devuelve a "under_review" (la app mantiene el
 *     estado actual), asi que no habria que reavisar.
 *   - Y aunque lo hiciera, avisar en cada update seria ruido: basta con saber
 *     que hay algo nuevo esperando.
 *
 * Un fallo de correo NO se propaga: el inmueble ya esta en Firestore y se ve en
 * el panel, asi que reintentar en bucle no aportaria nada.
 */
exports.notificarNuevoInmueblePorAprobar = functions.firestore
  .document('propiedades/{propiedadId}')
  .onCreate(async (snap, context) => {
    const id = context.params.propiedadId;
    const data = snap.data() || {};

    // Filtro explicito aunque el trigger sea onCreate: si alguien crea el
    // documento ya publicado (por ejemplo una importacion), no hay nada que
    // aprobar y el correo seria falso.
    const estado = String(data.estado || '').toLowerCase();
    if (!ESTADOS_PENDIENTES.includes(estado)) {
      console.log(`[Inmueble] ${id} creado con estado "${data.estado || '(vacio)'}": no requiere aprobacion, sin aviso.`);
      return null;
    }

    // El nombre del propietario no viene en el documento: hay que leerlo.
    let propietario = 'Desconocido';
    let emailPropietario;
    if (data.idPropietario) {
      try {
        const u = await db.doc(`usuarios/${data.idPropietario}`).get();
        if (u.exists) {
          const ud = u.data() || {};
          propietario = ud.nombre || ud.email || data.idPropietario;
          emailPropietario = ud.email || undefined;
        }
      } catch (e) {
        console.warn(`[Inmueble] No se pudo leer el propietario ${data.idPropietario}: ${e.message}`);
      }
    }

    const ubicacion = [data.departamento, data.ciudad, data.barrio, data.distrito]
      .filter(Boolean)
      // .filter(Boolean) deja duplicados: la app guarda el departamento en
      // "ciudad", asi que sin esto el mismo nombre salia dos veces.
      .filter((v, i, a) => a.indexOf(v) === i)
      .join(' · ') || 'Sin ubicaci&oacute;n';

    const ambientes = [
      Number(data.ambientes) > 0 ? `${data.ambientes} ambientes` : null,
      Number(data.superficieM2) > 0 ? `${data.superficieM2} m²` : null,
    ].filter(Boolean).join(' · ') || 'Sin datos';

    const correo = {
      to: DESTINO_INMUEBLES,
      replyTo: emailPropietario,
      subject: `[AlkilApp] Nuevo inmueble por aprobar: ${(data.titulo || '(sin título)').slice(0, 60)}`,
      html: construirHtmlInmueble({
        id,
        titulo: data.titulo || '(sin título)',
        tipo: data.tipo || 'departamento',
        operacion: data.operacion || 'alquiler',
        precio: precioLegible(data),
        ubicacion,
        ambientes,
        superficie: data.superficieM2,
        propietario,
        publicadoEn: data.publicadoEn || snap.createTime,
      }),
    };

    if (!RESEND_API_KEY && !GMAIL_PASS) {
      console.error(
        `[Inmueble] Email NO enviado para ${id}: falta RESEND_API_KEY` +
        ' (o GMAIL_APP_PASSWORD). El inmueble sigue visible en el panel admin.'
      );
      return null;
    }

    try {
      if (RESEND_API_KEY) {
        const r = await enviarConResend(correo);
        console.log(`[Inmueble] Email enviado (Resend) para ${id} -> id=${(r && r.id) || 'n/a'}`);
      } else {
        const t = getTransporter();
        await t.sendMail({
          from: `"AlkilApp Avisos" <${GMAIL_USER}>`,
          to: correo.to,
          replyTo: correo.replyTo,
          subject: correo.subject,
          html: correo.html,
        });
        console.log(`[Inmueble] Email enviado (Gmail) para ${id}`);
      }
    } catch (error) {
      console.error(`[Inmueble] Error enviando email del inmueble ${id}:`, error.message);
    }
    return null;
  });
