
package com.driveguard.backend.sensor.service.Impl;

import com.driveguard.backend.sensor.dto.result.RiskAssessmentResult;
import com.driveguard.backend.sensor.enums.RiskLevel;
import com.driveguard.backend.sensor.enums.ServerStatus;
import com.driveguard.backend.sensor.service.RiskAssessmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentServiceImpl implements RiskAssessmentService {

    @Override
    public RiskAssessmentResult assess(
            BigDecimal heartRate,
            BigDecimal spo2,
            String edgeAiStatus,
            BigDecimal edgeAiConfidence,
            Boolean isDrowsy
    ) {

        // 1. Kiem tra du lieu Edge AI
        if (edgeAiStatus == null || edgeAiStatus.isBlank()
                || edgeAiConfidence == null
                || isDrowsy == null) {

            log.warn("[RISK] Thieu du lieu Edge AI.");
            return buildResult(ServerStatus.UNKNOWN, null);
        }

        String normalizedStatus = edgeAiStatus.trim()
                .toUpperCase(Locale.ROOT);

        if (!normalizedStatus.equals("NORMAL")
                && !normalizedStatus.equals("DROWSY")) {

            log.warn("[RISK] Edge AI status khong hop le: {}",
                    normalizedStatus);

            return buildResult(ServerStatus.UNKNOWN, null);
        }

        if (edgeAiConfidence.compareTo(BigDecimal.ZERO) < 0
                || edgeAiConfidence.compareTo(BigDecimal.ONE) > 0) {

            log.warn("[RISK] Edge AI confidence khong hop le.");
            return buildResult(ServerStatus.UNKNOWN, null);
        }

        // 2. Kiem tra tinh nhat quan cua Edge AI
        boolean statusDrowsy = normalizedStatus.equals("DROWSY");
        boolean flagDrowsy = Boolean.TRUE.equals(isDrowsy);

        if (statusDrowsy != flagDrowsy) {
            log.warn("[RISK] Edge AI status va isDrowsy mau thuan.");
            return buildResult(ServerStatus.UNKNOWN, null);
        }

        // 3. Xu ly ngu gat doc lap voi cam bien PPG
        if (statusDrowsy) {

            log.warn("[RISK] Phat hien ngu gat | Confidence: {}",
                    edgeAiConfidence);

            return buildResult(
                    ServerStatus.WARNING,
                    RiskLevel.MEDIUM
            );
        }

        // 4. Kiem tra du lieu PPG
        if (!isValidPpg(heartRate, spo2)) {

            log.warn("[RISK] Khong co du lieu PPG hop le.");
            return buildResult(ServerStatus.UNKNOWN, null);
        }

        // 5. Khong phat hien bat thuong theo quy tac demo
        return buildResult(
                ServerStatus.NORMAL,
                RiskLevel.LOW
        );
    }

    private boolean isValidPpg(
            BigDecimal heartRate,
            BigDecimal spo2
    ) {
        if (heartRate == null || spo2 == null) {
            return false;
        }

        if (heartRate.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        return spo2.compareTo(BigDecimal.ZERO) > 0
                && spo2.compareTo(new BigDecimal("100")) <= 0;
    }

    private RiskAssessmentResult buildResult(
            ServerStatus serverStatus,
            RiskLevel riskLevel
    ) {
        return RiskAssessmentResult.builder()
                .serverStatus(serverStatus)
                .riskLevel(riskLevel)
                .build();
    }
}
