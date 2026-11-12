package com.driveguard.backend.deviceevent.exception;

public class DeviceNotFoundException extends IllegalArgumentException {

    public DeviceNotFoundException(String deviceCode) {
        super("Device not found: " + deviceCode);
    }
}
