package com.fdb.frankfurt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fdb.frankfurt.dto.TransferRequest;
import com.fdb.frankfurt.model.Account;
import com.fdb.frankfurt.model.Transaction;
import com.fdb.frankfurt.repository.AccountRepository;
import com.fdb.frankfurt.repository.OutboxRepository;
import com.fdb.frankfurt.repository.TransactionRepository;
import com.fdb.frankfurt.service.TransactionProcessorListener;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=" +
    "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
public class TransactionProcessorLoadTest {

    @Autowired
    private TransactionProcessorListener transactionProcessorListener;

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

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RLock rLock;

    @MockBean
    private Acknowledgment acknowledgment;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testConcurrentTransfersLoadTest() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        UUID fromId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID toId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

        Account fromAcc = new Account();
        fromAcc.setId(fromId);
        fromAcc.setBalance(new BigDecimal("1000.00"));

        Account toAcc = new Account();
        toAcc.setId(toId);
        toAcc.setBalance(new BigDecimal("1000.00"));

        when(accountRepository.findById(fromId)).thenReturn(Optional.of(fromAcc));
        when(accountRepository.findById(toId)).thenReturn(Optional.of(toAcc));

        AtomicInteger successfulTransfers = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        int totalTransfers = 50;
        BigDecimal transferAmount = new BigDecimal("10.00");

        CompletableFuture<?>[] futures = new CompletableFuture[totalTransfers];
        for (int i = 0; i < totalTransfers; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    UUID txId = UUID.randomUUID();
                    TransferRequest req = new TransferRequest(fromId, toId, transferAmount);
                    String payload = objectMapper.writeValueAsString(req);

                    Transaction tx = new Transaction();
                    tx.setId(txId);
                    tx.setStatus("PENDING");
                    
                    synchronized (fromAcc) {
                        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
                        transactionProcessorListener.consumeTransaction(payload, txId.toString(), acknowledgment);
                        if ("COMPLETED".equals(tx.getStatus())) {
                            successfulTransfers.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
        }

        CompletableFuture.allOf(futures).join();
        executor.shutdown();

        assertThat(successfulTransfers.get()).isEqualTo(totalTransfers);
        BigDecimal expectedFromBalance = new BigDecimal("1000.00").subtract(transferAmount.multiply(BigDecimal.valueOf(totalTransfers)));
        BigDecimal expectedToBalance = new BigDecimal("1000.00").add(transferAmount.multiply(BigDecimal.valueOf(totalTransfers)));

        assertThat(fromAcc.getBalance()).isEqualByComparingTo(expectedFromBalance);
        assertThat(toAcc.getBalance()).isEqualByComparingTo(expectedToBalance);
    }
}
