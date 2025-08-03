package com.tenacy.logpulse.batch;

import com.tenacy.logpulse.domain.LogEntry;
import com.tenacy.logpulse.service.LogCompressionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

@Slf4j
public class LogArchiveProcessor implements ItemProcessor<LogEntry, LogEntry> {

    @Autowired
    private LogCompressionService compressionService;

    @Override
    public LogEntry process(LogEntry item) {
        log.debug("로그 항목 아카이빙: {}", item.getId());

        // 이미 압축된 로그면 그대로 반환
        if (Boolean.TRUE.equals(item.getCompressed())) {
            return item;
        }

        // 압축되지 않은 로그이고 압축할 만한 크기인 경우 압축 수행
        String content = item.getContent();
        if (content != null && !content.isEmpty() && compressionService.shouldCompress(content)) {
            // 원본 크기 계산
            int originalSize = content.getBytes(StandardCharsets.UTF_8).length;

            // 압축 수행
            String compressedContent = compressionService.compressContent(content);
            int compressedSize = compressedContent.getBytes(StandardCharsets.UTF_8).length;

            // 압축이 효과적인 경우에만 압축된 콘텐츠 사용
            if (compressedSize < originalSize) {
                item.setContent(compressedContent);
                item.setCompressed(true);
                item.setOriginalSize(originalSize);
                item.setCompressedSize(compressedSize);

                log.debug("아카이브용 로그 내용 압축 - ID: {}, 원본 크기: {}, 압축 크기: {}, 비율: {}%",
                        item.getId(), originalSize, compressedSize,
                        Math.round((1 - (double)compressedSize/originalSize) * 100));
            } else {
                // 압축이 효과적이지 않은 경우
                item.setCompressed(false);
                item.setOriginalSize(originalSize);
                item.setCompressedSize(originalSize);
                log.debug("로그 ID: {}에 대한 압축 건너뜀 - 비효율적인 압축", item.getId());
            }
        } else {
            // 압축할 만한 크기가 아닌 경우
            if (content != null) {
                int size = content.getBytes(StandardCharsets.UTF_8).length;
                item.setOriginalSize(size);
                item.setCompressedSize(size);
            }
            item.setCompressed(false);
            log.debug("로그 ID: {}에 대한 압축 건너뜀 - 콘텐츠가 너무 작음", item.getId());
        }

        return item;
    }
}