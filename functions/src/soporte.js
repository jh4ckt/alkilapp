const functions = require('firebase-functions');
const admin = require('firebase-admin');
const nodemailer = require('nodemailer');

admin.initializeApp();

// Configurar Nodemailer con Gmail
const transporter = nodemailer.createTransport({
  service: 'gmail',
  auth: {
    user: process.env.GMAIL_USER || 'alkilapp2026@gmail.com',
    pass: process.env.GMAIL_APP_PASSWORD || 'your-app-password'
  }
});

/**
 * Cloud Function disparada al crear un nuevo documento en la colección "soporte"
 * Envía un email de notificación a alkilapp2026@gmail.com
 */
exports.notificarNuevoTicketSoporte = functions.firestore
  .document('soporte/{ticketId}')
  .onCreate(async (snap, context) => {
    const ticketId = context.params.ticketId;
    const data = snap.data();

    const {
      usuarioId,
      emailContacto,
      asunto,
      mensaje,
      estado,
      fechaCreacion
    } = data;

    const htmlContent = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <style>
          body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; }
          .header { background: #FF6B5E; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
          .content { background: #f9f9f9; padding: 20px; border: 1px solid #eee; }
          .field { margin-bottom: 15px; }
          .label { font-weight: bold; color: #666; font-size: 12px; text-transform: uppercase; }
          .value { font-size: 14px; color: #333; margin-top: 4px; }
          .footer { background: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #888; border-radius: 0 0 8px 8px; }
          .ticket-id { background: #FF6B5E; color: white; padding: 4px 12px; border-radius: 4px; font-size: 12px; font-weight: bold; }
        </style>
      </head>
      <body>
        <div class="header">
          <h1>🔔 Nuevo Ticket de Soporte - AlkilApp</h1>
        </div>
        <div class="content">
          <div class="field">
            <div class="label">ID del Ticket</div>
            <div class="value"><span class="ticket-id">${context.params.ticketId}</span></div>
          </div>
          <div class="field">
            <div class="label">ID Usuario</div>
            <div class="value">${data.usuarioId || 'N/A'}</div>
          </div>
          <div class="field">
            <div class="label">Email de Contacto</div>
            <div class="value">${data.emailContacto || 'N/A'}</div>
          </div>
          <div class="field">
            <div class="label">Asunto</div>
            <div class="value">${data.asunto || 'Sin asunto'}</div>
          </div>
          <div class="field">
            <div class="label">Mensaje</div>
            <div class="value" style="white-space: pre-wrap;">${data.mensaje || 'Sin mensaje'}</div>
          </div>
          <div class="field">
            <div class="label">Estado</div>
            <div class="value">${data.estado || 'pendiente'}</div>
          </div>
          <div class="field">
            <div class="label">Fecha Creación</div>
            <div class="value">${data.fechaCreacion ? data.fechaCreacion.toDate().toLocaleString('es-ES') : new Date().toLocaleString('es-ES')}</div>
          </div>
        </div>
        <div class="footer">
          <p>AlkilApp - Sistema de Soporte Automático</p>
          <p>Ticket ID: <span class="ticket-id">${context.params.ticketId}</span></p>
        </body>
      </html>
    `;

    const mailOptions = {
      from: '"AlkilApp Soporte" <alkilapp2026@gmail.com>',
      to: 'alkilapp2026@gmail.com',
      subject: `[Soporte AlkilApp] ${data.asunto || 'Sin asunto'} - Ticket: ${context.params.ticketId}`,
      html: htmlContent
    };

    try {
      await transporter.sendMail(mailOptions);
      console.log(`Email de notificación enviado para ticket ${context.params.ticketId}`);
    } catch (error) {
      console.error('Error enviando email:', error);
      throw error;
    }
  });

// Función auxiliar para actualizar estado del ticket (para usar desde Admin Panel)
exports.actualizarEstadoTicket = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'No autenticado');
  }

  // Verificar si es admin (opcional: verificar custom claim 'admin')
  const { ticketId, nuevoEstado } = data;
  const estadosValidos = ['pendiente', 'en_proceso', 'resuelto'];
  
  if (!estadosValidos.includes(nuevoEstado)) {
    throw new functions.https.HttpsError('invalid-argument', 'Estado inválido');
  }

  try {
    await admin.firestore().collection('soporte').doc(data.ticketId).update({
      estado: data.nuevoEstado,
      fechaActualizacion: admin.firestore.FieldValue.serverTimestamp()
    });
    return { success: true };
  } catch (error) {
    throw new functions.https.HttpsError('internal', error.message);
  }
};