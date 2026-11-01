package com.peterscode.edutrack.payment.dto;

import com.peterscode.edutrack.payment.enums.PaymentTransactionEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionDto {

    private Long id;
    private Long invoiceId;
    private String invoiceNumber;
    private PaymentTransactionEventType eventType;
    private String mpesaTransactionId;
    private BigDecimal amount;
    private String details;
    private LocalDateTime createdAt;
}
