const admin = require('firebase-admin');

async function requireAuth(req, res, next) {
  const authHeader = req.headers.authorization;
  
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Token de autorización requerido' });
  }
  
  const token = authHeader.split('Bearer ')[1];
  
  try {
    const decodedToken = await require('firebase-admin').auth().verifyIdToken(token);
    req.user = decodedToken;
    next();
  } catch (error) {
    console.error('Error verificando token:', error);
    res.status(401).json({ error: 'Token inválido o expirado' });
  }
}

function requireAdmin(req, res, next) {
  if (req.user && req.user.admin === true) {
    next();
  } else {
    res.status(403).json({ error: 'Acceso denegado: se requieren permisos de administrador' });
  }
}

module.exports = { requireAuth, requireAdmin };