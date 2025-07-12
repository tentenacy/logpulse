package com.tenacy.logpulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailAlertService implements AlertService {

    private final JavaMailSender mailSender;

    @Value("${logpulse.alert.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${logpulse.alert.email.sender:}")
    private String sender;

    @Value("${logpulse.alert.email.recipients:}")
    private String recipients;

    @Override
    public void sendAlert(String subject, String message) {
        // 로그에는 항상 기록
        log.info("ALERT - Subject: {}, Message: {}", subject, message);

        // 이메일 설정이 유효한지 확인
        if (!isEmailConfigValid()) {
            return;
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sender);
            mailMessage.setTo(recipients.split(","));
            mailMessage.setSubject("[LogPulse Alert] " + subject);
            mailMessage.setText(message);

            mailSender.send(mailMessage);
            log.info("Alert email sent: {}", subject);
        } catch (Exception e) {
            log.error("Failed to send alert email: {}", e.getMessage(), e);
        }
    }

    private boolean isEmailConfigValid() {
        return emailEnabled &&
                StringUtils.hasText(sender) &&
                StringUtils.hasText(recipients);
    }
}