package com.peterscode.edutrack.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterscode.edutrack.payment.dto.MpesaCallbackRequest;
import com.peterscode.edutrack.payment.entity.PaymentInvoice;
import com.peterscode.edutrack.payment.entity.SubscriptionPrice;
import com.peterscode.edutrack.payment.enums.BillingPeriod;
import com.peterscode.edutrack.payment.enums.PaymentInvoiceStatus;
import com.peterscode.edutrack.payment.repository.PaymentInvoiceRepository;
import com.peterscode.edutrack.payment.repository.PaymentTransactionRepository;
import com.peterscode.edutrack.payment.repository.SubscriptionPriceRepository;
import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import com.peterscode.edutrack.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "spring.mail.username=test",
        "spring.mail.password=test",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enabled=false"
})
class MpesaWebhookIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentInvoiceRepository paymentInvoiceRepository;

    @Autowired
    private SubscriptionPriceRepository subscriptionPriceRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    private School testSchool;
    private PaymentInvoice pendingInvoice;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        paymentTransactionRepository.deleteAll();
        paymentInvoiceRepository.deleteAll();
        subscriptionPriceRepository.deleteAll();
        userRepository.deleteAll();
        schoolRepository.deleteAll();

        // Seed Super Admin for email notification
        User superAdmin = User.builder()
                .firstName("Super")
                .lastName("Admin")
                .username("superadmin")
                .email("superadmin@edutrack.com")
                .password("Password123!")
                .role(Role.SUPER_ADMIN)
                .schoolId(0L)
                .active(true)
                .build();
        userRepository.save(superAdmin);

        // Seed School
        testSchool = School.builder()
                .name("Test Academy")
                .code("TEST")
                .subdomain("test")
                .phone("+254712345678")
                .email("admin@testacademy.edu")
                .city("Nairobi")
                .country("Kenya")
                .timezone("Africa/Nairobi")
                .currency("KES")
                .subscriptionTier(SubscriptionTier.FREE)
                .active(true)
                .build();
        testSchool = schoolRepository.save(testSchool);

        // Seed Subscription Price
        SubscriptionPrice price = SubscriptionPrice.builder()
                .tier(SubscriptionTier.STANDARD)
                .billingPeriod(BillingPeriod.TERM)
                .amount(new BigDecimal("5000.00"))
                .currency("KES")
                .active(true)
                .build();
        subscriptionPriceRepository.save(price);

        // Seed Pending Invoice
        pendingInvoice = PaymentInvoice.builder()
                .invoiceNumber("EDU-TEST-20250101120000")
                .school(testSchool)
                .tier(SubscriptionTier.STANDARD)
                .billingPeriod(BillingPeriod.TERM)
                .amount(new BigDecimal("5000.00"))
                .currency("KES")
                .status(PaymentInvoiceStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        pendingInvoice = paymentInvoiceRepository.save(pendingInvoice);
    }

    private MpesaCallbackRequest buildConfirmationCallback(String invoiceNumber, BigDecimal amount, String mpesaReceipt, int resultCode, String resultDesc) {
        MpesaCallbackRequest request = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback stk = new MpesaCallbackRequest.StkCallback();
        stk.setResultCode(resultCode);
        stk.setResultDesc(resultDesc);
        stk.setCheckoutRequestID("REQ_INTEGRATION_001");

        MpesaCallbackRequest.CallbackMetadata metadata = new MpesaCallbackRequest.CallbackMetadata();
        List<MpesaCallbackRequest.Item> items = new ArrayList<>();
        items.add(new MpesaCallbackRequest.Item("Account_Reference", invoiceNumber));
        items.add(new MpesaCallbackRequest.Item("Amount", amount.toString()));
        items.add(new MpesaCallbackRequest.Item("MpesaReceiptNumber", mpesaReceipt));
        items.add(new MpesaCallbackRequest.Item("PhoneNumber", "254712345678"));
        items.add(new MpesaCallbackRequest.Item("TransactionDate", "20250101120000"));
        metadata.setItem(items);
        stk.setCallbackMetadata(metadata);
        body.setStkCallback(stk);
        request.setBody(body);
        return request;
    }

    @Test
    @DisplayName("POST /confirmation processes valid payment and activates subscription")
    void confirmation_validPayment_activatesSubscription() throws Exception {
        MpesaCallbackRequest callback = buildConfirmationCallback(
                pendingInvoice.getInvoiceNumber(),
                pendingInvoice.getAmount(),
                "SLM9876543",
                0, "Success");

        String response = mockMvc.perform(post("/api/v1/payments/callback/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify response contains ResultCode 0
        assertThat(response).contains("\"ResultCode\":0");

        // Refresh invoice from DB
        Optional<PaymentInvoice> savedInvoice = paymentInvoiceRepository.findByInvoiceNumber(pendingInvoice.getInvoiceNumber());
        assertThat(savedInvoice).isPresent();
        assertThat(savedInvoice.get().getStatus()).isEqualTo(PaymentInvoiceStatus.CONFIRMED);
        assertThat(savedInvoice.get().getMpesaTransactionId()).isEqualTo("SLM9876543");
        assertThat(savedInvoice.get().getConfirmedAt()).isNotNull();

        // Verify payment transactions were logged
        var transactions = paymentTransactionRepository.findByInvoiceId(pendingInvoice.getId());
        assertThat(transactions).isNotEmpty();
        assertThat(transactions.size()).isGreaterThanOrEqualTo(2);

        // Verify school subscription was updated
        testSchool = schoolRepository.findById(testSchool.getId()).orElseThrow();
        assertThat(testSchool.getSubscriptionTier()).isEqualTo(SubscriptionTier.STANDARD);

    }

    @Test
    @DisplayName("POST /confirmation returns 200 OK with proper JSON structure")
    void confirmation_responseStructure_isValid() throws Exception {
        MpesaCallbackRequest callback = buildConfirmationCallback(
                pendingInvoice.getInvoiceNumber(),
                pendingInvoice.getAmount(),
                "SLM1111111",
                0, "Success");

        mockMvc.perform(post("/api/v1/payments/callback/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Body").exists())
                .andExpect(jsonPath("$.Body.stkCallback").exists());
    }

    @Test
    @DisplayName("POST /confirmation handles failed payment gracefully")
    void confirmation_failedPayment_marksFailed() throws Exception {
        MpesaCallbackRequest callback = buildConfirmationCallback(
                pendingInvoice.getInvoiceNumber(),
                pendingInvoice.getAmount(),
                null,
                1, "Payment declined by customer");

        mockMvc.perform(post("/api/v1/payments/callback/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk());

        // Refresh invoice from DB
        Optional<PaymentInvoice> savedInvoice = paymentInvoiceRepository.findByInvoiceNumber(pendingInvoice.getInvoiceNumber());
        assertThat(savedInvoice).isPresent();
        assertThat(savedInvoice.get().getStatus()).isEqualTo(PaymentInvoiceStatus.FAILED);

    }

    @Test
    @DisplayName("POST /confirmation handles non-existent invoice gracefully")
    void confirmation_missingInvoice_returns200() throws Exception {
        MpesaCallbackRequest callback = buildConfirmationCallback(
                "EDU-NONEXISTENT-000000",
                new BigDecimal("5000.00"),
                "SLM2222222",
                0, "Success");

        mockMvc.perform(post("/api/v1/payments/callback/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk());

        // Verify no invoice was created
        Optional<PaymentInvoice> found = paymentInvoiceRepository.findByInvoiceNumber("EDU-NONEXISTENT-000000");
        assertThat(found).isEmpty();
    }
}
