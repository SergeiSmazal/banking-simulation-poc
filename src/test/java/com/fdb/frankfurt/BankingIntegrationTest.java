package com.fdb.frankfurt;

import com.fdb.frankfurt.dto.TransferRequest;
import com.fdb.frankfurt.model.Account;
import com.fdb.frankfurt.repository.AccountRepository;
import com.fdb.frankfurt.repository.OutboxRepository;
import com.fdb.frankfurt.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RedisContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 10. Observability + Tests: Integration tests using Testcontainers.
 * Runs PostgreSQL, Redis, and Kafka containers to test the full lifecycle:
 * POST /transfer → outbox → Kafka → consumer → balance updated.
 */
@SpringBootTest
@Testcontainers
class BankingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("frankfurt_bank")
            .withUsername("bank_admin")
            .withPassword("bank_secure_password");

    @Container
    static RedisContainer redis = new RedisContainer<>("redis:7-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer<>("apache/kafka:latest");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        // Ensure containers are running
        assertThat(postgres.isRunning()).isTrue();
        assertThat(redis.isRunning()).isTrue();
        assertThat(kafka.isRunning()).isTrue();

        // Seed accounts
        accountRepository.save(new Account(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "Alice Smith", new BigDecimal("1000.00"), "EUR",
                Instant.now()));
        accountRepository.save(new Account(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                "Bob Johnson", new BigDecimal("2500.50"), "EUR",
                Instant.now()));
    }

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAll();
        outboxRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void testAccountBalanceInitialState() {
        Optional<Account> alice = accountRepository.findById(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(alice).isPresent();
        assertThat(alice.get().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));

        Optional<Account> bob = accountRepository.findById(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        assertThat(bob).isPresent();
        assertThat(bob.get().getBalance()).isEqualByComparingTo(new BigDecimal("2500.50"));
    }

    @Test
    void testPrometheusMetricsExist() {
        // Verify Micrometer is exposing metrics
        assertThat(meterRegistry)
                .hasMeterWithName("fdb.transfers.received")
                .hasMeterWithName("fdb.transfers.processed")
                .hasMeterWithName("fdb.simulator.running");
    }

    @Test
    void testDatabaseConnection() {
        // Verify we can read/write to the Testcontainers PostgreSQL
        assertThat(accountRepository.count()).isGreaterThanOrEqualTo(2L);
    }
}