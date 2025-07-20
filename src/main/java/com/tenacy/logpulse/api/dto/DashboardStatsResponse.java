
package com.tenacy.logpulse.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private LogCountResponse logCounts;
    private Map<String, Object> hourlyStats;
    private Map<String, Object> sourceStats;
    private Map<String, Object> sourceLevelStats;
    private SystemStatusResponse systemStatus;
    private Map<String, Object> recentErrors;
    private Map<String, Object> errorTrends;
    private LocalDateTime timestamp;
}