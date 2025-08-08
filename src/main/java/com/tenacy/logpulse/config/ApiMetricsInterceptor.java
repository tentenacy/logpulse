package com.tenacy.logpulse.config;

import com.tenacy.logpulse.service.SystemMetricsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiMetricsInterceptor implements HandlerInterceptor {

    private final SystemMetricsService systemMetricsService;

    private static final String START_TIME_ATTRIBUTE = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 요청 시작 시간 기록
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        // 응답 시간 계산 및 기록
        try {
            Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
            if (startTime != null) {
                long endTime = System.currentTimeMillis();
                long responseTime = endTime - startTime;

                // API 패턴 필터링 (대시보드 API만 측정)
                String requestURI = request.getRequestURI();
                if (requestURI.startsWith("/api/") && !requestURI.contains("/health")) {
                    systemMetricsService.recordResponseTime(responseTime);

                    // 오류 응답 감지
                    int status = response.getStatus();
                    if (status >= 400) {
                        log.warn("API 오류 응답: {} {} - HTTP {}",
                                request.getMethod(), requestURI, status);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("응답 시간 측정 중 오류 발생", e);
        }
    }
}