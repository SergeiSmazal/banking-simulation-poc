package com.fdb.frankfurt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account {
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private String ownerName;
    
    @Column(nullable = false)
    private BigDecimal balance;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
