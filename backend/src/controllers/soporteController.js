const admin = require('firebase-admin');

const db = admin.firestore();

// Obtener todos los tickets de soporte
async function getAllTickets(req, res, next) {
  try {
    const { estado, page = 1, limit = 20 } = req.query;
    
    let query = require('firebase-admin').firestore().collection('soporte')
      .orderBy('fechaCreacion', 'desc');
    
    if (estado) {
      query = query.where('estado', '==', estado);
    }
    
    const snapshot = await query.limit(parseInt(limit)).get();
    
    const tickets = [];
    snapshot.forEach(doc => {
      tickets.push({
        id: doc.id,
        ...doc.data()
      });
    });
    
    // Obtener total para paginación
    const totalSnapshot = await require('firebase-admin').firestore().collection('soporte').count().get();
    const total = totalSnapshot.data().count;
    
    res.json({
      tickets,
      pagination: {
        page: parseInt(page),
        limit: parseInt(limit),
        total,
        pages: Math.ceil(total / parseInt(limit))
      }
    });
  } catch (error) {
    next(error);
  }
}

// Actualizar estado de un ticket
async function updateTicketStatus(req, res, next) {
  try {
    const { id } = req.params;
    const { estado } = req.body;
    
    const estadosValidos = ['pendiente', 'en_proceso', 'resuelto', 'cerrado'];
    if (!['pendiente', 'en_proceso', 'resuelto', 'cerrado'].includes(req.body.estado)) {
      return res.status(400).json({ error: 'Estado inválido. Valores permitidos: pendiente, en_proceso, resuelto, cerrado' });
    }
    
    const ticketRef = require('firebase-admin').firestore().collection('soporte').doc(req.params.id);
    const doc = await ticketRef.get();
    
    if (!doc.exists) {
      return res.status(404).json({ error: 'Ticket no encontrado' });
    }
    
    await require('firebase-admin').firestore().collection('soporte').doc(req.params.id).update({
      estado: req.body.estado,
      fechaActualizacion: admin.firestore.FieldValue.serverTimestamp()
    });
    
    res.json({ success: true, message: 'Estado actualizado correctamente' });
  } catch (error) {
    next(error);
  }
}

// Obtener un ticket por ID
async function getTicketById(req, res, next) {
  try {
    const doc = await require('firebase-admin').firestore().collection('soporte').doc(req.params.id).get();
    
    if (!doc.exists) {
      return res.status(404).json({ error: 'Ticket no encontrado' });
    }
    
    res.json({ id: doc.id, ...doc.data() });
  } catch (error) {
    next(error);
  }
}

module.exports = {
  getAllTickets,
  updateTicketStatus,
  getTicketById
};