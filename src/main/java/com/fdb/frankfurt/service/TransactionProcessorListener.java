package com.fdb.frankfurt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fdb.frankfurt.dto.TransferRequest;
import com.fdb.frankfurt.model.Account;
import com.fdb.frankfurt.model.Transaction;
import com.fdb.frankfurt.repository.AccountRepository;
import com.fdb.frankfurt.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionProcessorListener {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "transactions", groupId = "fdb-core-group")
    @SuppressWarnings("null")
    public void consumeTransaction(@Payload String payload,
                                   @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String transactionIdStr,
                                   Acknowledgment acknowledgment) {
        log.info("Received transaction event from Kafka. Key: {}", transactionIdStr);

        if (transactionIdStr == null || payload == null) {
            log.error("Received message with null key or payload");
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        UUID transactionId;
        try {
            transactionId = UUID.fromString(transactionIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid transaction ID format in Kafka message key: {}", transactionIdStr, e);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        // 1. Idempotency: SET NX in Redis by key processed:{transactionId} with 24 hours TTL
        String idempotencyKey = "processed:" + transactionId;
        Boolean isNew = stringRedisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isNew)) {
            log.info("Transaction {} already processed (Idempotency check). Skipping.", transactionId);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        TransferRequest request;
        try {
            request = objectMapper.readValue(payload, TransferRequest.class);
        } catch (Exception e) {
            log.error("Failed to deserialize transfer request payload for transaction {}", transactionId, e);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        UUID fromId = request.getFromAccountId();
        UUID toId = request.getToAccountId();
        BigDecimal amount = request.getAmount();

        // 2. Distributed lock on both accounts via Redisson in deterministic order (ascending ID)
        UUID firstId = fromId.compareTo(toId) < 0 ? fromId : toId;
        UUID secondId = fromId.compareTo(toId) < 0 ? toId : fromId;

        RLock lock1 = redissonClient.getLock("lock:account:" + firstId);
        RLock lock2 = redissonClient.getLock("lock:account:" + secondId);

        boolean locked1 = false;
        boolean locked2 = false;

        try {
            locked1 = lock1.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked1) {
                log.warn("Could not acquire lock for account {}", firstId);
                throw new RuntimeException("Failed to acquire lock for account " + firstId);
            }

            locked2 = lock2.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked2) {
                log.warn("Could not acquire lock for account {}", secondId);
                throw new RuntimeException("Failed to acquire lock for account " + secondId);
            }

            // 3. Process transaction within DB transaction and cache-aside balance check
            processTransferWithCacheAside(transactionId, fromId, toId, amount);

            // Successfully processed, commit offset
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }

        } catch (Exception e) {
            log.error("Error processing transaction {}", transactionId, e);
            // Remove idempotency key so it can be retried if transient failure
            stringRedisTemplate.delete(idempotencyKey);
            throw new RuntimeException(e);
        } finally {
            if (locked2 && lock2.isHeldByCurrentThread()) {
                lock2.unlock();
            }
            if (locked1 && lock1.isHeldByCurrentThread()) {
                lock1.unlock();
            }
        }
    }

    @Transactional
    public void processTransferWithCacheAside(UUID transactionId, UUID fromId, UUID toId, BigDecimal amount) {
        Optional<Transaction> txOpt = transactionRepository.findById(transactionId);
        if (txOpt.isEmpty()) {
            log.error("Transaction not found in database: {}", transactionId);
            return;
        }

        Transaction tx = txOpt.get();
        if ("COMPLETED".equals(tx.getStatus()) || "FAILED".equals(tx.getStatus())) {
            log.info("Transaction {} already in final status {}", transactionId, tx.getStatus());
            return;
        }

        // Cache-aside for balance: check Redis cache, fallback to DB
        BigDecimal fromBalance = getCachedOrDbBalance(fromId);
        BigDecimal toBalance = getCachedOrDbBalance(toId);

        if (fromBalance.compareTo(amount) < 0) {
            log.warn("Insufficient funds for account {}. Balance: {}, Required: {}", fromId, fromBalance, amount);
            tx.setStatus("FAILED");
            tx.setUpdatedAt(OffsetDateTime.now());
            transactionRepository.save(tx);
            return;
        }

        // Update balances in DB
        Account fromAcc = accountRepository.findById(fromId).orElseThrow();
        Account toAcc = accountRepository.findById(toId).orElseThrow();

        fromAcc.setBalance(fromAcc.getBalance().subtract(amount));
        toAcc.setBalance(toAcc.getBalance().add(amount));

        accountRepository.save(fromAcc);
        accountRepository.save(toAcc);

        // Invalidate / update balance cache in Redis
        stringRedisTemplate.opsForValue().set("balance:" + fromId, fromAcc.getBalance().toString());
        stringRedisTemplate.opsForValue().set("balance:" + toId, toAcc.getBalance().toString());

        // Update transaction status
        tx.setStatus("COMPLETED");
        tx.setUpdatedAt(OffsetDateTime.now());
        transactionRepository.save(tx);

        log.info("Successfully processed transaction {}: transferred {} from {} to {}", transactionId, amount, fromId, toId);
    }

    private BigDecimal getCachedOrDbBalance(UUID accountId) {
        String cacheKey = "balance:" + accountId;
        String cachedBalance = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedBalance != null) {
            return new BigDecimal(cachedBalance);
        }
        // Cache miss: load from DB
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        stringRedisTemplate.opsForValue().set(cacheKey, account.getBalance().toString());
        return account.getBalance();
    }
}
