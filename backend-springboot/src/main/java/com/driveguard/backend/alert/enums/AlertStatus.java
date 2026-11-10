
package com.driveguard.backend.alert.enums;

/**
 * Trạng thái xử lý cảnh báo.
 *
 * NEW: Cảnh báo mới.
 * ACKNOWLEDGED: Đã được xác nhận.
 * RESOLVED: Đã được giải quyết.
 * FALSE_ALARM: Cảnh báo được xác định là nhầm.
 */
public enum AlertStatus {

    NEW,
    ACKNOWLEDGED,
    RESOLVED,
    FALSE_ALARM
}
