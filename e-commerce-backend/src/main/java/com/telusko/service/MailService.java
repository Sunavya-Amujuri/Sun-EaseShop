package com.telusko.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;

    /**
     * Sends a mail without propagating delivery failures.
     * <p>
     * Callers such as registration and password reset run inside a transaction, so letting a
     * mail error escape would roll back the account or token that was just written. The token
     * is persisted either way and can be re-sent via the resend endpoints.
     */
    public void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        try {
            mailSender.send(msg);
        } catch (MailException ex) {
            log.error("Failed to send mail '{}' to {}: {}", subject, to, ex.getMessage());
        }
    }
}