package com.fdb.frankfurt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequest {
    @NotNull
    private UUID fromAccountId;
    
    @NotNull
    private UUID toAccountId;
    
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
}
