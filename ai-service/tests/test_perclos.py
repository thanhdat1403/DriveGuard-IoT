
"""Unit Test thuật toán PERCLOS của DriveGuard IoT."""

import sys
from pathlib import Path

import pytest

sys.path.insert(
    0,
    str(Path(__file__).resolve().parents[1] / "src"),
)

from perclos import PERCLOSTracker


def feed(tracker, start, end, closed_intervals=()):
    """Tạo mẫu EAR mỗi 0,1 giây để mô phỏng camera 10 FPS."""
    result = None
    for i in range(round(start * 10), round(end * 10) + 1):
        timestamp = i / 10.0
        closed = any(
            begin <= timestamp < finish
            for begin, finish in closed_intervals
        )
        ear = 0.05 if closed else 0.30
        result = tracker.update(timestamp, ear)
    return result


def test_perclos_all_open():
    """T001: Mắt mở liên tục phải cho PERCLOS = 0%."""
    tracker = PERCLOSTracker()
    result = feed(tracker, 0, 60)

    assert result.is_ready
    assert result.perclos == pytest.approx(0.0)
    assert result.valid_seconds == pytest.approx(60.0)


def test_perclos_all_closed():
    """T002: Mắt nhắm liên tục phải cho PERCLOS = 100%."""
    tracker = PERCLOSTracker()
    result = feed(tracker, 0, 60, [(0, 60.1)])

    assert result.is_ready
    assert result.perclos == pytest.approx(100.0)


def test_perclos_thirty_percent_closed():
    """T003: Nhắm 18/60 giây phải cho PERCLOS = 30%."""
    tracker = PERCLOSTracker()
    result = feed(tracker, 0, 60, [(0, 18)])

    assert result.is_ready
    assert result.closed_seconds == pytest.approx(18.0)
    assert result.perclos == pytest.approx(30.0)


def test_perclos_not_ready():
    """T004: Chưa đủ cửa sổ 60 giây thì chưa xuất PERCLOS."""
    tracker = PERCLOSTracker()
    result = feed(tracker, 0, 30)

    assert not result.is_ready
    assert result.perclos is None



def test_perclos_missing_face():
    """T005: Mất khuôn mặt không được tính là nhắm mắt."""
    tracker = PERCLOSTracker(
        window_seconds=10,
        min_coverage_ratio=0.8,
    )

    # 0–8 giây có khuôn mặt và mắt mở.
    for i in range(81):
        tracker.update(i / 10, 0.30)

    # 8,1–10 giây không phát hiện khuôn mặt.
    for i in range(81, 101):
        result = tracker.update(i / 10, None)

    # Đúng 80% độ bao phủ nên kết quả được xem là đủ dữ liệu.
    assert result.is_ready
    assert result.perclos == pytest.approx(0.0)
    assert result.closed_seconds == pytest.approx(0.0)
    assert result.valid_seconds == pytest.approx(8.0)
    assert result.coverage_ratio == pytest.approx(0.8)
    assert result.eye_closed is None



def test_perclos_camera_gap():
    """T006: Khoảng gián đoạn dài không được cộng thời gian."""
    tracker = PERCLOSTracker(
        window_seconds=10,
        min_coverage_ratio=0.8,
    )

    for i in range(41):
        tracker.update(i / 10, 0.05)

    # Camera mất dữ liệu từ giây 4 đến giây 7.
    tracker.update(7.0, 0.05)

    for i in range(71, 101):
        result = tracker.update(i / 10, 0.05)

    assert not result.is_ready
    assert result.perclos is None
    assert result.valid_seconds == pytest.approx(7.0)


def test_perclos_invalid_timestamp():
    """T007: Timestamp phải tăng nghiêm ngặt."""
    tracker = PERCLOSTracker()
    tracker.update(1.0, 0.30)

    with pytest.raises(ValueError, match="strictly increasing"):
        tracker.update(1.0, 0.30)



def test_perclos_sliding_window():
    """T008: Cửa sổ trượt loại bỏ hoàn toàn dữ liệu nhắm mắt cũ."""
    tracker = PERCLOSTracker(
        window_seconds=10,
        min_coverage_ratio=0.8,
    )

    # 0–10 giây: nhắm mắt.
    feed(tracker, 0, 10, [(0, 10.1)])

    # 10,1–21 giây: mở mắt.
    # Tại t=21, cửa sổ là [11, 21], không còn dữ liệu mắt nhắm.
    result = feed(tracker, 10.1, 21)

    assert result.is_ready
    assert result.perclos == pytest.approx(0.0)
    assert result.closed_seconds == pytest.approx(0.0)
    assert result.valid_seconds == pytest.approx(10.0)


def test_perclos_insufficient_coverage():
    """T009: Dữ liệu hợp lệ dưới 80% thì chưa được xuất PERCLOS."""
    tracker = PERCLOSTracker(
        window_seconds=10,
        min_coverage_ratio=0.8,
    )

    # 0–7 giây: mắt mở, có khuôn mặt.
    for i in range(71):
        tracker.update(i / 10, 0.30)

    # 7,1–10 giây: mất khuôn mặt.
    for i in range(71, 101):
        result = tracker.update(i / 10, None)

    assert not result.is_ready
    assert result.perclos is None
    assert result.valid_seconds == pytest.approx(7.0)
    assert result.coverage_ratio == pytest.approx(0.7)


def test_perclos_invalid_ear():
    """T010: EAR không hợp lệ phải phát sinh ValueError."""
    tracker = PERCLOSTracker()

    with pytest.raises(ValueError, match="ear must be finite"):
        tracker.update(0.0, float("nan"))


def test_perclos_reset():
    """T011: Reset phải xóa dữ liệu lịch sử."""
    tracker = PERCLOSTracker(
        window_seconds=10,
        min_coverage_ratio=0.8,
    )

    result = feed(tracker, 0, 10, [(0, 10.1)])
    assert result.is_ready
    assert result.perclos == pytest.approx(100.0)

    tracker.reset()

    result = tracker.update(0.0, 0.30)

    assert not result.is_ready
    assert result.perclos is None
    assert result.closed_seconds == pytest.approx(0.0)
    assert result.valid_seconds == pytest.approx(0.0)
    assert result.coverage_ratio == pytest.approx(0.0)
