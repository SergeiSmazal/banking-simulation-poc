package com.fdb.frankfurt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyAlert {
    private UUID transactionId;
    private UUID accountId;
    private BigDecimal amount;
    private String reason;
    private OffsetDateTime timestamp;
}
