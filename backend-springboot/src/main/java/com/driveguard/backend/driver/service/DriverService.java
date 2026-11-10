
package com.driveguard.backend.driver.service;

import com.driveguard.backend.driver.dto.request.DriverCreateRequest;
import com.driveguard.backend.driver.dto.response.DriverResponse;

import java.util.List;

public interface DriverService {

    /**
     * Tạo tài khoản User có role DRIVER
     * và hồ sơ Driver tương ứng.
     *
     * Hai thao tác lưu dữ liệu phải được thực hiện
     * trong cùng một transaction.
     *
     * @param request Dữ liệu tài xế cần tạo.
     * @return Thông tin tài xế vừa được tạo.
     */
    DriverResponse createDriver(
            DriverCreateRequest request
    );

    /**
     * Lấy thông tin tài xế theo ID hồ sơ.
     *
     * @param driverId ID của DriverEntity,
     *                 không phải ID của UserEntity.
     * @return Thông tin tài xế.
     */
    DriverResponse getDriverById(
            Long driverId
    );

    /**
     * Lấy danh sách tất cả tài xế.
     *
     * Phiên bản ban đầu dùng List.
     * Khi dữ liệu lớn, nên chuyển sang
     * phân trang bằng Pageable.
     *
     * @return Danh sách tài xế.
     */
    List<DriverResponse> getAllDrivers();
}
