"""Tính Eye Aspect Ratio (EAR) từ MediaPipe Face Landmarker. (Tức là tỷ lệ khung hình mắt)"""

# ==============================================================================
# 1. KHAI BÁO THƯ VIỆN & CÔNG DỤNG
# ==============================================================================

# Thư viện dataclasses (Tích hợp sẵn trong Python):
# - Tác dụng: Tạo nhanh các lớp (class) chuyên dùng để lưu trữ dữ liệu gọn gàng.
# - Cung cấp: Decorator @dataclass tự động sinh các hàm khởi tạo (__init__), so sánh,
#   in dữ liệu mà không cần viết code thủ công.
from dataclasses import dataclass

# Thư viện math (Tích hợp sẵn trong Python):
# - Tác dụng: Thực hiện các phép tính toán học cơ bản và hình học.
# - Cung cấp: Hàm hypot(dx, dy) tính nhanh cạnh huyền tam giác vuông $\sqrt{dx^2 + dy^2}$,
#   chính là khoảng cách Euclid giữa 2 điểm trên mặt phẳng 2D.
from math import hypot

# Thư viện typing (Tích hợp sẵn trong Python):
# - Tác dụng: Khai báo kiểu dữ liệu (Type Hints) giúp code rõ ràng, dễ kiểm tra lỗi.
# - Cung cấp:
#   + Optional: Cho biết giá trị có thể thuộc kiểu chỉ định hoặc là None.
#   + Sequence: Đại diện cho các kiểu dữ liệu dạng chuỗi/danh sách (list, tuple).
from typing import Optional, Sequence

# ==============================================================================
# 2. HẰNG SỐ CẤU HÌNH ĐIỂM MỐC MẮT
# ==============================================================================
# Thứ tự 6 điểm lấy quanh viền mắt:
# (1: góc ngoài, 2: trên-ngoài, 3: trên-trong, 4: góc trong, 5: dưới-trong, 6: dưới-ngoài)
# Các chỉ số (index) này được quy định cố định theo sơ đồ Face Mesh của MediaPipe.
RIGHT_EYE = (33, 160, 158, 133, 153, 144)
LEFT_EYE = (362, 385, 387, 263, 373, 380)

# Số siêu nhỏ dùng để tránh lỗi chia cho 0 khi tính toán
_EPSILON = 1e-9

# ==============================================================================
# 3. CẤU TRÚC DỮ LIỆU ĐẦU RA
# ==============================================================================
@dataclass(frozen=True)  # frozen=True giúp dữ liệu chỉ được đọc, không thể sửa đổi sau khi tạo
class EARResult:
    """Kết quả EAR của hai mắt và giá trị trung bình."""

    left_ear: float # Chỉ số mở mắt trái
    right_ear: float # Chỉ số mở mắt phải
    average_ear: float # Chỉ số mở mắt trung bình (EAR trung bình của cả hai mắt)

# ==============================================================================
# 4. CÁC HÀM TÍNH TOÁN HÌNH HỌC & EAR
# ==============================================================================
def _distance(point_a, point_b) -> float:
    """Tính khoảng cách đường chim bay (Euclid) giữa hai điểm 2D."""
    return hypot(
        point_a[0] - point_b[0],
        point_a[1] - point_b[1],
    )

def _point(landmarks: Sequence, index: int, width: int, height: int):
    """Lấy điểm mốc theo index và đổi tọa độ chuẩn hóa (0.0 -> 1.0) sang tọa độ pixel thực."""
    landmark = landmarks[index]
    return landmark.x * width, landmark.y * height

def calculate_eye_ear(
    landmarks: Sequence,
    indices: tuple[int, int, int, int, int, int],
    width: int,
    height: int,
) -> float:
    """Tính EAR của một mắt bằng 6 điểm landmark."""
    # Kiểm tra kích thước khung hình hợp lệ
    if width <= 0 or height <= 0:
        raise ValueError("Chiều rộng và chiều cao khung hình phải lớn hơn 0.")

    # Kiểm tra danh sách điểm mốc có đủ số lượng điểm cần thiết không
    if len(landmarks) <= max(indices):
        raise ValueError("Không đủ điểm mốc khuôn mặt để tính toán")

    # Lấy tọa độ pixel (x, y) của 6 điểm quanh mắt
    p1, p2, p3, p4, p5, p6 = (
        _point(landmarks, index, width, height)
        for index in indices
    )

    # Tính chiều rộng của mắt (khoảng cách ngang từ góc ngoài p1 đến góc trong p4)
    horizontal = _distance(p1, p4)

    # Tránh lỗi chia cho 0 nếu hai khóe mắt trùng nhau
    if horizontal <= _EPSILON:
        raise ValueError("Hình học mắt không hợp lệ: khoảng cách ngang bằng 0")

    # Tính 2 khoản cách chiều dọc (độ mở của mí mắt trên và dưới)
    vertical_1 = _distance(p2, p6)
    vertical_2 = _distance(p3, p5)

    # Công thức EAR chuẩn: (dọc_1 + dọc_2) / (2 * ngang)
    return (vertical_1 + vertical_2) / (2.0 * horizontal)

def calculate_ear(
    landmarks: Optional[Sequence],
    width: int,
    height: int,
) -> Optional[EARResult]:
    """Hàm chính trả về kết quả EAR hai mắt; trả về None nếu không có khuôn mặt."""
    if landmarks is None or len(landmarks) == 0:
        return None

    # Tính EAR riêng cho từng bên mắt
    left_ear = calculate_eye_ear(landmarks, LEFT_EYE, width, height)
    right_ear = calculate_eye_ear(landmarks, RIGHT_EYE, width, height)

    # Đóng gói và trả về kết quả hoàn chỉnh
    return EARResult(
        left_ear=left_ear,
        right_ear=right_ear,
        average_ear=(left_ear + right_ear) / 2.0,
    )