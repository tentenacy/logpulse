package com.tenacy.logpulse.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogCountResponse {
    private Long error;
    private Long warn;
    private Long info;
    private Long debug;
    private Long total;
    private Double errorRate;
}