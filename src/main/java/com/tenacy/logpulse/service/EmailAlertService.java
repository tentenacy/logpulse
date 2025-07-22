package com.tenacy.logpulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        log.info("ALERT - Subject: {}, Message: {}", subject, message);

        if (!isEmailConfigValid()) {
            return;
        }

        try {
            // 수신자 목록 파싱
            String[] recipientArray = recipients.split(",");

            // 도메인별로 수신자 그룹화
            Map<String, List<String>> domainRecipients = groupRecipientsByDomain(recipientArray);

            // 각 도메인 그룹별로 별도의 이메일 발송
            for (Map.Entry<String, List<String>> entry : domainRecipients.entrySet()) {
                String domain = entry.getKey();
                List<String> domainEmails = entry.getValue();

                log.info("Sending email alert to {} recipient(s) at domain {}", domainEmails.size(), domain);

                SimpleMailMessage mailMessage = new SimpleMailMessage();
                mailMessage.setFrom(sender);
                mailMessage.setTo(domainEmails.toArray(new String[0]));
                mailMessage.setSubject("[LogPulse Alert] " + subject);
                mailMessage.setText(message);

                mailSender.send(mailMessage);
                log.info("Alert email sent successfully to domain {}: {}", domain, subject);
            }
        } catch (Exception e) {
            log.error("Failed to send alert email: {}", e.getMessage(), e);
        }
    }

    private boolean isEmailConfigValid() {
        return emailEnabled &&
                StringUtils.hasText(sender) &&
                StringUtils.hasText(recipients);
    }

    private Map<String, List<String>> groupRecipientsByDomain(String[] recipients) {
        Map<String, List<String>> domainGroups = new HashMap<>();

        for (String email : recipients) {
            email = email.trim();
            if (email.isEmpty()) {
                continue;
            }

            // 도메인 추출
            String domain = extractDomain(email);

            // 도메인별로 그룹화
            domainGroups.computeIfAbsent(domain, k -> new ArrayList<>()).add(email);
        }

        return domainGroups;
    }

    private String extractDomain(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex > 0 && atIndex < email.length() - 1) {
            return email.substring(atIndex + 1);
        }
        return "unknown";
    }
}