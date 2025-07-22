package com.tenacy.logpulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogCompressionService {

    @Value("${logpulse.compression.enabled:true}")
    private boolean compressionEnabled;

    @Value("${logpulse.compression.min-size:1024}")
    private int minCompressionSize;

    public boolean shouldCompress(String content) {
        return compressionEnabled &&
                content != null &&
                content.length() > minCompressionSize &&
                !isCompressed(content);
    }

    public String compressContent(String content) {
        if (!shouldCompress(content)) {
            return content;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {

            gzipOut.write(content.getBytes(StandardCharsets.UTF_8));
            gzipOut.finish();

            byte[] compressedBytes = baos.toByteArray();
            String compressedContent = Base64.getEncoder().encodeToString(compressedBytes);

            double compressionRatio = (double) compressedBytes.length / content.getBytes(StandardCharsets.UTF_8).length;
            log.debug("Compressed log content: original size={}, compressed size={}, ratio={}",
                    content.length(), compressedBytes.length,
                    String.format("%.2f", compressionRatio));

            return compressedContent;

        } catch (IOException e) {
            log.warn("Failed to compress log content: {}", e.getMessage());
            return content;
        }
    }

    public String decompressContent(String compressedContent) {
        if (compressedContent == null || compressedContent.isEmpty() || !isCompressed(compressedContent)) {
            return compressedContent;
        }

        try {
            byte[] compressedBytes = Base64.getDecoder().decode(compressedContent);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes);
                 GZIPInputStream gzipIn = new GZIPInputStream(bais);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[1024];
                int len;
                while ((len = gzipIn.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                String decompressedContent = baos.toString(StandardCharsets.UTF_8.name());
                log.debug("Decompressed log content: compressed size={}, original size={}",
                        compressedBytes.length, decompressedContent.length());

                return decompressedContent;
            }

        } catch (IllegalArgumentException e) {
            log.debug("Content is not Base64 encoded, returning as is");
            return compressedContent;
        } catch (IOException e) {
            log.warn("Failed to decompress log content: {}", e.getMessage());
            return compressedContent;
        }
    }

    public boolean isCompressed(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(content);
            return decoded.length >= 2 &&
                    (decoded[0] == (byte) 0x1f) &&
                    (decoded[1] == (byte) 0x8b);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public double getCompressionRatio(String originalContent, String compressedContent) {
        if (originalContent == null || compressedContent == null ||
                originalContent.isEmpty() || compressedContent.isEmpty()) {
            return 1.0;
        }

        if (!isCompressed(compressedContent)) {
            return 1.0;
        }

        try {
            byte[] decodedContent = Base64.getDecoder().decode(compressedContent);
            return (double) decodedContent.length / originalContent.getBytes(StandardCharsets.UTF_8).length;
        } catch (IllegalArgumentException e) {
            return 1.0;
        }
    }
}