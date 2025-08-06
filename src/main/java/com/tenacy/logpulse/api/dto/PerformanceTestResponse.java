package com.tenacy.logpulse.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceTestResponse {
    private int totalLogs;
    private long elapsedTimeMs;
    private double logsPerSecond;
    private long originalSize;
    private long compressedSize;
    private double compressionRate;
}