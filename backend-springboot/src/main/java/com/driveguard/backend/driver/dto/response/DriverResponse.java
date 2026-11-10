
package com.driveguard.backend.driver.dto.response;

import com.driveguard.backend.driver.entity.DriverEntity;

public record DriverResponse(

        Long id,
        Long userId,
        String username,
        String fullName,
        String email,
        String role,
        Boolean active,
        String driverCode,
        String phone,
        String licenseNumber

) {

    public static DriverResponse fromEntity(
            DriverEntity driver
    ) {

        if (driver == null || driver.getUser() == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu hồ sơ tài xế không hợp lệ"
            );
        }

        var user = driver.getUser();

        return new DriverResponse(
                driver.getId(),
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getName(),
                user.getActive(),
                driver.getDriverCode(),
                driver.getPhone(),
                driver.getLicenseNumber()
        );
    }
}
