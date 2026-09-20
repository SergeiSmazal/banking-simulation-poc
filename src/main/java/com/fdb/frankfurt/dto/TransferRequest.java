package com.fdb.frankfurt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.lang.NonNull;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequest {
    @NotNull
    @NonNull
    private UUID fromAccountId;
    
    @NotNull
    @NonNull
    private UUID toAccountId;
    
    @NotNull
    private BigDecimal amount;
}
