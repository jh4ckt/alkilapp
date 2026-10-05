/**
 * Cloud Functions de AlkilApp
 *
 * notificarNuevoTicketSoporte:
 *   Se dispara cuando se crea un documento en la colección "soporte" de Firestore
 *   (evento onCreate) y envía un email HTML de notificación a alkilapp2026@gmail.com.
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
const { getApps, initializeApp } = require('firebase-admin/app');

if (!getApps().length) initializeApp();

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
async function enviarConResend({ replyTo, subject, html }) {
  const res = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${RESEND_API_KEY}`,
    },
    body: JSON.stringify({
      from: RESEND_FROM,
      to: [DESTINO],
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
