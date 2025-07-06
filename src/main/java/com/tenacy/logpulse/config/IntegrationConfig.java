package com.tenacy.logpulse.config;

import com.tenacy.logpulse.api.dto.LogEventDto;
import com.tenacy.logpulse.integration.filter.LogFilter;
import com.tenacy.logpulse.integration.router.LogRouter;
import com.tenacy.logpulse.integration.service.LogServiceActivator;
import com.tenacy.logpulse.integration.transformer.LogEnricher;
import com.tenacy.logpulse.service.LogAlertService;
import com.tenacy.logpulse.service.LogMetricsService;
import com.tenacy.logpulse.service.LogProducerService;
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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Configuration
@EnableIntegration
@IntegrationComponentScan(basePackages = "com.tenacy.logpulse.integration")
@RequiredArgsConstructor
public class IntegrationConfig {

    private final LogProducerService logProducerService;
    private final LogMetricsService logMetricsService;
    private final LogAlertService logAlertService;

    private final AtomicLong inputCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

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
    public LogServiceActivator logServiceActivator() {
        return new LogServiceActivator(logProducerService);
    }

    @Bean
    public IntegrationFlow logProcessingFlow() {
        return IntegrationFlow.from("logInputChannel")
                .<Object, Object>transform(payload -> {
                    long count = inputCount.incrementAndGet();
                    if (count % 10000 == 0) {
                        log.info("Input channel received: {} messages", count);
                    }

                    if (payload instanceof LogEventDto) {
                        return payload;
                    }
                    log.warn("Unexpected payload type: {}",
                            payload != null ? payload.getClass().getName() : "null");
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
                    errorCount.incrementAndGet();
                    if (message instanceof ErrorMessage errorMessage) {
                        Throwable payload = errorMessage.getPayload();
                        MessageHeaders headers = errorMessage.getHeaders();

                        log.error("Error in integration flow ({}): {} [headers: {}]",
                                errorCount.get(), payload.getMessage(), headers, payload);
                    } else {
                        log.error("Unknown error in integration flow ({}): {}",
                                errorCount.get(), message);
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
                            logMetricsService.recordLog(payload);
                        }
                    } catch (Exception e) {
                        log.error("Error recording metrics: {}", e.getMessage(), e);
                    }
                    return null;
                })
                .get();
    }

    @Bean
    public IntegrationFlow alertFlow() {
        return IntegrationFlow.from(processedLogChannel())
                .<LogEventDto>handle((payload, headers) -> {
                    try {
                        if (payload != null && "ERROR".equalsIgnoreCase(payload.getLogLevel())) {
                            logAlertService.checkLogForAlert(payload);
                        }
                    } catch (Exception e) {
                        log.error("Error checking alerts: {}", e.getMessage(), e);
                    }
                    return null;
                })
                .get();
    }
}