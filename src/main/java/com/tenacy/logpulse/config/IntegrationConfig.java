package com.tenacy.logpulse.config;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.integration.filter.LogFilter;
import com.tenacy.logpulse.integration.router.LogRouter;
import com.tenacy.logpulse.integration.service.LogServiceActivator;
import com.tenacy.logpulse.integration.transformer.LogEnricher;
import com.tenacy.logpulse.pattern.LogPatternDetector;
import com.tenacy.logpulse.service.LogAlertService;
import com.tenacy.logpulse.service.LogMetricsService;
import com.tenacy.logpulse.service.LogProducerService;
import com.tenacy.logpulse.service.RealTimeErrorMonitorService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
@EnableIntegration
@IntegrationComponentScan(basePackages = "com.tenacy.logpulse.integration")
@RequiredArgsConstructor
public class IntegrationConfig {

    private final LogProducerService logProducerService;
    private final LogMetricsService logMetricsService;
    private final RealTimeErrorMonitorService errorMonitorService;
    private final LogPatternDetector patternDetector;
    private final MeterRegistry meterRegistry;
    private final Counter inputChannelCounter;
    private final Counter channelErrorCounter;

    @Bean
    public Executor integrationExecutor() {
        return Executors.newFixedThreadPool(20);
    }

    @Bean
    public MessageChannel logInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel errorLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel warnLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel infoLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel debugLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel enrichedLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel filteredLogChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel processedLogChannel() {
        return new PublishSubscribeChannel(integrationExecutor());
    }

    @Bean
    public MessageChannel errorChannel() {
        return MessageChannels.publishSubscribe(integrationExecutor()).getObject();
    }

    @Bean
    public LogRouter logRouter() {
        return new LogRouter(errorLogChannel(), warnLogChannel(), infoLogChannel(), debugLogChannel());
    }

    @Bean
    public LogEnricher logEnricher() {
        return new LogEnricher();
    }

    @Bean
    public LogFilter logFilter() {
        return new LogFilter();
    }

    @Bean
    public IntegrationFlow logProcessingFlow() {
        return IntegrationFlow.from("logInputChannel")
                .<Object, Object>transform(payload -> {
                    // 입력 채널 카운터 증가
                    inputChannelCounter.increment();

                    // 페이로드 유형별 카운터
                    String payloadType = payload != null ? payload.getClass().getSimpleName() : "null";
                    meterRegistry.counter("logpulse.channel.payload.type", "type", payloadType).increment();

                    if (payload instanceof LogEventDto) {
                        return payload;
                    }
                    log.warn("Unexpected payload type: {}", payloadType);
                    if (payload == null) {
                        // null 페이로드는 오류로 처리하지 않고 건너뜀
                        return new LogEventDto();
                    }
                    return payload;
                })
                .route(logRouter())
                .get();
    }

    @Bean
    public IntegrationFlow errorHandlingFlow() {
        return IntegrationFlow.from(errorChannel())
                .handle(message -> {
                    // 오류 카운터 증가
                    channelErrorCounter.increment();

                    if (message instanceof ErrorMessage errorMessage) {
                        Throwable payload = errorMessage.getPayload();
                        MessageHeaders headers = errorMessage.getHeaders();

                        // 오류 유형별 카운터
                        String errorType = payload.getClass().getSimpleName();
                        meterRegistry.counter("logpulse.channel.error.type", "type", errorType).increment();

                        log.error("Error in integration flow: {} [headers: {}]",
                                payload.getMessage(), headers, payload);
                    } else {
                        log.error("Unknown error in integration flow: {}", message);
                        meterRegistry.counter("logpulse.channel.error.type", "type", "unknown").increment();
                    }
                })
                .get();
    }

    @Bean
    public IntegrationFlow metricsFlow() {
        return IntegrationFlow.from(processedLogChannel())
                .<LogEventDto>handle((payload, headers) -> {
                    try {
                        if (payload != null) {
                            Timer.Sample sample = Timer.start(meterRegistry);
                            logMetricsService.recordLog(payload);
                            sample.stop(meterRegistry.timer("logpulse.metrics.processing.time"));
                        }
                    } catch (Exception e) {
                        log.error("Error recording metrics: {}", e.getMessage(), e);
                        meterRegistry.counter("logpulse.metrics.errors").increment();
                    }
                    return null;
                })
                .get();
    }

    @Bean
    public IntegrationFlow errorMonitorFlow() {
        return IntegrationFlow.from(processedLogChannel())
                .<LogEventDto>handle((payload, headers) -> {
                    try {
                        if (payload != null && "ERROR".equalsIgnoreCase(payload.getLogLevel())) {
                            Timer.Sample sample = Timer.start(meterRegistry);
                            errorMonitorService.monitorLog(payload);
                            sample.stop(meterRegistry.timer("logpulse.errormonitor.processing.time"));

                            // 오류 모니터링 카운터
                            meterRegistry.counter("logpulse.errormonitor.processed").increment();
                        }
                    } catch (Exception e) {
                        log.error("Error monitoring errors: {}", e.getMessage(), e);
                        meterRegistry.counter("logpulse.errormonitor.errors").increment();
                    }
                    return null;
                })
                .get();
    }

    /**
     * 주기적으로 통계 로깅 (1분마다)
     */
    @Scheduled(fixedRate = 60000)
    public void logStats() {
        double input = inputChannelCounter.count();
        double errors = channelErrorCounter.count();

        if (input > 0) {
            double errorRate = (errors / input) * 100.0;
            log.info("Channel stats - Input messages: {}, Errors: {} ({:.2f}%)",
                    (long)input, (long)errors, errorRate);
        }
    }
}