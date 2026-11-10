
package com.driveguard.backend.sensor.service.Impl;

import com.driveguard.backend.sensor.dto.result.RiskAssessmentResult;
import com.driveguard.backend.sensor.enums.RiskLevel;
import com.driveguard.backend.sensor.enums.ServerStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho RiskAssessmentServiceImpl.
 *
 * Mục tiêu:
 * - Kiểm tra quy tắc đánh giá dữ liệu cảm biến.
 * - Kiểm tra phát hiện trạng thái ngủ gật.
 * - Kiểm tra xử lý dữ liệu không hợp lệ.
 * - Đảm bảo ServerStatus và RiskLevel nhất quán.
 *
 * Không cần khởi động Spring Boot ApplicationContext.
 * Không cần kết nối MariaDB hoặc MQTT Broker.
 *
 * Đây là kiểm thử nghiệp vụ trên dữ liệu mô phỏng,
 * không phải kiểm chứng độ chính xác y khoa.
 */
class RiskAssessmentServiceImplTest {

    private RiskAssessmentServiceImpl riskAssessmentService;

    /**
     * Khởi tạo Service trước mỗi bài kiểm thử.
     */
    @BeforeEach
    void setUp() {
        riskAssessmentService = new RiskAssessmentServiceImpl();
    }

    /**
     * TC01: Dữ liệu bình thường.
     *
     * Kỳ vọng:
     * ServerStatus = NORMAL
     * RiskLevel = LOW
     */
    @Test
    void assess_shouldReturnNormal_whenDataIsNormal() {

        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("80"),
                new BigDecimal("98"),
                "NORMAL",
                new BigDecimal("0.95"),
                false
        );

        assertNotNull(result);
        assertEquals(ServerStatus.NORMAL, result.getServerStatus());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
    }

    /**
     * TC02: Thiết bị phát hiện ngủ gật qua isDrowsy.
     */
    @Test
    void assess_shouldReturnWarning_whenIsDrowsyTrue() {

        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("85"),
                new BigDecimal("97"),
                "DROWSY",
                new BigDecimal("0.95"),
                true
        );

        assertNotNull(result);
        assertEquals(ServerStatus.WARNING, result.getServerStatus());
        assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    /**
     * TC03: Edge AI trả về DROWSY.
     */
    @Test
    void assess_shouldReturnWarning_whenDrowsyWithoutPpg() {

        RiskAssessmentResult result = riskAssessmentService.assess(
                null,
                null,
                "DROWSY",
                new BigDecimal("0.95"),
                true
        );

        assertEquals(ServerStatus.WARNING, result.getServerStatus());
        assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    }

    /**
     * TC04: Thiếu dữ liệu nhịp tim.
     */
    @Test
    void assess_shouldReturnUnknown_whenHeartRateIsNull() {

        RiskAssessmentResult result = riskAssessmentService.assess(
                null,
                new BigDecimal("98"),
                "NORMAL",
                new BigDecimal("0.95"),
                false
        );

        assertEquals(ServerStatus.UNKNOWN, result.getServerStatus());
        assertNull(result.getRiskLevel());
    }

    /**
     * TC05: SpO2 vượt quá 100%.
     */
    @Test
    void assess_shouldReturnUnknown_whenSpo2IsInvalid() {

        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("80"),
                new BigDecimal("105"),
                "NORMAL",
                new BigDecimal("0.95"),
                false
        );

        assertEquals(ServerStatus.UNKNOWN, result.getServerStatus());
        assertNull(result.getRiskLevel());
    }

    /**
     * TC06: Độ tin cậy AI vượt quá 1.
     */
    @Test
    void assess_shouldReturnUnknown_whenConfidenceIsInvalid() {

        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("80"),
                new BigDecimal("98"),
                "NORMAL",
                new BigDecimal("1.2"),
                false
        );

        assertEquals(ServerStatus.UNKNOWN, result.getServerStatus());
        assertNull(result.getRiskLevel());
    }

    /**
     * TC07: Edge AI trả về trạng thái chưa được hỗ trợ.
     */
    @Test
    void assess_shouldReturnUnknown_whenEdgeAiStatusIsUnsupported() {

        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("80"),
                new BigDecimal("98"),
                "INVALID_STATUS",
                new BigDecimal("0.95"),
                false
        );

        assertEquals(ServerStatus.UNKNOWN, result.getServerStatus());
        assertNull(result.getRiskLevel());
    }

    /**
     * TC08: Edge AI trả về trạng thái viết thường.
     */
    @Test
    void assess_shouldAcceptLowercaseEdgeAiStatus() {

        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("80"),
                new BigDecimal("98"),
                "normal",
                new BigDecimal("0.95"),
                false
        );

        assertEquals(ServerStatus.NORMAL, result.getServerStatus());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
    }

    @Test
    void assess_shouldReturnUnknown_whenNormalStatusButDrowsyTrue() {

        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("85"),
                new BigDecimal("97"),
                "NORMAL",
                new BigDecimal("0.95"),
                true
        );

        assertEquals(ServerStatus.UNKNOWN, result.getServerStatus());
        assertNull(result.getRiskLevel());
    }

    @Test
    void assess_shouldReturnUnknown_whenDrowsyStatusButDrowsyFalse() {

        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("82"),
                new BigDecimal("98"),
                "DROWSY",
                new BigDecimal("0.95"),
                false
        );

        assertEquals(ServerStatus.UNKNOWN, result.getServerStatus());
        assertNull(result.getRiskLevel());
    }


    @Test
    void assess_shouldReturnUnknown_whenEdgeAiStatusIsNull() {
        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("80"),
                new BigDecimal("98"),
                null,
                new BigDecimal("0.95"),
                false
        );

        assertEquals(ServerStatus.UNKNOWN, result.getServerStatus());
        assertNull(result.getRiskLevel());
    }

    @Test
    void assess_shouldReturnUnknown_whenConfidenceIsNull() {
        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("80"),
                new BigDecimal("98"),
                "NORMAL",
                null,
                false
        );

        assertEquals(ServerStatus.UNKNOWN, result.getServerStatus());
        assertNull(result.getRiskLevel());
    }

    @Test
    void assess_shouldReturnUnknown_whenPpgValuesAreZero() {
        RiskAssessmentResult result = riskAssessmentService.assess(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "NORMAL",
                new BigDecimal("0.95"),
                false
        );

        assertEquals(ServerStatus.UNKNOWN, result.getServerStatus());
        assertNull(result.getRiskLevel());
    }

    @Test
    void assess_shouldReturnUnknown_whenConfidenceIsNegative() {
        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("80"),
                new BigDecimal("98"),
                "NORMAL",
                new BigDecimal("-0.1"),
                false
        );

        assertEquals(ServerStatus.UNKNOWN, result.getServerStatus());
        assertNull(result.getRiskLevel());
    }

    // Regression test: ghi nhận giới hạn của thuật toán demo.
    // Đây KHÔNG phải kỳ vọng đúng về an toàn y khoa.
    @Test
    void assess_currentImplementationDoesNotClassifyAbnormalVitals() {
        RiskAssessmentResult result = riskAssessmentService.assess(
                new BigDecimal("180"),
                new BigDecimal("85"),
                "NORMAL",
                new BigDecimal("0.95"),
                false
        );

        assertEquals(ServerStatus.NORMAL, result.getServerStatus());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
    }

}
