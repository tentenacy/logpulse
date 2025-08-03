package com.tenacy.logpulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailAlertService implements AlertService {

    private final JavaMailSender mailSender;

    @Value("${logpulse.alert.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${logpulse.alert.email.sender}")
    private String sender;

    @Value("${logpulse.alert.email.recipients}")
    private String recipients;

    @Async("emailTaskExecutor")
    @Override
    public void sendAlert(String subject, String message) {
        log.info("알림 - 제목: {}, 메시지: {}", subject, message);

        if (!isEmailConfigValid()) {
            return;
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sender);
            mailMessage.setTo(recipients.split(","));
            mailMessage.setSubject("[LogPulse 알림] " + subject);
            mailMessage.setText(message);

            mailSender.send(mailMessage);
            log.info("알림 이메일이 성공적으로 전송되었습니다: {}", subject);
        } catch (Exception e) {
            log.error("알림 이메일 전송 실패: {}", e.getMessage(), e);
        }
    }

    private boolean isEmailConfigValid() {
        return emailEnabled &&
                StringUtils.hasText(sender) &&
                StringUtils.hasText(recipients);
    }
}