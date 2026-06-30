package com.ncorp.upi_without_internet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentInstructionDto {
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String pinHash;
    private String nonce;     // UUID, unique per payment intent
    private Long signedAt;
}
