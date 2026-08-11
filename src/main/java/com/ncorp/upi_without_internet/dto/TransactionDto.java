package com.ncorp.upi_without_internet.dto;

import com.ncorp.upi_without_internet.modal.Status;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class TransactionDto {

    private Long id;

    private String senderVpa;

    private String receiverVpa;

    private BigDecimal amount;

    private Instant signedAt;

    private Instant settledAt;

    private String bridgeNodeId;

    private int hopCount;

    private Status status;
}
