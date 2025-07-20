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
public class SystemStatusResponse {
    private String uptime;
    private Double memoryUsage;
    private Long processedRate;
    private Double errorRate;
    private Integer avgResponseTime;
    private String status;
    private LocalDateTime timestamp;
}