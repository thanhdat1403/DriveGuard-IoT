//Middleware (Hàm trung gian) dùng để bảo mật cho ứng dụng
/** Nó có nhiệm vụ ktr xem người dùng đã đăng nhập chưa (Xác thực - Authentication)
 * và họ có đủ quyền hạn để truy cập vào 1 tính năng cụ thể nào đó hay không (Phân quyền - Authorization)
 */
// Nạp hàm 'verifyToken' từ file cấu hình jwt.js để giải mã và kiểm tra tính hợp lệ của mã Token (JWT)
const { verifyToken } = require('../config/jwt');
const pool = require('../config/database');

/**
 * MIDDLEWARE 1: Xác thực người dùng (Kiểm tra xem đã đăng nhập chưa)
 * Hàm này sẽ đứng chặn trước các API cần bảo mật để kiểm tra Token
 */
async function authenticateToken(req, res, next) {
    // Lấy chuỗi 'Authorization' từ trong Header của yêu cầu (request) do Client gửi lên
    const authorization = req.headers.authorization;

    // Kiểm tra: Nếu không có chuỗi Authorization HOẶC chuỗi đó không bắt đầu bằng chữ 'Bearer' (định dạng chuẩn của Token)
    if (!authorization || !authorization.startsWith('Bearer ')){
        // Trả về mã lỗi 401 (chưa xác thực) và yêu cầu người dùng đăng nhập
        return res.status(401).json({
            success: false,
            message: 'Vui lòng đăng nhập để tiếp tục'
        });
    }

    // Cắt bỏ 7 ký tự đầu tiên ('Bearer') và xóa khoảng trắng thừa để lấy ra đoạn mã Token nguyên bản
    const token = authorization.slice(7).trim();

    // Nếu sau khi cắt mà chuỗi token trống rỗng
    if (!token){
        // Trả về lỗi 401 thông báo Token không hợp lệ
        return res.status(401).json({
            success: false,
            message: 'Token không hợp lệ'
        });
    }

    let decoded;


    // Bước 1: Xác minh chữ ký và thời hạn JWT
    try {
        // Tiến hành giải mã Token bằng hàm verifyToken. Nếu Token giả hoặc hết hạn, dòng này sẽ ném ra lỗi (nhảy xuống khối catch)
        const decoded = verifyToken(token);
        
    } catch (error) {
        // Nếu có bất kỳ lỗi nào xảy ra trong quá trình giải mã (Token bị sửa đổi, Token hết hạn...)
        return res.status(401).json({
            success: false,
            message: 'Token không hợp lệ hoặc đã hết hạn'
        });
    }

    const userId = Number(decoded.sub);
    
    if (!Number.isSafeInteger(userId) || userId <= 0){
        return res.status(401).json({
            success: false,
            message: 'Token không hợp lệ'
        });
    }

    try {
        // Bước 2; Kiểm tra trạng thái và vai trò hiện tại trong MariaDB
        const [rows] = await pool.execute(
            `SELECT
                u.id,
                u.is_active,
                r.name AS role
             FROM users u
             INNER JOIN roles r ON r.id = u.role_id
             WHERE u.id = ?
             LIMIT 1`,
            [userId]
        );

        if (rows.length === 0 || !rows[0].is_active) {
            return res.status(401).json({
                success: false,
                message: 'Tài khoản không tồn tại hoặc đã bị vô hiệu hóa'
            });
        }

        // Chỉ sử dụng vai trò mới nhất từ database
        req.user = {
            id: Number(rows[0].id),
            role: rows[0].role
        };
        
        return next();
        
    } catch (error) {
        console.error('Authentication database check failed:', error.message);

        return res.status(500).json({
            success: false,
            message: 'Không thể xác minh tài khoản lúc này'
        });
    }
}

/**
 * MIDDLEWARE 2: Phân quyền người dùng (Kiểm tra xem có đủ quyền truy cập không)
 * Sử dụng cú pháp '...allowedRoles' để có thể truyền vào danh sách nhiều quyền (ví dụ: 'admin', 'manager')
 */
function authorizeRoles(...allowedRoles) {
    // Trả về một hàm Middleware có cấu trúc (req, res, next) tiêu chuẩn của Express
    return (req, res, next) => {
        // Nếu đối tượng 'req.user' không tồn tại (nghĩa là quên chưa chạy qua hàm authenticateToken ở trên)
        if (!req.user){
            return res.status(401).json({
                success: false,
                message: 'Vui lòng đăng nhập để tiếp tục'
            });
        }

        // Kiểm tra xem Quyền của người dùng hiện tại (req.user.role) có nằm trong danh sách quyền được phép (allowedRoles) hay không
        if (!allowedRoles.includes(req.user.role)){
            // Nếu không nằm trong danh sách, trả về lỗi 403 (Bị cấm / Không đủ quyền truy cập)
            return res.status(403).json({
                success: false,
                message: 'Bạn không có quyền thực hiện thao tác này'
            });
        }

        // Nếu có quyền, gọi next() để cho phép truy cập vào tính năng
        next();
    };

}

// Xuất (export) hai hàm Middleware này ra ngoài để các file khác trong dự án có thể nạp vào sử dụng
module.exports = {
    authenticateToken,
    authorizeRoles
};