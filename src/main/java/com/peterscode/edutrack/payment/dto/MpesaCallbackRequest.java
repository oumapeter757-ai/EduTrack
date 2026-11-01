package com.peterscode.edutrack.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpesaCallbackRequest {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JsonProperty("stkCallback")
        private StkCallback stkCallback;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StkCallback {
        @JsonProperty("MerchantRequestID")
        private String MerchantRequestID;
        @JsonProperty("CheckoutRequestID")
        private String CheckoutRequestID;
        @JsonProperty("ResultCode")
        private Integer ResultCode;
        @JsonProperty("ResultDesc")
        private String ResultDesc;
        @JsonProperty("CallbackMetadata")
        private CallbackMetadata callbackMetadata;
        @JsonProperty("TransactionType")
        private String TransactionType;
        @JsonProperty("TransAmount")
        private Double TransAmount;
        @JsonProperty("TransDate")
        private String TransDate;
        @JsonProperty("TransID")
        private String TransID;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallbackMetadata {
        @JsonProperty("Item")
        private List<Item> item;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonProperty("Name")
        private String Name;
        @JsonProperty("Value")
        private String Value;
    }

    @JsonProperty("Body")
    private Body body;
}
