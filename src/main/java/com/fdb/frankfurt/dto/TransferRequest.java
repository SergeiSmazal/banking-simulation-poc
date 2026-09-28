package com.fdb.frankfurt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    @NotNull
    private UUID fromAccountId;
    
    @NotNull
    private UUID toAccountId;
    
    @NotNull
    private BigDecimal amount;
}
