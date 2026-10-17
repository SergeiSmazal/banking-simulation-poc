package com.fdb.frankfurt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fdb.frankfurt.dto.AnomalyAlert;
import com.fdb.frankfurt.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectorListener {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // Thresholds for rule-based anomaly detection
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("50000.00");
    private static final long MAX_TRANSFERS_PER_MINUTE = 5;

    @KafkaListener(topics = "transactions", groupId = "fdb-anomaly-group")
    @SuppressWarnings("null")
    public void detectAnomaly(@Payload String payload,
                              @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String transactionIdStr,
                              Acknowledgment acknowledgment) {
        log.info("Anomaly detector received transaction event. Key: {}", transactionIdStr);

        if (transactionIdStr == null || payload == null) {
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        UUID transactionId;
        try {
            transactionId = UUID.fromString(transactionIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid transaction ID format in anomaly listener: {}", transactionIdStr, e);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        TransferRequest request;
        try {
            request = objectMapper.readValue(payload, TransferRequest.class);
        } catch (Exception e) {
            log.error("Failed to parse transfer request for anomaly detection on tx {}", transactionId, e);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        UUID accountId = request.getFromAccountId();
        BigDecimal amount = request.getAmount();

        boolean isAnomaly = false;
        StringBuilder reason = new StringBuilder();

        // Rule 1: High amount check
        if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
            isAnomaly = true;
            reason.append("Unusually high transfer amount: ").append(amount).append(". ");
        }

        // Rule 2: High velocity check (sliding window per account in Redis)
        String velocityKey = "anomaly:velocity:" + accountId;
        Long count = stringRedisTemplate.opsForValue().increment(velocityKey);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(velocityKey, Duration.ofMinutes(1));
        }

        if (count != null && count > MAX_TRANSFERS_PER_MINUTE) {
            isAnomaly = true;
            reason.append("High transfer frequency: ").append(count).append(" transfers within 1 minute. ");
        }

        if (isAnomaly) {
            String alertReason = reason.toString().trim();
            log.warn("ANOMALY DETECTED! Transaction: {}, Account: {}, Reason: {}", transactionId, accountId, alertReason);

            AnomalyAlert alert = new AnomalyAlert(
                    transactionId,
                    accountId,
                    amount,
                    alertReason,
                    OffsetDateTime.now()
            );

            try {
                String alertJson = objectMapper.writeValueAsString(alert);
                stringRedisTemplate.convertAndSend("alerts", alertJson);
                log.info("Published anomaly alert to Redis Pub/Sub channel 'alerts'");
            } catch (Exception e) {
                log.error("Failed to publish anomaly alert for tx {}", transactionId, e);
            }
        }

        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}
