function errorHandler(err, req, res, next) {
  console.error('Error:', err);

  // Error de validación de Mongoose/Firebase
  if (err.name === 'ValidationError') {
    return res.status(400).json({
      error: 'Error de validación',
      details: err.message
    });
  }

  // Error de Firebase/Firestore
  if (err.code && err.code.startsWith('permission-denied')) {
    return res.status(403).json({
      error: 'Acceso denegado',
      message: 'No tienes permisos para realizar esta acción'
    });
  }

  // Error genérico
  console.error('Error interno:', err);
  res.status(500).json({
    error: 'Error interno del servidor',
    message: process.env.NODE_ENV === 'development' ? err.message : 'Error interno'
  });
}

module.exports = { errorHandler };