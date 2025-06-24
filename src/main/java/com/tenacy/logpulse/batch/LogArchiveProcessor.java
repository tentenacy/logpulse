package com.tenacy.logpulse.batch;

import com.tenacy.logpulse.domain.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

@Slf4j
public class LogArchiveProcessor implements ItemProcessor<LogEntry, LogEntry> {

    @Override
    public LogEntry process(LogEntry item) {
        log.debug("Archiving log entry: {}", item);
        return item;
    }
}