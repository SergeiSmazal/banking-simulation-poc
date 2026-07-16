package com.fdb.frankfurt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
public class OutboxEvent {
    @Id
    private UUID id;
    
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;
    
    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    private OffsetDateTime processedAt;
}
