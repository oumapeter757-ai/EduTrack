package com.peterscode.edutrack.fees.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for C2B Mpesa callback.
 * Matches Safaricom's JSON callback format: Body → stkCallback → CallbackMetadata items.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record C2bCallbackRequest(
        @JsonProperty("Body") C2bBody body
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record C2bBody(
            @JsonProperty("stkCallback") StkCallback stkCallback
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StkCallback(
            @JsonProperty("MerchantRequestID") String merchantRequestID,
            @JsonProperty("CheckoutRequestID") String checkoutRequestID,
            @JsonProperty("ResultCode") Integer resultCode,
            @JsonProperty("ResultDesc") String resultDesc,
            @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackMetadata(
            @JsonProperty("Item") List<CallbackItem> item
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackItem(
            @JsonProperty("Name") String name,
            @JsonProperty("Value") String value
    ) {}
}
