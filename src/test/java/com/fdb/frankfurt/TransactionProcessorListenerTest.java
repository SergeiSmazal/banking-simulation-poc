package com.fdb.frankfurt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fdb.frankfurt.dto.TransferRequest;
import com.fdb.frankfurt.model.Account;
import com.fdb.frankfurt.model.Transaction;
import com.fdb.frankfurt.repository.AccountRepository;
import com.fdb.frankfurt.repository.TransactionRepository;
import com.fdb.frankfurt.service.TransactionProcessorListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TransactionProcessorListenerTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private Acknowledgment acknowledgment;

    private TransactionProcessorListener transactionProcessorListener;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        transactionProcessorListener = new TransactionProcessorListener(
                accountRepository,
                transactionRepository,
                stringRedisTemplate,
                redissonClient,
                objectMapper
        );
    }

    @Test
    public void testConsumeTransactionSuccess() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        TransferRequest request = new TransferRequest(fromId, toId, new BigDecimal("100.00"));
        String payload = objectMapper.writeValueAsString(request);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null); // cache miss to test cache-aside

        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setStatus("PENDING");
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        Account fromAcc = new Account();
        fromAcc.setId(fromId);
        fromAcc.setBalance(new BigDecimal("500.00"));

        Account toAcc = new Account();
        toAcc.setId(toId);
        toAcc.setBalance(new BigDecimal("200.00"));

        when(accountRepository.findById(fromId)).thenReturn(Optional.of(fromAcc));
        when(accountRepository.findById(toId)).thenReturn(Optional.of(toAcc));

        transactionProcessorListener.consumeTransaction(payload, txId.toString(), acknowledgment);

        assertThat(fromAcc.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(toAcc.getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(tx.getStatus()).isEqualTo("COMPLETED");

        verify(acknowledgment, times(1)).acknowledge();
        verify(accountRepository, times(1)).save(fromAcc);
        verify(accountRepository, times(1)).save(toAcc);
        verify(transactionRepository, times(1)).save(tx);
    }

    @Test
    public void testIdempotencySkipped() {
        UUID txId = UUID.randomUUID();
        // Return false for setIfAbsent, meaning already processed
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        transactionProcessorListener.consumeTransaction("payload", txId.toString(), acknowledgment);

        verify(acknowledgment, times(1)).acknowledge();
        verify(transactionRepository, never()).findById(any());
    }

    @Test
    public void testInsufficientFunds() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        TransferRequest request = new TransferRequest(fromId, toId, new BigDecimal("1000.00"));
        String payload = objectMapper.writeValueAsString(request);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("100.00"); // cached balance

        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setStatus("PENDING");
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        Account fromAcc = new Account();
        fromAcc.setId(fromId);
        fromAcc.setBalance(new BigDecimal("100.00"));

        Account toAcc = new Account();
        toAcc.setId(toId);
        toAcc.setBalance(new BigDecimal("200.00"));

        when(accountRepository.findById(fromId)).thenReturn(Optional.of(fromAcc));
        when(accountRepository.findById(toId)).thenReturn(Optional.of(toAcc));

        transactionProcessorListener.consumeTransaction(payload, txId.toString(), acknowledgment);

        assertThat(tx.getStatus()).isEqualTo("FAILED");
        verify(acknowledgment, times(1)).acknowledge();
        verify(transactionRepository, times(1)).save(tx);
        verify(accountRepository, never()).save(fromAcc);
    }

    @Test
    public void testConsumerProcessingFailureThrowsException() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        TransferRequest request = new TransferRequest(fromId, toId, new BigDecimal("100.00"));
        String payload = objectMapper.writeValueAsString(request);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(transactionRepository.findById(txId)).thenThrow(new RuntimeException("DB Connection Error"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            transactionProcessorListener.consumeTransaction(payload, txId.toString(), acknowledgment);
        });

        verify(stringRedisTemplate, times(1)).delete("processed:" + txId);
        verify(acknowledgment, never()).acknowledge();
    }
}
