// Nạp các thư viện (modules) cần thiết vào ứng dụng
const {
    authenticateToken,
    authorizeRoles
} = require('./middleware/authMiddleware');

const express = require('express'); // Thư viện Express.js dùng để tạo và quản lý máy chủ Web (Backend)
const cors = require('cors'); // Thư viện CORS dùng để cho phép ứng dụng Frontend truy cập vào API từ một tên miền khác
const pool = require('./config/database'); // Nạp cấu hình kết nối cơ sở dữ liệu MariaDB từ file database.js trong thư mục config
const { login } = require('./controllers/authController'); // Nạp hàm logic xử lý đăng nhập (login) từ thư mục controllers

const app = express(); // Khởi tạo một ứng dụng Express (đối tượng đại diện cho ứng dụng máy chủ của bạn)
const PORT = Number(process.env.PORT) || 3000; // Định nghĩa cổng chạy server: lấy từ biến môi trường của hệ thống, nếu không có thì mặc định là cổng 3000

// ==========================================
// CẤU HÌNH MIDDLEWARE (Các hàm trung gian xử lý request trước khi vào API chính)
// ==========================================

// Cấu hình CORS: Chỉ cho phép ứng dụng Frontend chạy từ địa chỉ 'http://localhost:5173' gửi yêu cầu (request) tới Backend này
app.use(cors({
    origin: 'http://localhost:5173'
}));

// Cho phép Backend đọc và hiểu dữ liệu gửi lên dưới dạng JSON từ Client, giới hạn dung lượng tối đa của 1 request là 100kb
app.use(express.json({ limit: '100kb' }));


// ==========================================
// ĐỊNH NGHĨA CÁC ĐƯỜNG DẪN API (ROUTES)
// ==========================================

// API 1: Kiểm tra trạng thái hoạt động của Backend
// Khi Client truy cập bằng phương thức GET vào đường dẫn '/api/health'
app.get('/api/health', (req, res) => {
    // Trả về mã trạng thái 200 (Thành công) cùng một chuỗi dữ liệu định dạng JSON thông báo Server đang chạy ổn định
    res.status(200).json({
        success: true,
        message: 'DriveGuard Backend is running',
        timestamp: new Date().toISOString() // Lấy thời gian hiện tại của hệ thống theo chuẩn quốc tế
    });
});

// API 2: Kiểm tra kết nối tới cơ sở dữ liệu MariaDB
// Sử dụng từ khóa 'async' vì việc truy vấn vào Database là một tác vụ bất đồng bộ (mất thời gian chờ)
app.get('/api/health/db', async (req, res) => {
    try {
        // Thực hiện một câu lệnh SQL để lấy tên cơ sở dữ liệu hiện tại và phiên bản của MariaDB
        // Từ khóa 'await' dùng để bắt ứng dụng dừng lại chờ cho đến khi Database trả về kết quả
        const [rows] = await pool.query(
            'SELECT DATABASE() AS databaseName, VERSION() AS databaseVersion'
        );

        // Nếu kết nối thành công, trả về mã 200 cùng thông tin tên và phiên bản Database vừa lấy được
        res.status(200).json({
            success: true,
            message: 'MariaDB connection successful',
            database: rows[0].databaseName,
            version: rows[0].databaseVersion
        });
        
    } catch (error) {
        // Nếu có lỗi xảy ra (Ví dụ: sai mật khẩu database, DB chưa bật...), đoạn code trong 'catch' này sẽ chạy
        console.error('Database health check failed;', error.message); // In chi tiết lỗi ra màn hình terminal của lập trình viên

        // Trả về mã lỗi 500 (Lỗi phía Server) và thông báo không thể kết nối tới MariaDB cho phía Client biết
        res.status(500).json({
            success: false,
            message: 'Cannot connect to MariaDB'
        });
    }
});

// API 3: Xử lý chức năng đăng nhập
// Khi Client gửi yêu cầu bằng phương thức POST lên đường dẫn '/api/auth/login' kèm tài khoản/mật khẩu,
// Hệ thống sẽ chuyển tiếp yêu cầu đó cho hàm 'login' (được nạp từ authController) tự động xử lý tiếp dữ liệu.
app.post('/api/auth/login', login);

// ==========================================
// KHỞI ĐỘNG MÁY CHỦ (START SERVER)
// ==========================================

// Kích hoạt máy chủ lắng nghe các yêu cầu gửi đến thông qua cổng (PORT) đã cấu hình ở trên
app.listen(PORT, () => {
    // Khi máy chủ khởi động thành công, in một dòng thông báo kèm địa chỉ chạy web ra terminal
    console.log(
        `DriveGuard Backend running at http://localhost:${PORT}`
    );
});

// API kiểm tra JWT và quyền ADMIN
app.get(
    '/api/auth/me',
    authenticateToken,
    (req, res) => {
        res.status(200).json({
            success: true,
            message: 'Xác thực JWT thành công',
            data: {
                id: req.user.id,
                role: req.user.role
            }
        });
    }
);

// API thử nghiệm chỉ dành cho ADMIN
app.get(
    '/api/admin/test',
    authenticateToken,
    authorizeRoles('ADMIN'),
    (req, res) => {
        res.status(200).json({
            success: true,
            message: 'ADMIN access granted',
            data: {
                id: req.user.id,
                role: req.user.role
            }
        });
    }
);