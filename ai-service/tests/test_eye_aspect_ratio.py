"""Kiểm thử công thức EAR và các trường hợp dữ liệu không hợp lệ."""

# ==============================================================================
# 1. KHAI BÁO THƯ VIỆN & CÔNG DỤNG
# ==============================================================================

# Thư viện sys (Tích hợp sẵn trong Python):
# - Tác dụng: Tương tác trực tiếp với trình thông dịch và hệ thống của Python.
# - Cung cấp: Danh sách sys.path chứa các đường dẫn tìm kiếm module, giúp thêm
#   thư mục 'src' vào để file test có thể import được file 'eye_aspect_ratio.py'.
import sys

# Thư viện pathlib (Tích hợp sẵn trong Python):
# - Tác dụng: Quản lý đường dẫn tệp tin chuẩn xác trên mọi hệ điều hành.
# - Cung cấp: Lớp Path giúp tìm đường dẫn tương đối từ thư mục test sang thư mục src.
from pathlib import Path

# Thư viện types (Tích hợp sẵn trong Python):
# - Tác dụng: Hỗ trợ tạo các kiểu đối tượng động đơn giản.
# - Cung cấp: Lớp SimpleNamespace giúp tạo nhanh các đối tượng giả lập có thuộc tính
#   .x và .y (giống hệt cấu trúc điểm landmark của MediaPipe) mà không cần tạo class.
from types import SimpleNamespace

# Thư viện pytest (Thư viện ngoài, cài qua 'pip install pytest'):
# - Tác dụng: Framework chuyên dùng để viết và chạy kiểm thử tự động (Unit Test).
# - Cung cấp:
#   + pytest.approx(): So sánh số thực (float) có sai số cho phép (tránh lỗi làm tròn máy tính).
#   + pytest.raises(): Kiểm tra xem đoạn code có ném ra đúng loại lỗi (Exception) mong đợi không.
import pytest


# ==============================================================================
# 2. CẤU HÌNH ĐƯỜNG DẪN & IMPORT MODULE CẦN TEST
# ==============================================================================
# Thêm thư mục 'src' (nằm ngang hàng với thư mục chứa file test này) vào hệ thống
sys.path.insert(
    0,
    str(Path(__file__).resolve().parents[1] / "src"),
)

# Import các hằng số và hàm cần kiểm thử từ module eye_aspect_ratio
from eye_aspect_ratio import (
    LEFT_EYE,
    RIGHT_EYE,
    calculate_ear,
    calculate_eye_ear,
)

# Kích thước khung hình giả lập chuẩn
WIDTH = 640
HEIGHT = 480


# ==============================================================================
# 3. HÀM TẠO DỮ LIỆU GIẢ LẬP (MOCK DATA)
# ==============================================================================
def make_landmarks(left_opening=0.30, right_opening=0.30):
    """Tạo 478 landmark giả lập với độ mở EAR của hai mắt được xác định trước."""
    # Khởi tạo mặc định 478 điểm nằm ở chính giữa khung hình (0.5, 0.5)
    points = [
        SimpleNamespace(x=0.5, y=0.5)
        for _ in range(478)
    ]

    def set_eye(indices, center_x, opening):
        """Gán tọa độ 6 điểm cho một bên mắt sao cho EAR tính ra bằng đúng 'opening'."""
        # Cố định chiều rộng mắt = 100 px -> để EAR = opening thì chiều cao phải = opening * 100 px
        cx = center_x
        cy = 240.0
        half_height = opening * 100.0 / 2.0

        # Tọa độ pixel của 6 điểm quanh viền mắt
        coords = (
            (cx - 50, cy),                 # p1: góc ngoài
            (cx - 25, cy - half_height),   # p2: trên-ngoài
            (cx + 25, cy - half_height),   # p3: trên-trong
            (cx + 50, cy),                 # p4: góc trong
            (cx + 25, cy + half_height),   # p5: dưới-trong
            (cx - 25, cy + half_height),   # p6: dưới-ngoài
        )

        # Chuyển ngược tọa độ pixel về tọa độ chuẩn hóa (0.0 -> 1.0) giống MediaPipe
        for index, (x, y) in zip(indices, coords):
            points[index] = SimpleNamespace(
                x=x / WIDTH,
                y=y / HEIGHT,
            )

    # Thiết lập vị trí và độ mở cho mắt trái (tâm x=420) và mắt phải (tâm x=220)
    set_eye(LEFT_EYE, 420, left_opening)
    set_eye(RIGHT_EYE, 220, right_opening)

    return points


# ==============================================================================
# 4. CÁC CA KIỂM THỬ (TEST CASES)
# ==============================================================================
def test_ear_open_eye():
    """Kiểm thử 1: EAR của mắt mở bình thường phải bằng 0.30."""
    points = make_landmarks()
    result = calculate_ear(points, WIDTH, HEIGHT)

    assert result is not None
    assert result.left_ear == pytest.approx(0.30)
    assert result.right_ear == pytest.approx(0.30)
    assert result.average_ear == pytest.approx(0.30)


def test_ear_closed_eye():
    """Kiểm thử 2: EAR của mắt nhắm phải giảm xuống 0.05."""
    points = make_landmarks(0.05, 0.05)
    result = calculate_ear(points, WIDTH, HEIGHT)

    assert result is not None
    assert result.average_ear == pytest.approx(0.05)


def test_ear_different_eye_openings():
    """Kiểm thử 3: EAR trung bình phải đúng khi hai mắt mở khác nhau (nháy mắt)."""
    points = make_landmarks(0.30, 0.10)
    result = calculate_ear(points, WIDTH, HEIGHT)

    assert result is not None
    assert result.left_ear == pytest.approx(0.30)
    assert result.right_ear == pytest.approx(0.10)
    assert result.average_ear == pytest.approx(0.20)


def test_ear_no_face():
    """Kiểm thử 4: Khi không phát hiện khuôn mặt (None hoặc rỗng) phải trả về None."""
    assert calculate_ear(None, WIDTH, HEIGHT) is None
    assert calculate_ear([], WIDTH, HEIGHT) is None


def test_ear_insufficient_landmarks():
    """Kiểm thử 5: Dữ liệu bị thiếu điểm mốc (chỉ có 10 điểm) phải báo lỗi ValueError."""
    with pytest.raises(ValueError, match="Không đủ điểm mốc"):
        calculate_eye_ear(
            make_landmarks()[:10],
            LEFT_EYE,
            WIDTH,
            HEIGHT,
        )


def test_ear_zero_horizontal_distance():
    """Kiểm thử 6: Hai khóe mắt trùng nhau (chiều ngang = 0) phải báo lỗi ValueError."""
    points = make_landmarks()

    # Gán tọa độ góc trong (index 3) trùng với góc ngoài (index 0)
    points[LEFT_EYE[3]] = points[LEFT_EYE[0]]

    with pytest.raises(ValueError, match="khoảng cách ngang bằng 0"):
        calculate_eye_ear(
            points,
            LEFT_EYE,
            WIDTH,
            HEIGHT,
        )
