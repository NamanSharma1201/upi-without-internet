package com.ncorp.upi_without_internet.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {
    private String vpa;
    private String holderName;
    private BigDecimal balance;
}
