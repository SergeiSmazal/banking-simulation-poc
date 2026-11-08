package com.fdb.frankfurt.service;

import com.fdb.frankfurt.model.OutboxEvent;
import com.fdb.frankfurt.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishEvents() {
        // In a real system, you'd use pagination or LIMIT to avoid loading too many events at once
        List<OutboxEvent> unprocessedEvents = outboxRepository.findAll().stream()
                .filter(event -> event != null && event.getProcessedAt() == null)
                .toList();

        for (OutboxEvent event : unprocessedEvents) {
            log.info("Publishing event: {}", event.getId());

            String aggregateId = event.getAggregateId();
            String payload = event.getPayload();
            if (aggregateId != null && payload != null) {
                kafkaTemplate.send("transactions", aggregateId, payload)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                event.setProcessedAt(OffsetDateTime.now());
                                outboxRepository.save(event);
                                log.info("Event published successfully: {}", event.getId());
                            } else {
                                log.error("Failed to publish event: {}", event.getId(), ex);
                            }
                        });
            } else {
                log.error("Missing aggregateId or payload for event: {}", event.getId());
            }
        }
    }
}