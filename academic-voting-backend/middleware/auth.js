const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'YWNhZGVtaWN2b3RpbmctandrLXNlY3JldC1rZXktMzI=';

/**
 * Protects routes by validating Bearer JWT.
 * Attaches decoded user to req.user.
 */
function protect(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ status: 401, message: 'Unauthorized: Bearer token is missing' });
  }

  const token = authHeader.split(' ')[1];

  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = {
      userId: decoded.sub || decoded.userId,
      name: decoded.name,
      email: decoded.email,
      role: decoded.role,
    };
    next();
  } catch (err) {
    return res.status(401).json({ status: 401, message: 'Unauthorized: Invalid or expired token' });
  }
}

/**
 * Enforces role-based access.
 */
function authorize(role) {
  return (req, res, next) => {
    if (!req.user || req.user.role !== role) {
      return res.status(403).json({ status: 403, message: 'Forbidden: Access denied' });
    }
    next();
  };
}

module.exports = {
  protect,
  authorize,
  JWT_SECRET,
};
