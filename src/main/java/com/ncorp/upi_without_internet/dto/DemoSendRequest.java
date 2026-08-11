package com.ncorp.upi_without_internet.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DemoSendRequest {
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String pin;
    private Integer ttl;
    private String startDevice;
}
