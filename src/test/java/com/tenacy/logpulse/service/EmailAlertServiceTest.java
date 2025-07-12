package com.tenacy.logpulse.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EmailAlertServiceTest {

    @Autowired
    private EmailAlertService emailAlertService;

    @Test
    void sendAlertTest() {
        emailAlertService.sendAlert(
                "테스트 알림",
                "이것은 LogPulse 시스템의 테스트 알림입니다.\n\n" +
                        "심각한 오류가 발생했습니다: NullPointerException in UserService"
        );
    }
}