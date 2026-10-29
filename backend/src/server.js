
const express = require('express');
const pool = require('./config/database');

const app = express();
const PORT = 3000;

app.use(express.json());

// Kiểm tra Backend
app.get('/api/health', (req, res) => {
    res.status(200).json({
        success: true,
        message: 'DriveGuard Backend is running',
        timestamp: new Date().toISOString()
    });
});

// Kiểm tra kết nối MariaDB
app.get('/api/health/db', async (req, res) => {
    try {
        const [rows] = await pool.query(
            'SELECT DATABASE() AS databaseName, VERSION() AS databaseVersion'
        );

        res.status(200).json({
            success: true,
            message: 'MariaDB connection successful',
            database: rows[0].databaseName,
            version: rows[0].databaseVersion
        });
    } catch (error) {
        console.error('Database health check failed:', error.message);

        res.status(500).json({
            success: false,
            message: 'Cannot connect to MariaDB'
        });
    }
});

// Khởi động Backend
app.listen(PORT, () => {
    console.log(`DriveGuard Backend running at http://localhost:${PORT}`);
});
