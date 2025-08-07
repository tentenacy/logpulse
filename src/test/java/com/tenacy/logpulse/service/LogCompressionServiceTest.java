package com.tenacy.logpulse.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
public class LogCompressionServiceTest {

    @Spy
    @InjectMocks
    private LogCompressionService compressionService;

    @BeforeEach
    void setUp() {
        // 압축 설정 주입
        ReflectionTestUtils.setField(compressionService, "compressionEnabled", true);
        ReflectionTestUtils.setField(compressionService, "minCompressionSize", 100);
    }

    @Test
    @DisplayName("압축 대상 결정 - 길이에 따른 압축 대상 판별")
    void shouldCompress_ShouldReturnTrueForLargeContent() {
        // given
        String shortContent = "짧은 내용";
        String longContent = "긴 내용입니다.".repeat(20); // 최소 압축 크기보다 큰 내용

        // when
        boolean shouldCompressShort = compressionService.shouldCompress(shortContent);
        boolean shouldCompressLong = compressionService.shouldCompress(longContent);

        // then
        assertFalse(shouldCompressShort, "짧은 내용은 압축 대상이 아니어야 함");
        assertTrue(shouldCompressLong, "긴 내용은 압축 대상이어야 함");
    }

    @Test
    @DisplayName("압축 기능 - 내용 압축 및 압축률 확인")
    void compressContent_ShouldReduceSize() {
        // given
        String repeatedContent = "반복되는 내용입니다. ".repeat(100); // 반복 패턴이 많은 내용

        // when
        String compressed = compressionService.compressContent(repeatedContent);

        // then
        assertNotNull(compressed);
        assertNotEquals(repeatedContent, compressed);

        // Base64 디코딩 가능 여부 확인 (압축 결과가 Base64 인코딩되었는지)
        assertDoesNotThrow(() -> Base64.getDecoder().decode(compressed));

        // 압축률 계산 및 확인
        double ratio = 1.0 - (double) compressed.getBytes(StandardCharsets.UTF_8).length /
                repeatedContent.getBytes(StandardCharsets.UTF_8).length;

        assertTrue(ratio > 0.5, "50% 이상의 압축률을 달성해야 함");
    }

    @Test
    @DisplayName("압축 해제 - 압축된 내용 원복 확인")
    void decompressContent_ShouldRestoreOriginalContent() {
        // given
        String original = "원본 메시지입니다. 이 내용은 압축 후 다시 원복되어야 합니다.".repeat(10);

        // when
        String compressed = compressionService.compressContent(original);
        String decompressed = compressionService.decompressContent(compressed);

        // then
        assertNotNull(decompressed);
        assertEquals(original, decompressed, "압축 해제 후 원본과 동일해야 함");
    }

    @Test
    @DisplayName("압축 비활성화 - 압축 기능 비활성화 시 동작 확인")
    void shouldNotCompressWhenDisabled() {
        // given
        ReflectionTestUtils.setField(compressionService, "compressionEnabled", false);
        String longContent = "압축 대상이 될 만한 긴 내용입니다.".repeat(20);

        // when
        boolean shouldCompress = compressionService.shouldCompress(longContent);

        // then
        assertFalse(shouldCompress, "압축 기능이 비활성화되면 압축하지 않아야 함");
    }
}