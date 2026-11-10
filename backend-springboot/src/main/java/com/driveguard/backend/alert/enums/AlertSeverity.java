
package com.driveguard.backend.alert.enums;

/**
 * Mức độ nghiêm trọng của cảnh báo.
 *
 * Phải đồng bộ với ENUM severity trong MariaDB.
 */
public enum AlertSeverity {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
