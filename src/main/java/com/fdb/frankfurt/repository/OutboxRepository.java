package com.fdb.frankfurt.repository;

import com.fdb.frankfurt.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
}
