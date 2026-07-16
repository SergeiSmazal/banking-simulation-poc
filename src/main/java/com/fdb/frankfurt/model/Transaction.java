package com.fdb.frankfurt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
public class Transaction {
    @Id
    private UUID id;
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    @Column(nullable = false)
    private BigDecimal amount;
    
    @Column(nullable = false)
    private String status; // PENDING, COMPLETED, FAILED
    
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    
    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
