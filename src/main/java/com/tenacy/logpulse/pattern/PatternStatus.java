package com.tenacy.logpulse.pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PatternStatus {
    private final boolean detected;
    private final PatternResult result;  // 패턴이 감지된 경우에만 값이 있음
}