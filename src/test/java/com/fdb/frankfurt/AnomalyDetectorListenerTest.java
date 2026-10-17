package com.fdb.frankfurt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fdb.frankfurt.dto.TransferRequest;
import com.fdb.frankfurt.service.AnomalyDetectorListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class AnomalyDetectorListenerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private Acknowledgment acknowledgment;

    private AnomalyDetectorListener anomalyDetectorListener;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        anomalyDetectorListener = new AnomalyDetectorListener(
                stringRedisTemplate,
                objectMapper
        );
    }

    @Test
    public void testHighAmountAnomalyDetected() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        TransferRequest request = new TransferRequest(fromId, toId, new BigDecimal("60000.00"));
        String payload = objectMapper.writeValueAsString(request);

        when(valueOperations.increment(anyString())).thenReturn(1L);

        anomalyDetectorListener.detectAnomaly(payload, txId.toString(), acknowledgment);

        verify(stringRedisTemplate, times(1)).convertAndSend(eq("alerts"), anyString());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    public void testHighVelocityAnomalyDetected() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        TransferRequest request = new TransferRequest(fromId, toId, new BigDecimal("100.00"));
        String payload = objectMapper.writeValueAsString(request);

        // Velocity count exceeds 5
        when(valueOperations.increment(anyString())).thenReturn(6L);

        anomalyDetectorListener.detectAnomaly(payload, txId.toString(), acknowledgment);

        verify(stringRedisTemplate, times(1)).convertAndSend(eq("alerts"), anyString());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    public void testNormalTransactionNoAnomaly() throws Exception {
        UUID txId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        TransferRequest request = new TransferRequest(fromId, toId, new BigDecimal("100.00"));
        String payload = objectMapper.writeValueAsString(request);

        when(valueOperations.increment(anyString())).thenReturn(2L);

        anomalyDetectorListener.detectAnomaly(payload, txId.toString(), acknowledgment);

        verify(stringRedisTemplate, never()).convertAndSend(anyString(), anyString());
        verify(acknowledgment, times(1)).acknowledge();
    }
}
