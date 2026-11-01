package com.peterscode.edutrack.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterscode.edutrack.payment.dto.MpesaCallbackRequest;
import com.peterscode.edutrack.payment.service.MpesaCallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MpesaWebhookControllerTest {

    @Mock
    private MpesaCallbackService mpesaCallbackService;

    @InjectMocks
    private MpesaWebhookController controller;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MpesaCallbackRequest validationCallback;
    private MpesaCallbackRequest confirmationCallback;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        validationCallback = buildCallback("EDU-TEST-20250101120000", new BigDecimal("5000.00"), null, 0, "Success");
        confirmationCallback = buildCallback("EDU-TEST-20250101120000", new BigDecimal("5000.00"), "SLM1234567", 0, "Success");
    }

    private MpesaCallbackRequest buildCallback(String invoiceNumber, BigDecimal amount, String mpesaReceipt, int resultCode, String resultDesc) {
        MpesaCallbackRequest request = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback stk = new MpesaCallbackRequest.StkCallback();
        stk.setResultCode(resultCode);
        stk.setResultDesc(resultDesc);
        stk.setCheckoutRequestID("REQ_12345");

        MpesaCallbackRequest.CallbackMetadata metadata = new MpesaCallbackRequest.CallbackMetadata();
        List<MpesaCallbackRequest.Item> items = new ArrayList<>();
        items.add(new MpesaCallbackRequest.Item("Account_Reference", invoiceNumber));
        if (amount != null) {
            items.add(new MpesaCallbackRequest.Item("Amount", amount.toString()));
        }
        if (mpesaReceipt != null) {
            items.add(new MpesaCallbackRequest.Item("MpesaReceiptNumber", mpesaReceipt));
        }
        metadata.setItem(items);
        stk.setCallbackMetadata(metadata);
        body.setStkCallback(stk);
        request.setBody(body);
        return request;
    }

    // ─── Validation Endpoint Tests ───

    @Test
    @DisplayName("POST /validation returns 200 OK with ResultCode 0 for valid callback")
    void handleValidation_validCallback_returnsOk() throws Exception {
        MpesaCallbackRequest response = buildAcceptResponse();
        when(mpesaCallbackService.handleValidation(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/callback/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationCallback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Body.stkCallback.ResultCode").value(0))
                .andExpect(jsonPath("$.Body.stkCallback.ResultDesc").value("Accept"));

        verify(mpesaCallbackService).handleValidation(any());
    }

    @Test
    @DisplayName("POST /validation returns 200 OK with ResultCode 1 for rejected callback")
    void handleValidation_rejectedCallback_returnsRejected() throws Exception {
        MpesaCallbackRequest response = buildRejectResponse("Invoice not found");
        when(mpesaCallbackService.handleValidation(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/callback/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationCallback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Body.stkCallback.ResultCode").value(1))
                .andExpect(jsonPath("$.Body.stkCallback.ResultDesc").value("Invoice not found"));
    }

    // ─── Confirmation Endpoint Tests ───

    @Test
    @DisplayName("POST /confirmation returns 200 OK for valid callback")
    void handleConfirmation_validCallback_returnsOk() throws Exception {
        when(mpesaCallbackService.handleConfirmation(any())).thenReturn(confirmationCallback);

        mockMvc.perform(post("/api/v1/payments/callback/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmationCallback)))
                .andExpect(status().isOk());

        verify(mpesaCallbackService).handleConfirmation(any());
    }

    @Test
    @DisplayName("POST /confirmation parses Mpesa JSON payload correctly")
    void handleConfirmation_parsesMpesaJsonPayload() throws Exception {
        MpesaCallbackRequest response = buildAcceptResponse();
        when(mpesaCallbackService.handleConfirmation(any())).thenReturn(response);

        String jsonPayload = objectMapper.writeValueAsString(confirmationCallback);

        mockMvc.perform(post("/api/v1/payments/callback/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Body.stkCallback.ResultCode").value(0));
    }

    @Test
    @DisplayName("POST /confirmation handles failed payment callback")
    void handleConfirmation_failedPayment_returnsOk() throws Exception {
        MpesaCallbackRequest failedCallback = buildCallback(
                "EDU-TEST-20250101120000",
                new BigDecimal("5000.00"),
                null,
                1, "Payment declined");
        when(mpesaCallbackService.handleConfirmation(any())).thenReturn(failedCallback);

        mockMvc.perform(post("/api/v1/payments/callback/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failedCallback)))
                .andExpect(status().isOk());

        verify(mpesaCallbackService).handleConfirmation(any());
    }

    private MpesaCallbackRequest buildAcceptResponse() {
        MpesaCallbackRequest response = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback stk = new MpesaCallbackRequest.StkCallback();
        stk.setResultCode(0);
        stk.setResultDesc("Accept");
        body.setStkCallback(stk);
        response.setBody(body);
        return response;
    }

    private MpesaCallbackRequest buildRejectResponse(String reason) {
        MpesaCallbackRequest response = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback stk = new MpesaCallbackRequest.StkCallback();
        stk.setResultCode(1);
        stk.setResultDesc(reason);
        body.setStkCallback(stk);
        response.setBody(body);
        return response;
    }
}
