package com.tenacy.logpulse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailAlertService implements AlertService {

    @Override
    public void sendAlert(String subject, String message) {
        // 구현 예정
        log.info("ALERT - Subject: {}, Message: {}", subject, message);
    }
}