"""DriveGuard IoT - Phát hiện nhắm mắt kéo dài dựa trên EAR."""

# ==============================================================================
# 1. KHAI BÁO THƯ VIỆN & CÔNG DỤNG
# ==============================================================================

# Thư viện dataclasses (Tích hợp sẵn trong Python):
# - Tác dụng: Tạo nhanh các lớp (class) chuyên dùng để đóng gói dữ liệu đầu ra.
# - Cung cấp: Decorator @dataclass tự động sinh hàm khởi tạo cho EyeClosureResult.
from dataclasses import dataclass

# Thư viện enum (Tích hợp sẵn trong Python):
# - Tác dụng: Định nghĩa tập hợp các hằng số cố định (liệt kê) giúp tránh gõ sai chuỗi.
# - Cung cấp: Lớp Enum để tạo danh sách trạng thái mắt và các loại sự kiện cảnh báo.
from enum import Enum

# Thư viện math (Tích hợp sẵn trong Python):
# - Tác dụng: Thực hiện các kiểm tra tính hợp lệ của số học.
# - Cung cấp: Hàm isfinite() kiểm tra số truyền vào có hợp lệ không (loại bỏ NaN/vô cực).
from math import isfinite

# Thư viện typing (Tích hợp sẵn trong Python):
# - Tác dụng: Khai báo kiểu dữ liệu giúp code rõ ràng, dễ kiểm soát lỗi.
# - Cung cấp: Optional cho biết giá trị trả về có thể là kiểu chỉ định hoặc None.
from typing import Optional


# ==============================================================================
# 2. ĐỊNH NGHĨA TRẠNG THÁI & SỰ KIỆN
# ==============================================================================
class EyeClosureState(str, Enum):
    """Các trạng thái hiện tại của mắt tài xế."""
    OPEN = "OPEN"                            # Mắt đang mở bình thường
    CLOSED = "CLOSED"                        # Mắt đang nhắm (nhưng chưa quá 2 giây)
    PROLONGED_CLOSURE = "PROLONGED_CLOSURE"  # Nhắm mắt kéo dài vượt ngưỡng nguy hiểm
    UNKNOWN = "UNKNOWN"                      # Không nhận diện được khuôn mặt


class EyeClosureEvent(str, Enum):
    """Các sự kiện tức thời (chỉ kích hoạt 1 lần tại thời điểm chuyển trạng thái)."""
    PROLONGED_CLOSURE = "PROLONGED_CLOSURE"  # Bắt đầu chạm ngưỡng ngủ gật -> Bật còi/cảnh báo
    RECOVERED = "RECOVERED"                  # Đã mở mắt lại sau khi bị cảnh báo -> Tắt cảnh báo


@dataclass(frozen=True)  # frozen=True khóa dữ liệu đầu ra, không cho phép sửa đổi nhầm
class EyeClosureResult:
    """Gói kết quả trả về sau mỗi lần cập nhật khung hình."""
    state: EyeClosureState               # Trạng thái mắt hiện tại
    closed_duration_seconds: float       # Số giây mắt đã nhắm liên tục tính đến hiện tại
    event: Optional[EyeClosureEvent]     # Sự kiện vừa xảy ra (nếu có), bình thường là None
    is_alert_active: bool                # True trong suốt thời gian đang báo động ngủ gật


# ==============================================================================
# 3. BỘ PHÁT HIỆN NHẮM MẮT KÉO DÀI
# ==============================================================================
class EyeClosureDetector:
    """Theo dõi nhắm mắt liên tục và chỉ phát một cảnh báo mỗi đợt."""

    def __init__(
        self,
        ear_threshold: float = 0.18,         # Ngưỡng EAR: nhỏ hơn mức này tính là nhắm mắt
        prolonged_seconds: float = 2.0,      # Nhắm liên tục >= 2.0 giây sẽ kích hoạt cảnh báo
        max_sample_gap_seconds: float = 0.5, # Nếu camera lag > 0.5s sẽ reset bộ đếm để tránh báo giả
    ):
        # Kiểm tra tính hợp lệ của các thông số cấu hình
        if not isfinite(ear_threshold) or ear_threshold <= 0:
            raise ValueError("ear_threshold must be positive")

        if not isfinite(prolonged_seconds) or prolonged_seconds <= 0:
            raise ValueError("prolonged_seconds must be positive")

        if not isfinite(max_sample_gap_seconds) or max_sample_gap_seconds <= 0:
            raise ValueError("max_sample_gap_seconds must be positive")

        self.ear_threshold = ear_threshold
        self.prolonged_seconds = prolonged_seconds
        self.max_sample_gap_seconds = max_sample_gap_seconds

        self.reset()

    def reset(self):
        """Đưa bộ đếm và trạng thái cảnh báo về ban đầu."""
        self._last_timestamp = None  # Dấu thời gian của lần đo trước đó
        self._closed_since = None    # Mốc thời gian bắt đầu nhắm mắt (None nếu mắt đang mở)
        self._alert_active = False   # Cờ đánh dấu hiện tại có đang bật cảnh báo hay không

    def update(
        self,
        timestamp: float,
        ear: Optional[float],
    ) -> EyeClosureResult:
        """Nhận chỉ số EAR mới theo thời gian thực (giây) và đánh giá trạng thái."""
        # 1. Kiểm tra đầu vào hợp lệ (thời gian phải tăng dần, EAR >= 0)
        if not isfinite(timestamp):
            raise ValueError("timestamp must be finite")

        if self._last_timestamp is not None and timestamp <= self._last_timestamp:
            raise ValueError("timestamp must be strictly increasing")

        if ear is not None and (not isfinite(ear) or ear < 0):
            raise ValueError("ear must be finite and >= 0")

        # 2. Xử lý gián đoạn tín hiệu (camera bị treo hoặc rớt frame quá 0.5s)
        if self._last_timestamp is not None:
            gap = timestamp - self._last_timestamp
            if gap > self.max_sample_gap_seconds:
                self._closed_since = None
                self._alert_active = False

        self._last_timestamp = timestamp

        # 3. Trường hợp mất dấu khuôn mặt (ear is None) -> Reset toàn bộ và báo UNKNOWN
        if ear is None:
            self._closed_since = None
            self._alert_active = False

            return EyeClosureResult(
                state=EyeClosureState.UNKNOWN,
                closed_duration_seconds=0.0,
                event=None,
                is_alert_active=False,
            )

        # 4. Trường hợp MẮT MỞ (ear >= ngưỡng)
        if ear >= self.ear_threshold:
            # Nếu trước đó đang báo động thì ghi nhận sự kiện RECOVERED (đã tỉnh táo lại)
            recovered = self._alert_active

            self._closed_since = None
            self._alert_active = False

            return EyeClosureResult(
                state=EyeClosureState.OPEN,
                closed_duration_seconds=0.0,
                event=EyeClosureEvent.RECOVERED if recovered else None,
                is_alert_active=False,
            )

        # 5. Trường hợp MẮT NHẮM (ear < ngưỡng)
        # Nếu là frame đầu tiên bắt đầu nhắm mắt, lưu lại mốc thời gian bắt đầu
        if self._closed_since is None:
            self._closed_since = timestamp

        # Tính tổng thời gian đã nhắm mắt liên tục
        duration = timestamp - self._closed_since

        # Nếu thời gian nhắm vượt quá ngưỡng nguy hiểm (ví dụ >= 2.0 giây)
        if duration >= self.prolonged_seconds:
            # Chỉ phát sự kiện cảnh báo ở đúng frame đầu tiên chạm ngưỡng
            first_alert = not self._alert_active
            self._alert_active = True

            return EyeClosureResult(
                state=EyeClosureState.PROLONGED_CLOSURE,
                closed_duration_seconds=duration,
                event=EyeClosureEvent.PROLONGED_CLOSURE if first_alert else None,
                is_alert_active=True,
            )

        # Mắt đang nhắm nhưng chưa đủ 2 giây (ví dụ chớp mắt tự nhiên) -> Trạng thái CLOSED
        return EyeClosureResult(
            state=EyeClosureState.CLOSED,
            closed_duration_seconds=duration,
            event=None,
            is_alert_active=False,
        )
