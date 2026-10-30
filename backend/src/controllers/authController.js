
const bcrypt = require('bcryptjs');
const pool = require('../config/database');
const { generateToken } = require('../config/jwt');

async function login(req, res) {
    try {
        const { username, password } = req.body || {};

        if (
            typeof username !== 'string' ||
            typeof password !== 'string' ||
            !username.trim() ||
            !password
        ) {
            return res.status(400).json({
                success: false,
                message: 'Vui lòng nhập username và password'
            });
        }

        const [rows] = await pool.execute(
            `SELECT
                u.id,
                u.username,
                u.password_hash,
                u.full_name,
                u.email,
                u.is_active,
                r.name AS role
             FROM users u
             INNER JOIN roles r ON r.id = u.role_id
             WHERE u.username = ?
             LIMIT 1`,
            [username.trim()]
        );

        if (rows.length === 0) {
            return res.status(401).json({
                success: false,
                message: 'Tên đăng nhập hoặc mật khẩu không chính xác'
            });
        }

        const user = rows[0];

        const passwordMatches = await bcrypt.compare(
            password,
            user.password_hash
        );

        if (!passwordMatches) {
            return res.status(401).json({
                success: false,
                message: 'Tên đăng nhập hoặc mật khẩu không chính xác'
            });
        }

        if (!user.is_active) {
            return res.status(403).json({
                success: false,
                message: 'Tài khoản đã bị vô hiệu hóa'
            });
        }

        const token = generateToken({
            id: user.id,
            role: user.role
        });

        return res.status(200).json({
            success: true,
            message: 'Đăng nhập thành công',
            data: {
                accessToken: token,
                tokenType: 'Bearer',
                expiresIn: process.env.JWT_EXPIRES_IN || '1h',
                user: {
                    id: user.id,
                    username: user.username,
                    fullName: user.full_name,
                    email: user.email,
                    role: user.role
                }
            }
        });
    } catch (error) {
        console.error('Login failed:', error.message);

        return res.status(500).json({
            success: false,
            message: 'Đã xảy ra lỗi khi đăng nhập'
        });
    }
}

module.exports = { login };
