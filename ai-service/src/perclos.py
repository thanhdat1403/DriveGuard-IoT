"""
DriveGuard IoT - G03.07.02
PERCLOS: Percentage of Eye Closure over Time.

Theo dõi tỷ lệ thời gian mắt nhắm dựa trên EAR.
Sử dụng timestamp thực tế thay vì đếm số frame.
"""

# ==============================================================================
# 1. KHAI BÁO THƯ VIỆN & CÔNG DỤNG
# ==============================================================================

# Thư viện collections (Tích hợp sẵn trong Python):
# - Tác dụng: Cung cấp các cấu trúc dữ liệu tối ưu hiệu năng cao.
# - Cung cấp: Hàng đợi hai đầu deque() giúp thêm mới ở cuối (append) và xóa dữ liệu
#   cũ ở đầu (popleft) cực nhanh cho bài toán cửa sổ trượt.
from collections import deque

# Thư viện dataclasses (Tích hợp sẵn trong Python):
# - Tác dụng: Tạo nhanh các lớp chuyên lưu trữ dữ liệu đầu ra gọn gàng.
# - Cung cấp: Decorator @dataclass tự sinh hàm khởi tạo cho kết quả PERCLOSResult.
from dataclasses import dataclass

# Thư viện math (Tích hợp sẵn trong Python):
# - Tác dụng: Thực hiện các kiểm tra và phép tính toán học.
# - Cung cấp: Hàm isfinite() kiểm tra xem số có hợp lệ không (loại bỏ NaN hoặc vô cực).
from math import isfinite

# Thư viện typing (Tích hợp sẵn trong Python):
# - Tác dụng: Khai báo kiểu dữ liệu giúp code rõ ràng, dễ kiểm soát lỗi.
# - Cung cấp: Optional cho biết biến có thể nhận giá trị cụ thể hoặc None.
from typing import Optional


# ==============================================================================
# 2. CẤU TRÚC DỮ LIỆU ĐẦU RA
# ==============================================================================
@dataclass(frozen=True)  # frozen=True khóa dữ liệu, tránh bị sửa đổi nhầm sau khi tính
class PERCLOSResult:
    """Gói dữ liệu trả về sau mỗi lần cập nhật trạng thái mắt."""
    perclos: Optional[float]      # Tỷ lệ % mắt nhắm (0 -> 100), None nếu chưa đủ dữ liệu
    closed_seconds: float         # Tổng số giây mắt nhắm trong cửa sổ thời gian
    valid_seconds: float          # Tổng số giây đo được hợp lệ (có nhận diện thấy mặt)
    coverage_ratio: float         # Tỷ lệ độ phủ dữ liệu hợp lệ (valid_seconds / window_seconds)
    is_ready: bool                # True nếu đã đủ thời gian và độ phủ để tính PERCLOS
    eye_closed: Optional[bool]    # Trạng thái mắt ở frame hiện tại (True: nhắm, False: mở)


# ==============================================================================
# 3. BỘ THEO DÕI CHỈ SỐ PERCLOS
# ==============================================================================
class PERCLOSTracker:
    """Theo dõi PERCLOS trong cửa sổ thời gian trượt."""

    def __init__(
        self,
        ear_threshold: float = 0.18,         # Ngưỡng EAR: nhỏ hơn mức này coi là nhắm mắt
        window_seconds: float = 60.0,        # Độ dài cửa sổ thời gian theo dõi (60 giây)
        max_sample_gap_seconds: float = 0.5, # Khoảng trễ tối đa cho phép giữa 2 lần đo
        min_coverage_ratio: float = 0.8,     # Phải có ít nhất 80% dữ liệu hợp lệ mới tính
    ):
        # Kiểm tra tính hợp lệ của các tham số cấu hình đầu vào
        if not isfinite(ear_threshold) or ear_threshold <= 0:
            raise ValueError("ear_threshold must be positive")

        if not isfinite(window_seconds) or window_seconds <= 0:
            raise ValueError("window_seconds must be positive")

        if not isfinite(max_sample_gap_seconds) or max_sample_gap_seconds <= 0:
            raise ValueError("max_sample_gap_seconds must be positive")

        if not isfinite(min_coverage_ratio) or not 0 < min_coverage_ratio <= 1:
            raise ValueError("min_coverage_ratio must be in (0, 1]")

        self.ear_threshold = ear_threshold
        self.window_seconds = window_seconds
        self.max_sample_gap_seconds = max_sample_gap_seconds
        self.min_coverage_ratio = min_coverage_ratio

        # Hàng đợi lưu các khoảng thời gian đo được: (bắt_đầu, kết_thúc, mắt_có_nhắm_không)
        self._intervals = deque()
        self._last_timestamp = None
        self._last_closed = None
        self._start_timestamp = None

    def reset(self):
        """Xóa toàn bộ lịch sử đo để bắt đầu phiên theo dõi mới."""
        self._intervals.clear()
        self._last_timestamp = None
        self._last_closed = None
        self._start_timestamp = None

    def update(
        self,
        timestamp: float,
        ear: Optional[float],
    ) -> PERCLOSResult:
        """Nhận mẫu EAR mới theo dấu thời gian (giây) và trả về kết quả PERCLOS."""
        # Đảm bảo thời gian hợp lệ và luôn tiến tới (tăng dần)
        if not isfinite(timestamp):
            raise ValueError("timestamp must be finite")

        if self._last_timestamp is not None and timestamp <= self._last_timestamp:
            raise ValueError("timestamp must be strictly increasing")

        if ear is not None and (not isfinite(ear) or ear < 0):
            raise ValueError("ear must be finite and >= 0")

        # Lưu mốc thời gian bắt đầu phiên chạy đầu tiên
        if self._start_timestamp is None:
            self._start_timestamp = timestamp

        # Xác định mắt hiện tại đang nhắm hay mở (None nếu mất dấu khuôn mặt)
        current_closed = (ear < self.ear_threshold) if ear is not None else None

        if self._last_timestamp is not None:
            delta = timestamp - self._last_timestamp

            # Chỉ lưu khoảng thời gian này nếu camera không bị lag quá lâu (<= 0.5s)
            # và cả hai lần đo liên tiếp đều nhận diện được mặt
            if (
                delta <= self.max_sample_gap_seconds
                and self._last_closed is not None
                and current_closed is not None
            ):
                self._intervals.append(
                    (self._last_timestamp, timestamp, self._last_closed)
                )

        # Cập nhật trạng thái làm mốc cho lần gọi tiếp theo
        self._last_timestamp = timestamp
        self._last_closed = current_closed

        return self._calculate(timestamp, current_closed)

    def _calculate(
        self,
        timestamp: float,
        current_closed: Optional[bool],
    ) -> PERCLOSResult:
        """Tính toán tỷ lệ % nhắm mắt trong 60 giây gần nhất."""
        window_start = timestamp - self.window_seconds

        # Xóa bỏ các dữ liệu cũ đã trôi ra ngoài cửa sổ 60 giây
        while self._intervals and self._intervals[0][1] <= window_start:
            self._intervals.popleft()

        closed_seconds = 0.0
        valid_seconds = 0.0

        # Cộng dồn thời gian hợp lệ và thời gian nhắm mắt nằm trong cửa sổ
        for start, end, closed in self._intervals:
            # Cắt phần thời gian bị lẹm ra ngoài mốc window_start (nếu có)
            duration = max(0.0, end - max(start, window_start))
            valid_seconds += duration

            if closed:
                closed_seconds += duration

        # Tính tỷ lệ độ phủ dữ liệu (tối đa là 1.0 tức 100%)
        coverage_ratio = min(valid_seconds / self.window_seconds, 1.0)
        elapsed = timestamp - self._start_timestamp

        # Chỉ sẵn sàng xuất kết quả khi đã chạy đủ 60s và dữ liệu phủ >= 80%
        is_ready = (
            elapsed >= self.window_seconds
            and coverage_ratio >= self.min_coverage_ratio
        )

        # Tính phần trăm PERCLOS = (thời gian nhắm / tổng thời gian hợp lệ) * 100
        perclos = (
            100.0 * closed_seconds / valid_seconds
            if is_ready and valid_seconds > 0
            else None
        )

        return PERCLOSResult(
            perclos=perclos,
            closed_seconds=closed_seconds,
            valid_seconds=valid_seconds,
            coverage_ratio=coverage_ratio,
            is_ready=is_ready,
            eye_closed=current_closed,
        )
