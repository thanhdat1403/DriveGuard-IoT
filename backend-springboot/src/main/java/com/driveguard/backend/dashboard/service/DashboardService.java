
package com.driveguard.backend.dashboard.service;

import com.driveguard.backend.dashboard.dto.response.DashboardSummaryResponse;

/**
 * Service interface quản lý nghiệp vụ Dashboard.
 *
 * Chịu trách nhiệm cung cấp dữ liệu tổng hợp
 * phục vụ giao diện giám sát DriveGuard IoT.
 */
public interface DashboardService {

    /**
     * Lấy thống kê tổng quan của một thiết bị.
     *
     * @param deviceId ID thiết bị cần thống kê
     * @return dữ liệu tổng hợp Dashboard
     */
    DashboardSummaryResponse getSummary(Long deviceId);
    /* DashboardSummaryResponse : DTO chứa dữ liệu trả về cho Dashboard
    getSummary() : Phương thức lấy thống kê tổng quan
    Long deviceId : ID thiết bị cần giám sát
    * */

}
