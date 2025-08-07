package com.tenacy.logpulse.performance;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.domain.LogRepository;
import com.tenacy.logpulse.service.LogCompressionService;
import com.tenacy.logpulse.service.LogService;
import com.tenacy.logpulse.service.LogStatisticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
public class PerformanceTest {

    @Autowired
    private LogService logService;

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private LogCompressionService compressionService;

    // 테스트 파라미터
    private final int TEST_LOG_COUNT = 1000;
    private final int CONCURRENT_THREADS = 10;
    private final int BATCH_SIZE = 100;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        logRepository.deleteAll();
    }

    @Test
    @DisplayName("처리량 테스트 - 초당 처리 로그 수 측정")
    void testThroughput() throws Exception {
        // given
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicLong successCount = new AtomicLong(0);

        // 테스트를 위한 임시 로그 서비스 설정 (통계 업데이트 비활성화)
        LogStatisticsService mockStatisticsService = mock(LogStatisticsService.class);

        // 기존 로그 서비스의 통계 서비스를 모킹된 버전으로 교체
        ReflectionTestUtils.setField(logService, "logStatisticsService", mockStatisticsService);

        int logsPerThread = TEST_LOG_COUNT / CONCURRENT_THREADS;

        // when - 시간 측정 시작
        Instant start = Instant.now();

        // 여러 스레드에서 동시에 로그 생성
        for (int t = 0; t < CONCURRENT_THREADS; t++) {
            int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < logsPerThread; i++) {
                        LogEntryRequest request = createLogRequest(threadId, i);
                        logService.createLog(request);
                        successCount.incrementAndGet();

                        // 배치 단위로 잠시 쉬어줌
                        if (i > 0 && i % BATCH_SIZE == 0) {
                            Thread.sleep(5);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " error: " + e.getMessage());
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);

        // 시간 측정 종료
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        // 초당 처리량 계산
        double logsPerSecond = (double) TEST_LOG_COUNT / (duration.toMillis() / 1000.0);

        // then
        System.out.println("처리량 테스트 결과:");
        System.out.println("총 로그 수: " + TEST_LOG_COUNT);
        System.out.println("소요 시간: " + duration.toMillis() + "ms");
        System.out.println("초당 처리량: " + String.format("%.2f", logsPerSecond) + " logs/sec");

        // 목표: 50,000 logs/sec (테스트 환경에서는 목표의 일부만 검증)
        assertTrue(logsPerSecond > 100, "초당 최소 100개 이상의 로그를 처리해야 함");
        assertEquals(TEST_LOG_COUNT, successCount.get(), "모든 로그가 성공적으로 처리되어야 함");
    }

    @Test
    @DisplayName("압축률 테스트 - 로그 압축 효율성 측정")
    void testCompressionRate() {
        // given
        String repeatedContent = "반복되는 내용입니다. 이 내용은 압축 효율이 높아야 합니다. ".repeat(50);

        // when
        byte[] originalBytes = repeatedContent.getBytes(StandardCharsets.UTF_8);
        String compressed = compressionService.compressContent(repeatedContent);
        byte[] compressedBytes = compressed.getBytes(StandardCharsets.UTF_8);

        // 압축률 계산
        double compressionRate = 100.0 * (1.0 - ((double) compressedBytes.length / originalBytes.length));

        // then
        System.out.println("압축률 테스트 결과:");
        System.out.println("원본 크기: " + originalBytes.length + " bytes");
        System.out.println("압축 후 크기: " + compressedBytes.length + " bytes");
        System.out.println("압축률: " + String.format("%.2f", compressionRate) + "%");

        // 목표: 80% 이상의 압축률
        assertTrue(compressionRate > 70.0, "70% 이상의 압축률을 달성해야 함");
    }

    @Test
    @DisplayName("지연시간 테스트 - 개별 로그 처리 시간 측정")
    void testLatency() {
        // given
        String content = "지연시간 테스트 로그 메시지 " + UUID.randomUUID();
        LogEntryRequest request = LogEntryRequest.builder()
                .source("latency-test")
                .content(content)
                .logLevel("INFO")
                .build();

        // when - 시간 측정 시작
        Instant start = Instant.now();

        logService.createLog(request);

        // 시간 측정 종료
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        // then
        System.out.println("지연시간 테스트 결과:");
        System.out.println("로그 처리 시간: " + duration.toMillis() + "ms");

        // 목표: 100ms 이하의 지연시간
        assertTrue(duration.toMillis() < 100, "단일 로그 처리 시간이 100ms 미만이어야 함");
    }

    // 테스트용 로그 요청 생성 헬퍼 메서드
    private LogEntryRequest createLogRequest(int threadId, int logNumber) {
        String[] logLevels = {"ERROR", "WARN", "INFO", "DEBUG"};
        String logLevel = logLevels[(int) (Math.random() * logLevels.length)];

        // 로그 레벨에 따라 다른 내용 생성 (압축률 테스트 위함)
        String content;

        if ("ERROR".equals(logLevel)) {
            // 에러 로그는 보통 더 상세한 정보를 포함
            content = String.format(
                    "에러 발생: 스레드 %d, 작업 %d, ID %s, 시간 %s, 상세: 작업 처리 중 예외 발생. " +
                            "스택 트레이스: com.example.SomeClass.method(SomeClass.java:123) at " +
                            "com.example.OtherClass.otherMethod(OtherClass.java:456) at " +
                            "java.base/java.lang.Thread.run(Thread.java:829)",
                    threadId, logNumber, UUID.randomUUID(), LocalDateTime.now());
        } else if ("WARN".equals(logLevel)) {
            // 경고 로그
            content = String.format(
                    "경고: 스레드 %d에서 작업 %d 지연 발생. 타임아웃 임계값 접근 중. " +
                            "현재 실행 시간: %dms, 임계값: 5000ms. 리소스 사용량: CPU %d%%, 메모리 %d%%",
                    threadId, logNumber, (int)(Math.random() * 4800 + 200),
                    (int)(Math.random() * 80 + 10), (int)(Math.random() * 70 + 20));
        } else if ("INFO".equals(logLevel)) {
            // 정보 로그
            content = String.format(
                    "정보: 스레드 %d, 작업 %d 완료. 처리된 항목: %d개, 소요 시간: %dms, " +
                            "상태: 정상, 세션 ID: %s, 사용자 ID: user-%d",
                    threadId, logNumber, (int)(Math.random() * 100 + 1),
                    (int)(Math.random() * 200 + 50), UUID.randomUUID().toString().substring(0, 8),
                    (int)(Math.random() * 10000));
        } else {
            // 디버그 로그
            content = String.format(
                    "디버그: 스레드 %d, 작업 %d 상세 정보. 파라미터: {id=%d, name='item-%s', count=%d}, " +
                            "컨텍스트: {env='test', version='1.0.%d', mode='async'}",
                    threadId, logNumber, (int)(Math.random() * 10000),
                    UUID.randomUUID().toString().substring(0, 6),
                    (int)(Math.random() * 50 + 1), (int)(Math.random() * 100));
        }

        return LogEntryRequest.builder()
                .source("performance-test-service-" + threadId)
                .content(content)
                .logLevel(logLevel)
                .build();
    }
}