
package com.driveguard.backend.device.service;

import java.time.LocalDateTime;

public interface DeviceService {

    /**
     * Cap nhat thiet bi ONLINE khi nhan duoc
     * Heartbeat hoac Telemetry hop le.
     */
    void markDeviceOnline(String deviceCode);

    /**
     * Tim va chuyen cac thiet bi ONLINE
     * da qua han Heartbeat sang OFFLINE.
     *
     * @param cutoffTime Moc thoi gian UTC.
     * @return So thiet bi duoc chuyen OFFLINE.
     */
    int markExpiredDevicesOffline(LocalDateTime cutoffTime);
}
