const mysql = require('mysql2/promise'); // mysql2: Đọc kết nối MariaDB, thực hiện truy vấn SQL và hỗ trợ connection pool
const path = require('path');
const dotenv = require('dotenv'); // dotenv: Đọc các biến cấu hình từ file .env, tránh viết thông tin kết nối trực tiếp trong mã nguồn

// Đọc file .env ở thư mục gốc D:\datnIoT
dotenv.config({
    path: path.resolve(__dirname, '../../../.env') //Đường dẫn ../../../.env được tính từ src/config/database.js trở về thư mục gốc D:\datnIoT.
});

const pool = mysql.createPool({
    host: process.env.DB_HOST,
    port: Number(process.env.DB_PORT),
    user: process.env.MARIADB_USER,
    password: process.env.MARIADB_PASSWORD,
    database: process.env.DB_NAME,
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
});

module.exports = pool;