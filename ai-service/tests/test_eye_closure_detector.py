
"""Unit Test phát hiện nhắm mắt kéo dài của DriveGuard IoT."""

import sys
from pathlib import Path

import pytest

sys.path.insert(
    0,
    str(Path(__file__).resolve().parents[1] / "src"),
)

from eye_closure_detector import (
    EyeClosureDetector,
    EyeClosureEvent,
    EyeClosureState,
)


def test_eye_open():
    """T001: Mắt mở không được phát cảnh báo."""
    detector = EyeClosureDetector()

    result = detector.update(0.0, 0.30)

    assert result.state == EyeClosureState.OPEN
    assert result.event is None
    assert not result.is_alert_active


def test_short_eye_closure():
    """T002: Nhắm mắt dưới 2 giây không cảnh báo."""
    detector = EyeClosureDetector()

    detector.update(0.0, 0.05)
    result = detector.update(1.5, 0.05)

    assert result.state == EyeClosureState.CLOSED
    assert result.event is None
    assert not result.is_alert_active


def test_prolonged_eye_closure():
    """T003: Nhắm mắt liên tục đủ 2 giây phát cảnh báo."""
    detector = EyeClosureDetector()

    for i in range(21):
        result = detector.update(i / 10, 0.05)

    assert result.state == EyeClosureState.PROLONGED_CLOSURE
    assert result.event == EyeClosureEvent.PROLONGED_CLOSURE
    assert result.is_alert_active
    assert result.closed_duration_seconds == pytest.approx(2.0)


def test_alert_only_once():
    """T004: Mỗi đợt nhắm mắt chỉ phát một sự kiện cảnh báo."""
    detector = EyeClosureDetector()
    events = []

    for i in range(41):
        result = detector.update(i / 10, 0.05)
        if result.event is not None:
            events.append(result.event)

    assert events == [EyeClosureEvent.PROLONGED_CLOSURE]


def test_recovered():
    """T005: Mắt mở lại sau cảnh báo phải phát RECOVERED."""
    detector = EyeClosureDetector()

    for i in range(21):
        detector.update(i / 10, 0.05)

    result = detector.update(2.1, 0.30)

    assert result.state == EyeClosureState.OPEN
    assert result.event == EyeClosureEvent.RECOVERED
    assert not result.is_alert_active


def test_missing_face():
    """T006: Mất khuôn mặt phải hủy đợt nhắm mắt."""
    detector = EyeClosureDetector()

    for i in range(11):
        detector.update(i / 10, 0.05)

    result = detector.update(1.1, None)

    assert result.state == EyeClosureState.UNKNOWN
    assert result.event is None
    assert not result.is_alert_active

    result = detector.update(1.2, 0.05)

    assert result.state == EyeClosureState.CLOSED
    assert result.closed_duration_seconds == pytest.approx(0.0)


def test_camera_gap():
    """T007: Gián đoạn camera quá 0,5 giây phải reset đợt nhắm mắt."""
    detector = EyeClosureDetector()

    for i in range(11):
        detector.update(i / 10, 0.05)

    result = detector.update(3.0, 0.05)

    assert result.state == EyeClosureState.CLOSED
    assert result.closed_duration_seconds == pytest.approx(0.0)
    assert result.event is None


def test_invalid_timestamp():
    """T008: Timestamp không tăng phải phát sinh ValueError."""
    detector = EyeClosureDetector()

    detector.update(1.0, 0.30)

    with pytest.raises(ValueError, match="strictly increasing"):
        detector.update(1.0, 0.30)


def test_invalid_ear():
    """T009: EAR NaN phải bị từ chối."""
    detector = EyeClosureDetector()

    with pytest.raises(ValueError, match="ear must be finite"):
        detector.update(0.0, float("nan"))


def test_reset():
    """T010: Reset phải xóa trạng thái cảnh báo cũ."""
    detector = EyeClosureDetector()

    for i in range(21):
        detector.update(i / 10, 0.05)

    detector.reset()
    result = detector.update(0.0, 0.30)

    assert result.state == EyeClosureState.OPEN
    assert result.event is None
    assert not result.is_alert_active


def test_new_closure_after_recovery():
    """T011: Đợt nhắm mắt mới được phép tạo cảnh báo mới."""
    detector = EyeClosureDetector()
    events = []

    for i in range(21):
        result = detector.update(i / 10, 0.05)
        if result.event is not None:
            events.append(result.event)

    result = detector.update(2.1, 0.30)
    if result.event is not None:
        events.append(result.event)

    for i in range(22, 43):
        result = detector.update(i / 10, 0.05)
        if result.event is not None:
            events.append(result.event)

    assert events == [
        EyeClosureEvent.PROLONGED_CLOSURE,
        EyeClosureEvent.RECOVERED,
        EyeClosureEvent.PROLONGED_CLOSURE,
    ]
