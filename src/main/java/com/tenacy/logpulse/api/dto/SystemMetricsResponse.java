package com.tenacy.logpulse.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMetricsResponse {
    private LocalDateTime timestamp;

    // 현재 지표
    private long currentProcessedRate;  // 분당 처리 로그 수 (현재)
    private double currentErrorRate;    // 현재 오류율 (%)
    private int currentResponseTime;    // 현재 평균 응답 시간 (ms)

    // 평균 지표 (5분 평균)
    private long averageProcessedRate;  // 분당 처리 로그 수 (평균)
    private double averageErrorRate;    // 평균 오류율 (%)
    private int averageResponseTime;    // 평균 응답 시간 (ms)
}