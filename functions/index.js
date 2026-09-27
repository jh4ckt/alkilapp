/**
 * Cloud Functions de AlkilApp
 *
 * notificarNuevoTicketSoporte:
 *   Se dispara cuando se crea un documento en la colección "soporte" de Firestore
 *   (evento onCreate) y envía un email HTML de notificación a alkilapp2026@gmail.com
 *   vía Nodemailer + Gmail.
 *
 * Configuración de credenciales (app password de Gmail, NO la contraseña normal):
 *   firebase functions:config:set gmail.user="alkilapp2026@gmail.com" gmail.pass="xxxx xxxx xxxx xxxx"
 * o bien con variables de entorno (.env en la raíz de functions/):
 *   GMAIL_USER=alkilapp2026@gmail.com
 *   GMAIL_APP_PASSWORD=abcdabcdabcdabcd
 */
const functions = require('firebase-functions');
const admin = require('firebase-admin');
const nodemailer = require('nodemailer');

if (!admin.apps.length) admin.initializeApp();

// Destinatario de las notificaciones de soporte
const DESTINO = 'alkilapp2026@gmail.com';

// Credenciales: functions.config() (legado) o variables de entorno
const cfg = (typeof functions.config === 'function' ? functions.config() : {}) || {};
const GMAIL_USER = process.env.GMAIL_USER || (cfg.gmail && cfg.gmail.user) || DESTINO;
const GMAIL_PASS = process.env.GMAIL_APP_PASSWORD || (cfg.gmail && cfg.gmail.pass) || '';

// Transportador perezoso: solo se construye si hay contraseña (evita que el
// deploy falle en entornos sin secretos configurados aún)
let transporter = null;
function getTransporter() {
  if (transporter) return transporter;
  if (!GMAIL_PASS) {
    console.warn('[Soporte] GMAIL_APP_PASSWORD no configurada: el correo NO se enviará.');
    return null;
  }
  transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: { user: GMAIL_USER, pass: GMAIL_PASS },
  });
  return transporter;
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
    const mailOptions = {
      from: `"AlkilApp Soporte" <${GMAIL_USER}>`,
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

    const t = getTransporter();
    if (!t) {
      // No reintentamos indefinidamente: el ticket ya quedó registrado en Firestore
      console.error(`[Soporte] Email NO enviado para ticket ${ticketId}: falta GMAIL_APP_PASSWORD.`);
      return null;
    }

    try {
      await t.sendMail(mailOptions);
      console.log(`[Soporte] Email enviado para ticket ${ticketId}`);
    } catch (error) {
      // No lanzamos el error: un fallo de Gmail no debe romper el trigger ni
      // reintentar infinitamente (el ticket sigue vivo en la colección).
      console.error(`[Soporte] Error enviando email del ticket ${ticketId}:`, error.message);
    }
    return null;
  });
