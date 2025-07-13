package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.service.IntegrationLogService;
import com.tenacy.logpulse.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final LogService logService;
    private final IntegrationLogService integrationLogService;
    private static final AtomicInteger counter = new AtomicInteger(0);

    /**
     * 단일 ERROR 로그를 생성하는 API
     * RealTimeErrorMonitorService를 테스트하기 위해 사용
     */
    @GetMapping("/error-log")
    public ResponseEntity<String> createSingleErrorLog(
            @RequestParam(defaultValue = "test-error") String source,
            @RequestParam(defaultValue = "") String content) {

        try {
            int count = counter.incrementAndGet();
            String logContent = content.isEmpty()
                    ? "Test error log #" + count + " at " + LocalDateTime.now()
                    : content;

            LogEntryRequest request = LogEntryRequest.builder()
                    .source(source)
                    .content(logContent)
                    .logLevel("ERROR")
                    .build();

            LogEntryResponse response = logService.createLog(request);

            return ResponseEntity.ok("ERROR 로그 생성 완료: ID=" + response.getId() +
                    ", 내용=" + response.getContent() +
                    "\n\n이 API를 여러 번 호출하여 RealTimeErrorMonitorService 테스트 가능");

        } catch (Exception e) {
            log.error("Error log creation failed", e);
            return ResponseEntity.status(500)
                    .body("ERROR 로그 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 여러 개의 ERROR 로그를 빠르게 생성하는 API
     * RealTimeErrorMonitorService 임계값 초과 테스트
     */
    @GetMapping("/error-logs/batch")
    public ResponseEntity<String> createMultipleErrorLogs(
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "test-error-batch") String source) {

        StringBuilder result = new StringBuilder();
        result.append("ERROR 로그 배치 생성 결과:\n\n");

        try {
            List<LogEntryResponse> responses = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                LogEntryRequest request = LogEntryRequest.builder()
                        .source(source)
                        .content("Batch error log #" + (i + 1) + " at " + LocalDateTime.now())
                        .logLevel("ERROR")
                        .build();

                LogEntryResponse response = logService.createLog(request);
                responses.add(response);

                // 로그 처리를 위한 약간의 지연
                Thread.sleep(50);
            }

            result.append("총 ").append(count).append("개의 ERROR 로그 생성 완료\n");
            result.append("첫 번째 로그 ID: ").append(responses.get(0).getId()).append("\n");
            result.append("마지막 로그 ID: ").append(responses.get(responses.size() - 1).getId()).append("\n\n");

            result.append("RealTimeErrorMonitorService가 임계값을 초과하는 로그 수를 감지했다면 ");
            result.append("시스템 로그와 이메일을 확인하세요.");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Batch error log creation failed", e);
            return ResponseEntity.status(500)
                    .body("ERROR 로그 배치 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 다양한 로그 레벨을 생성하는 API
     * RealTimeErrorMonitorService가 ERROR 로그만 카운트하는지 테스트
     */
    @GetMapping("/mixed-logs")
    public ResponseEntity<String> createMixedLogs(
            @RequestParam(defaultValue = "5") int errorCount,
            @RequestParam(defaultValue = "10") int infoCount,
            @RequestParam(defaultValue = "test-mixed") String source) {

        try {
            StringBuilder result = new StringBuilder();
            result.append("다양한 로그 레벨 생성 결과:\n\n");

            // INFO 로그 생성
            for (int i = 0; i < infoCount; i++) {
                LogEntryRequest request = LogEntryRequest.builder()
                        .source(source)
                        .content("INFO log #" + (i + 1))
                        .logLevel("INFO")
                        .build();

                logService.createLog(request);
                Thread.sleep(20);
            }

            result.append("INFO 로그 ").append(infoCount).append("개 생성 완료\n");

            // ERROR 로그 생성
            for (int i = 0; i < errorCount; i++) {
                LogEntryRequest request = LogEntryRequest.builder()
                        .source(source)
                        .content("ERROR log #" + (i + 1))
                        .logLevel("ERROR")
                        .build();

                logService.createLog(request);
                Thread.sleep(20);
            }

            result.append("ERROR 로그 ").append(errorCount).append("개 생성 완료\n\n");

            result.append("RealTimeErrorMonitorService가 ERROR 로그만 카운트한다면, ");
            result.append("INFO 로그는 무시하고 ERROR 로그 ").append(errorCount)
                    .append("개만 카운트해야 합니다.");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Mixed log creation failed", e);
            return ResponseEntity.status(500)
                    .body("다양한 로그 레벨 생성 실패: " + e.getMessage());
        }
    }

    /**
     * IntegrationLogService를 통해 ERROR 로그를 생성하는 API
     * 직접 LogEventDto를 전송하여 RealTimeErrorMonitorService 테스트
     */
    @GetMapping("/integration/error-log")
    public ResponseEntity<String> createSingleIntegrationErrorLog(
            @RequestParam(defaultValue = "integration-test") String source,
            @RequestParam(defaultValue = "") String content) {

        try {
            int count = counter.incrementAndGet();
            String logContent = content.isEmpty()
                    ? "Integration test error log #" + count + " at " + LocalDateTime.now()
                    : content;

            LogEventDto eventDto = LogEventDto.builder()
                    .source(source)
                    .content(logContent)
                    .logLevel("ERROR")
                    .timestamp(LocalDateTime.now())
                    .build();

            integrationLogService.processLog(eventDto);

            return ResponseEntity.ok("Integration ERROR 로그 생성 완료: " +
                    "소스=" + source +
                    ", 내용=" + logContent +
                    "\n\n이 API를 여러 번 호출하여 통합 파이프라인을 통한 RealTimeErrorMonitorService 테스트 가능");

        } catch (Exception e) {
            log.error("Integration error log creation failed", e);
            return ResponseEntity.status(500)
                    .body("Integration ERROR 로그 생성 실패: " + e.getMessage());
        }
    }

    /**
     * IntegrationLogService를 통해 여러 개의 ERROR 로그를 빠르게 생성하는 API
     */
    @GetMapping("/integration/error-logs/batch")
    public ResponseEntity<String> createMultipleIntegrationErrorLogs(
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "integration-batch-test") String source) {

        StringBuilder result = new StringBuilder();
        result.append("Integration ERROR 로그 배치 생성 결과:\n\n");

        try {
            for (int i = 0; i < count; i++) {
                LogEventDto eventDto = LogEventDto.builder()
                        .source(source)
                        .content("Integration batch error log #" + (i + 1) + " at " + LocalDateTime.now())
                        .logLevel("ERROR")
                        .timestamp(LocalDateTime.now())
                        .build();

                integrationLogService.processLog(eventDto);

                // 로그 처리를 위한 약간의 지연
                Thread.sleep(50);
            }

            result.append("총 ").append(count).append("개의 Integration ERROR 로그 생성 완료\n\n");
            result.append("RealTimeErrorMonitorService가 임계값을 초과하는 로그 수를 감지했다면 ");
            result.append("시스템 로그와 이메일을 확인하세요.");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Integration batch error log creation failed", e);
            return ResponseEntity.status(500)
                    .body("Integration ERROR 로그 배치 생성 실패: " + e.getMessage());
        }
    }

    /**
     * IntegrationLogService를 통해 다양한 로그 레벨을 생성하는 API
     */
    @GetMapping("/integration/mixed-logs")
    public ResponseEntity<String> createMixedIntegrationLogs(
            @RequestParam(defaultValue = "5") int errorCount,
            @RequestParam(defaultValue = "10") int infoCount,
            @RequestParam(defaultValue = "integration-mixed-test") String source) {

        try {
            StringBuilder result = new StringBuilder();
            result.append("Integration 다양한 로그 레벨 생성 결과:\n\n");

            // INFO 로그 생성
            for (int i = 0; i < infoCount; i++) {
                LogEventDto eventDto = LogEventDto.builder()
                        .source(source)
                        .content("Integration INFO log #" + (i + 1))
                        .logLevel("INFO")
                        .timestamp(LocalDateTime.now())
                        .build();

                integrationLogService.processLog(eventDto);
                Thread.sleep(20);
            }

            result.append("INFO 로그 ").append(infoCount).append("개 생성 완료\n");

            // ERROR 로그 생성
            for (int i = 0; i < errorCount; i++) {
                LogEventDto eventDto = LogEventDto.builder()
                        .source(source)
                        .content("Integration ERROR log #" + (i + 1))
                        .logLevel("ERROR")
                        .timestamp(LocalDateTime.now())
                        .build();

                integrationLogService.processLog(eventDto);
                Thread.sleep(20);
            }

            result.append("ERROR 로그 ").append(errorCount).append("개 생성 완료\n\n");

            result.append("RealTimeErrorMonitorService가 ERROR 로그만 카운트한다면, ");
            result.append("INFO 로그는 무시하고 ERROR 로그 ").append(errorCount)
                    .append("개만 카운트해야 합니다.");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Integration mixed log creation failed", e);
            return ResponseEntity.status(500)
                    .body("Integration 다양한 로그 레벨 생성 실패: " + e.getMessage());
        }
    }
}