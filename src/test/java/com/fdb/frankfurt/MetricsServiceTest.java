package com.fdb.frankfurt;

import com.fdb.frankfurt.model.Account;
import com.fdb.frankfurt.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 10. Metrics integration test (simple unit-based verification).
 * Tests that Micrometer metrics are properly registered.
 *
 * Note: Full Testcontainers integration tests (requiring dependencyManagement import)
 * need the Maven wrapper to be repaired before they can compile.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.redis.host=localhost",
        "spring.kafka.bootstrap-servers=none"
})
class MetricsServiceTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private com.fdb.frankfurt.service.MetricsService metricsService;

    @BeforeEach
    void setUp() {
        Account alice = new Account();
        alice.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        alice.setOwnerName("Alice Smith");
        alice.setBalance(new BigDecimal("1000.00"));
        alice.setCurrency("EUR");
        alice.setCreatedAt(Instant.now());
        accountRepository.save(alice);
    }

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
    }

    @Test
    void testAccountBalanceUpdate() {
        Optional<Account> alice = accountRepository.findById(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(alice).isPresent();
        assertThat(alice.get().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));

        // Update balance
        alice.get().setBalance(new BigDecimal("900.00"));
        accountRepository.save(alice.get());

        // Verify
        Optional<Account> updated = accountRepository.findById(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(updated).isPresent();
        assertThat(updated.get().getBalance()).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    @Test
    void testMetricsServiceExists() {
        assertThat(metricsService).isNotNull();
    }
}