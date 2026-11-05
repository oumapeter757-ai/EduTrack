package com.peterscode.edutrack.notification.provider;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Objects;

public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailProvider(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = Objects.requireNonNull(mailSender);
        this.fromAddress = validateAddress(fromAddress);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ProviderReceipt send(EmailDelivery delivery) throws DeliveryFailureException {
        String destination = validateAddress(delivery.destination());
        String subject = rejectHeaderControls(delivery.subject());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(destination);
            helper.setSubject(subject);
            helper.setText(delivery.body(), delivery.html());
            mailSender.send(message);
            return new ProviderReceipt(null);
        } catch (MessagingException | MailException exception) {
            throw new DeliveryFailureException("SMTP_OUTCOME_UNKNOWN", true, true);
        }
    }

    private String validateAddress(String value) {
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Email address is invalid");
        }
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            return address.getAddress();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Email address is invalid");
        }
    }

    private String rejectHeaderControls(String value) {
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Email subject is invalid");
        }
        return value;
    }
}
