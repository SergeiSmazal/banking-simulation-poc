package com.fdb.frankfurt.service;

import com.fdb.frankfurt.model.OutboxEvent;
import com.fdb.frankfurt.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishEvents() {
        // In a real system, you'd use pagination or LIMIT to avoid loading too many events at once
        List<OutboxEvent> unprocessedEvents = outboxRepository.findAll().stream()
                .filter(event -> event.getProcessedAt() == null)
                .toList();

        for (OutboxEvent event : unprocessedEvents) {
            log.info("Publishing event: {}", event.getId());
            
            // Assuming the aggregateId is the transactionId, and we use it as the Kafka key
            kafkaTemplate.send("transactions", event.getAggregateId(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            event.setProcessedAt(OffsetDateTime.now());
                            outboxRepository.save(event);
                            log.info("Event published successfully: {}", event.getId());
                        } else {
                            log.error("Failed to publish event: {}", event.getId(), ex);
                        }
                    });
        }
    }
}
