package com.peterscode.edutrack.fees.mpesa;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/**
 * Production implementation of MpesaClient using the Safaricom Daraja API.
 * <p>
 * This component is NOT used in tests — tests use FakeMpesaClient.
 * The STK Push endpoint and response format are marked UNVERIFIED
 * until confirmed against current Safaricom Daraja documentation.
 * </p>
 */
@Slf4j
@Component
public class DarajaMpesaClient implements MpesaClient {

    // UNVERIFIED: STK Push endpoint — confirm against Safaricom Daraja docs
    private static final String STK_PUSH_ENDPOINT = "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest";
    // UNVERIFIED: Status query endpoint
    private static final String STATUS_QUERY_ENDPOINT = "https://sandbox.safaricom.co.ke/mpesa/stkpushquery/v1/query";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Value("${mpesa.till-number}")
    private String tillNumber;

    @Value("${mpesa.passkey}")
    private String passkey;

    @Value("${mpesa.environment:sandbox}")
    private String environment;

    @Value("${mpesa.stk-callback-url:https://localhost:8080/api/v1/fees/callback/stk}")
    private String stkCallbackUrl;

    public DarajaMpesaClient(RestTemplate restTemplate, ObjectMapper objectMapper, Clock clock) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public StkPushResult initiateStkPush(String phoneNumber, BigDecimal amount,
                                          String callbackUrl, String accountReference,
                                          String description) {
        try {
            String timestamp = Instant.now(clock).toString().replace(":", "").replace(".", "").substring(0, 14);
            String password = Base64.getEncoder().encodeToString(
                    (tillNumber + passkey + timestamp).getBytes(StandardCharsets.UTF_8));

            StkPushRequest request = new StkPushRequest(
                    accountReference,
                    tillNumber,
                    "BillNumber",
                    "Lipa na Mpesa",
                    amount.toPlainString(),
                    callbackUrl != null ? callbackUrl : stkCallbackUrl,
                    phoneNumber,
                    password,
                    timestamp
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer PLACEHOLDER_TOKEN");

            HttpEntity<StkPushRequest> entity = new HttpEntity<>(request, headers);

            log.info("Initiating STK Push: phone={}, amount={}, accountRef={}",
                    phoneNumber, amount, accountReference);

            // UNVERIFIED: response format — confirm against Daraja docs
            ResponseEntity<String> response = restTemplate.postForEntity(
                    STK_PUSH_ENDPOINT, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                StkPushResponse body = objectMapper.readValue(response.getBody(), StkPushResponse.class);
                return new StkPushResult(
                        body.checkoutRequestID(),
                        body.customerMessage(),
                        "0".equals(body.resultCode())
                );
            }
            return new StkPushResult(null, "Request failed", false);
        } catch (Exception e) {
            log.error("STK Push initiation failed", e);
            return new StkPushResult(null, e.getMessage(), false);
        }
    }

    @Override
    public StkPushStatusResult queryStkPushStatus(String checkoutRequestID) {
        try {
            // UNVERIFIED: status query format
            String timestamp = Instant.now(clock).toString().replace(":", "").replace(".", "").substring(0, 14);
            String password = Base64.getEncoder().encodeToString(
                    (tillNumber + passkey + timestamp).getBytes(StandardCharsets.UTF_8));

            StatusQueryRequest request = new StatusQueryRequest(
                    checkoutRequestID, tillNumber, password, timestamp);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer PLACEHOLDER_TOKEN");

            HttpEntity<StatusQueryRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    STATUS_QUERY_ENDPOINT, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                StatusQueryResponse body = objectMapper.readValue(response.getBody(), StatusQueryResponse.class);
                return new StkPushStatusResult(
                        checkoutRequestID,
                        body.resultCode(),
                        body.resultDesc(),
                        true
                );
            }
            return new StkPushStatusResult(checkoutRequestID, "1", "Query failed", true);
        } catch (Exception e) {
            log.error("STK status query failed", e);
            return new StkPushStatusResult(checkoutRequestID, "1", e.getMessage(), true);
        }
    }

    // UNVERIFIED: request/response formats — confirm against Daraja docs
    record StkPushRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("BusinessShortCode") String businessShortCode,
            @com.fasterxml.jackson.annotation.JsonProperty("Password") String password,
            @com.fasterxml.jackson.annotation.JsonProperty("Timestamp") String timestamp,
            @com.fasterxml.jackson.annotation.JsonProperty("TransactionType") String transactionType,
            @com.fasterxml.jackson.annotation.JsonProperty("Amount") String amount,
            @com.fasterxml.jackson.annotation.JsonProperty("PartyA") String partyA,
            @com.fasterxml.jackson.annotation.JsonProperty("PartyB") String partyB,
            @com.fasterxml.jackson.annotation.JsonProperty("PhoneNumber") String phoneNumber,
            @com.fasterxml.jackson.annotation.JsonProperty("CallBackURL") String callBackURL,
            @com.fasterxml.jackson.annotation.JsonProperty("AccountReference") String accountReference,
            @com.fasterxml.jackson.annotation.JsonProperty("TransactionDesc") String transactionDesc
    ) {
        public StkPushRequest(String accountReference, String partyB, String transactionType,
                              String transactionDesc, String amount, String callBackURL,
                              String partyA, String password, String timestamp) {
            this(accountReference, partyB, transactionType, transactionDesc,
                    amount, partyA, partyB, partyA, callBackURL, accountReference, transactionDesc);
        }
    }

    record StkPushResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("CheckoutRequestID") String checkoutRequestID,
            @com.fasterxml.jackson.annotation.JsonProperty("CustomerMessage") String customerMessage,
            @com.fasterxml.jackson.annotation.JsonProperty("resultCode") String resultCode
    ) {}

    record StatusQueryRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("BusinessShortCode") String businessShortCode,
            @com.fasterxml.jackson.annotation.JsonProperty("Password") String password,
            @com.fasterxml.jackson.annotation.JsonProperty("Timestamp") String timestamp,
            @com.fasterxml.jackson.annotation.JsonProperty("CheckoutRequestID") String checkoutRequestID
    ) {}

    record StatusQueryResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("CheckoutRequestID") String checkoutRequestID,
            @com.fasterxml.jackson.annotation.JsonProperty("resultCode") String resultCode,
            @com.fasterxml.jackson.annotation.JsonProperty("resultDesc") String resultDesc
    ) {}
}
