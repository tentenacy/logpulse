package com.tenacy.logpulse.elasticsearch.document;

import com.tenacy.logpulse.domain.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.InnerField;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(indexName = "logs")
@Setting(settingPath = "elasticsearch/settings.json")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String source;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "standard"),
            otherFields = {
                    @InnerField(
                            suffix = "ngram",
                            type = FieldType.Text,
                            analyzer = "path_analyzer"
                    )
            }
    )
    private String content;

    @Field(type = FieldType.Keyword)
    private String logLevel;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime timestamp;

    public static LogDocument of(LogEntry logEntry) {
        return LogDocument.builder()
                .id(logEntry.getId() != null ? logEntry.getId().toString() : UUID.randomUUID().toString())
                .source(logEntry.getSource())
                .content(logEntry.getContent())
                .logLevel(logEntry.getLogLevel())
                .timestamp(logEntry.getCreatedAt())
                .build();
    }
}