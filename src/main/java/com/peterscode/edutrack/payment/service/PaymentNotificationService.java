package com.peterscode.edutrack.payment.service;

import com.peterscode.edutrack.payment.config.MpesaConfig;
import com.peterscode.edutrack.payment.entity.PaymentInvoice;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentNotificationService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final MpesaConfig mpesaConfig;

    /**
     * Sends dual email notifications after successful payment confirmation:
     * 1. Payment receipt to the school admin.
     * 2. Audit notification to all super admins.
     */
    public void sendPaymentConfirmationEmails(PaymentInvoice invoice) {
        School school = invoice.getSchool();
        sendSchoolAdminReceipt(invoice, school);
        sendSuperAdminAuditNotification(invoice, school);
    }

    /**
     * Sends a payment receipt email to the school admin after successful payment.
     */
    public void sendPaymentReceipt(PaymentInvoice invoice) {
        School school = invoice.getSchool();
        sendSchoolAdminReceipt(invoice, school);
    }

    private void sendSchoolAdminReceipt(PaymentInvoice invoice, School school) {
        String adminEmail = school.getEmail();
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("No email configured for school {} ({})", school.getName(), school.getCode());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(adminEmail);
            helper.setSubject("Payment Receipt — EduTrack Subscription Activated");

            String body = buildSchoolAdminTemplate(invoice, school);
            helper.setText(body, true);

            mailSender.send(message);
            log.info("Payment receipt sent to {} for invoice {}", adminEmail, invoice.getInvoiceNumber());
        } catch (MessagingException e) {
            log.error("Failed to send payment receipt for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
        }
    }

    private void sendSuperAdminAuditNotification(PaymentInvoice invoice, School school) {
        List<User> superAdmins = userRepository.findByRole(Role.SUPER_ADMIN);
        if (superAdmins.isEmpty()) {
            log.warn("No SUPER_ADMIN users found to send audit notification for invoice {}", invoice.getInvoiceNumber());
            return;
        }

        for (User superAdmin : superAdmins) {
            if (superAdmin.getEmail() == null || superAdmin.getEmail().isBlank()) {
                continue;
            }
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(superAdmin.getEmail());
                helper.setSubject("[Audit] School Payment Confirmed — " + school.getName());

                String body = buildSuperAdminTemplate(invoice, school);
                helper.setText(body, true);

                mailSender.send(message);
                log.info("Super admin audit notification sent to {} for invoice {}", superAdmin.getEmail(), invoice.getInvoiceNumber());
            } catch (MessagingException e) {
                log.error("Failed to send super admin audit notification to {}: {}", superAdmin.getEmail(), e.getMessage(), e);
            }
        }
    }

    private String buildSchoolAdminTemplate(PaymentInvoice invoice, School school) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'>"
                + "<style>"
                + "body{font-family:Arial,sans-serif;background:#f4f6f9;margin:0;padding:40px 20px;color:#333}"
                + ".container{max-width:600px;margin:auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.1)}"
                + ".header{background:#4caf50;color:#fff;padding:24px;text-align:center}"
                + ".header h1{margin:0;font-size:22px}"
                + ".body{padding:24px}"
                + ".body p{line-height:1.6}"
                + "table{width:100%;border-collapse:collapse;margin:16px 0}"
                + "th,td{text-align:left;padding:10px 12px;border-bottom:1px solid #eee}"
                + "th{background:#f9f9f9;font-weight:600;color:#555}"
                + ".amount{font-size:20px;font-weight:700;color:#4caf50}"
                + ".footer{background:#f9f9f9;padding:16px 24px;text-align:center;font-size:12px;color:#888}"
                + "</style></head><body>"
                + "<div class='container'>"
                + "<div class='header'><h1>✓ Payment Successful</h1></div>"
                + "<div class='body'>"
                + "<p>Dear <strong>" + escapeHtml(school.getName()) + "</strong> Admin,</p>"
                + "<p>Your subscription payment has been processed successfully. Your EduTrack account has been updated.</p>"
                + "<table>"
                + "<tr><th>Invoice Number</th><td>" + escapeHtml(invoice.getInvoiceNumber()) + "</td></tr>"
                + "<tr><th>Amount Paid</th><td class='amount'>" + escapeHtml(invoice.getCurrency()) + " " + invoice.getAmount() + "</td></tr>"
                + "<tr><th>Subscription Tier</th><td>" + escapeHtml(invoice.getTier().name()) + "</td></tr>"
                + "<tr><th>Billing Period</th><td>" + escapeHtml(invoice.getBillingPeriod().name()) + "</td></tr>"
                + (invoice.getMpesaTransactionId() != null ? "<tr><th>Mpesa Transaction ID</th><td>" + escapeHtml(invoice.getMpesaTransactionId()) + "</td></tr>" : "")
                + (invoice.getConfirmedAt() != null ? "<tr><th>Payment Date</th><td>" + escapeHtml(invoice.getConfirmedAt().toString()) + "</td></tr>" : "")
                + "<tr><th>Active Tier</th><td>" + escapeHtml(school.getSubscriptionTier().name()) + "</td></tr>"
                + (school.getSubscriptionStartDate() != null ? "<tr><th>Subscription Start</th><td>" + escapeHtml(school.getSubscriptionStartDate().toString()) + "</td></tr>" : "")
                + (school.getSubscriptionEndDate() != null ? "<tr><th>Subscription End</th><td>" + escapeHtml(school.getSubscriptionEndDate().toString()) + "</td></tr>" : "")
                + "</table>"
                + "<p>Your subscription is now active. You can access all features included in your tier.</p>"
                + "<p>Thank you for choosing EduTrack!</p>"
                + "</div>"
                + "<div class='footer'>© 2025 EduTrack. All rights reserved.</div>"
                + "</div></body></html>";
    }

    private String buildSuperAdminTemplate(PaymentInvoice invoice, School school) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'>"
                + "<style>"
                + "body{font-family:Arial,sans-serif;background:#f4f6f9;margin:0;padding:40px 20px;color:#333}"
                + ".container{max-width:600px;margin:auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.1)}"
                + ".header{background:#2196f3;color:#fff;padding:24px;text-align:center}"
                + ".header h1{margin:0;font-size:22px}"
                + ".body{padding:24px}"
                + ".body p{line-height:1.6}"
                + "table{width:100%;border-collapse:collapse;margin:16px 0}"
                + "th,td{text-align:left;padding:10px 12px;border-bottom:1px solid #eee}"
                + "th{background:#f9f9f9;font-weight:600;color:#555}"
                + ".amount{font-size:20px;font-weight:700;color:#2196f3}"
                + ".footer{background:#f9f9f9;padding:16px 24px;text-align:center;font-size:12px;color:#888}"
                + "</style></head><body>"
                + "<div class='container'>"
                + "<div class='header'><h1>📋 Payment Audit Notification</h1></div>"
                + "<div class='body'>"
                + "<p>A school admin has successfully completed a subscription payment.</p>"
                + "<table>"
                + "<tr><th>School Name</th><td>" + escapeHtml(school.getName()) + "</td></tr>"
                + "<tr><th>School Code</th><td>" + escapeHtml(school.getCode()) + "</td></tr>"
                + "<tr><th>Admin Email</th><td>" + escapeHtml(school.getEmail()) + "</td></tr>"
                + "<tr><th>Invoice Number</th><td>" + escapeHtml(invoice.getInvoiceNumber()) + "</td></tr>"
                + "<tr><th>Amount</th><td class='amount'>" + escapeHtml(invoice.getCurrency()) + " " + invoice.getAmount() + "</td></tr>"
                + "<tr><th>Subscription Tier</th><td>" + escapeHtml(invoice.getTier().name()) + "</td></tr>"
                + "<tr><th>Billing Period</th><td>" + escapeHtml(invoice.getBillingPeriod().name()) + "</td></tr>"
                + (invoice.getMpesaTransactionId() != null ? "<tr><th>Mpesa Transaction ID</th><td>" + escapeHtml(invoice.getMpesaTransactionId()) + "</td></tr>" : "")
                + (invoice.getConfirmedAt() != null ? "<tr><th>Confirmed At</th><td>" + escapeHtml(invoice.getConfirmedAt().toString()) + "</td></tr>" : "")
                + "</table>"
                + "<p>The subscription has been automatically activated for the school.</p>"
                + "</div>"
                + "<div class='footer'>© 2025 EduTrack — Internal Audit Notification</div>"
                + "</div></body></html>";
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

}
