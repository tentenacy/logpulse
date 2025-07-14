package com.tenacy.logpulse.api;

import com.tenacy.logpulse.api.dto.LogEntryRequest;
import com.tenacy.logpulse.api.dto.LogEntryResponse;
import com.tenacy.logpulse.pattern.LogPatternDetector;
import com.tenacy.logpulse.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {

    private final LogService logService;
    private final LogPatternDetector patternDetector;

    /**
     * 반복 에러 패턴 테스트
     */
    @GetMapping("/patterns/repeated-error")
    public ResponseEntity<String> testStatefulRepeatedErrorPattern(
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(defaultValue = "test") String source,
            @RequestParam(defaultValue = "Database connection failed") String errorMessage) {

        try {
            StringBuilder result = new StringBuilder();
            result.append("반복 에러 패턴 테스트 결과:\n\n");

            // 동일한 에러 메시지로 여러 개의 로그 생성
            for (int i = 0; i < count; i++) {
                LogEntryRequest request = LogEntryRequest.builder()
                        .source(source)
                        .content(errorMessage + " (instance " + (i + 1) + ")")
                        .logLevel("ERROR")
                        .build();

                LogEntryResponse response = logService.createLog(request);
                result.append("로그 생성: ID=").append(response.getId())
                        .append(", 내용=").append(response.getContent()).append("\n");

                // 약간의 지연
                Thread.sleep(50);
            }

            result.append("\n총 ").append(count).append("개의 ERROR 로그 생성 완료\n\n");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Stateful pattern test failed", e);
            return ResponseEntity.status(500)
                    .body("상태 기반 패턴 테스트 실패: " + e.getMessage());
        }
    }

    /**
     * 패턴 상태 리셋
     */
    @PostMapping("/patterns/reset")
    public ResponseEntity<String> resetPatternState(
            @RequestParam(required = false) String patternId) {

        try {
            if (patternId != null && !patternId.isEmpty()) {
                patternDetector.resetPattern(patternId);
                return ResponseEntity.ok("패턴 '" + patternId + "' 상태가 초기화되었습니다.");
            } else {
                patternDetector.resetAllPatterns();
                return ResponseEntity.ok("모든 패턴의 상태가 초기화되었습니다.");
            }
        } catch (Exception e) {
            log.error("Failed to reset pattern state", e);
            return ResponseEntity.status(500)
                    .body("패턴 상태 초기화 실패: " + e.getMessage());
        }
    }
}