package com.fdb.frankfurt;

import com.fdb.frankfurt.dto.TransferRequest;
import com.fdb.frankfurt.model.OutboxEvent;
import com.fdb.frankfurt.model.Transaction;
import com.fdb.frankfurt.repository.AccountRepository;
import com.fdb.frankfurt.repository.OutboxRepository;
import com.fdb.frankfurt.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class RateLimitingTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private OutboxRepository outboxRepository;

    @MockBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @MockBean
    private org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    @Test
    @SuppressWarnings("null")
    public void testRateLimiting() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
        when(valueOperations.increment(any())).thenAnswer(inv -> (long) counter.incrementAndGet());

        when(accountRepository.existsById(any())).thenReturn(true);
        
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        request.setToAccountId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        request.setAmount(new BigDecimal("10.0"));

        // Make more than 20 requests
        for (int i = 0; i < 25; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity("/api/transfer", request, String.class);
            if (i < 20) {
                assertThat(response.getStatusCode()).isIn(HttpStatus.ACCEPTED, HttpStatus.OK);
            } else {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            }
        }
    }
}
