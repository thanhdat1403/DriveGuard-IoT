
const jwt = require('jsonwebtoken');

// JWT secret must come from environment variables.
const JWT_SECRET = process.env.JWT_SECRET;

// Default token expiration: 1 hour.
const JWT_EXPIRES_IN = process.env.JWT_EXPIRES_IN || '1h';

// Prevent the application from using an empty JWT secret.
if (!JWT_SECRET || !JWT_SECRET.trim()) {
    throw new Error(
        'JWT_SECRET is required. Please configure it in the environment.'
    );
}

/**
 * Generate a JWT for an authenticated user.
 * @param {Object} user
 * @returns {string}
 */
function generateToken(user) {
    return jwt.sign(
        {
            role: user.role
        },
        JWT_SECRET,
        {
            subject: String(user.id),
            expiresIn: JWT_EXPIRES_IN,
            issuer: 'driveguard-backend',
            audience: 'driveguard-frontend',
            algorithm: 'HS256'
        }
    );
}

/**
 * Verify a JWT and return its decoded payload.
 * @param {string} token
 * @returns {Object}
 */
function verifyToken(token) {
    return jwt.verify(token, JWT_SECRET, {
        issuer: 'driveguard-backend',
        audience: 'driveguard-frontend',
        algorithms: ['HS256']
    });
}

module.exports = {
    generateToken,
    verifyToken
};
