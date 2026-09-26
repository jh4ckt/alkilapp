const express = require('express');
const router = express.Router();
const admin = require('firebase-admin');

const db = admin.firestore();

// GET /api/soporte - Listar todos los tickets con paginación y filtros
router.get('/', async (req, res) => {
  try {
    const { estado, page = 1, limit = 20 } = req.query;
    
    let query = admin.firestore().collection('soporte')
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
    const totalSnapshot = await admin.firestore().collection('soporte').count().get();
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
    console.error('Error al obtener tickets:', error);
    res.status(500).json({ error: 'Error al obtener tickets' });
  }
});

// GET /api/soporte/:id - Obtener un ticket por ID
router.get('/:id', async (req, res) => {
  try {
    const doc = await admin.firestore().collection('soporte').doc(req.params.id).get();
    
    if (!doc.exists) {
      return res.status(404).json({ error: 'Ticket no encontrado' });
    }
    
    res.json({ id: doc.id, ...doc.data() });
  } catch (error) {
    res.status(500).json({ error: 'Error al obtener ticket' });
  }
});

// PATCH /api/soporte/:id/estado - Actualizar estado del ticket
router.patch('/:id/estado', async (req, res) => {
  try {
    const { estado } = req.body;
    const estadosValidos = ['pendiente', 'en_proceso', 'resuelto', 'cerrado'];
    
    if (!['pendiente', 'en_proceso', 'resuelto', 'cerrado'].includes(req.body.estado)) {
      return res.status(400).json({ error: 'Estado inválido. Valores permitidos: pendiente, en_proceso, resuelto, cerrado' });
    }
    
    const ticketRef = admin.firestore().collection('soporte').doc(req.params.id);
    const doc = await ticketRef.get();
    
    if (!doc.exists) {
      return res.status(404).json({ error: 'Ticket no encontrado' });
    }
    
    await ticketRef.update({
      estado: req.body.estado,
      fechaActualizacion: admin.firestore.FieldValue.serverTimestamp()
    });
    
    res.json({ success: true, message: 'Estado actualizado correctamente' });
  } catch (error) {
    console.error('Error al actualizar estado:', error);
    res.status(500).json({ error: 'Error al actualizar estado' });
  }
});

module.exports = router;