package com.fdb.frankfurt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fdb.frankfurt.dto.TransferRequest;
import com.fdb.frankfurt.model.OutboxEvent;
import com.fdb.frankfurt.model.Transaction;
import com.fdb.frankfurt.repository.AccountRepository;
import com.fdb.frankfurt.repository.OutboxRepository;
import com.fdb.frankfurt.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @Transactional
    public void createTransfer(TransferRequest request) {
        // 1. Validate accounts exist
        if (!accountRepository.existsById(request.getFromAccountId()) ||
            !accountRepository.existsById(request.getToAccountId())) {
            throw new IllegalArgumentException("Account not found");
        }

        // 2. Create Transaction record
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setAccountId(request.getFromAccountId());
        transaction.setAmount(request.getAmount());
        transaction.setStatus("PENDING");
        transaction.setIdempotencyKey(UUID.randomUUID().toString());
        transactionRepository.save(transaction);

        // 3. Create Outbox event
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setAggregateId(transaction.getId().toString());
        outboxEvent.setEventType("TRANSFER_CREATED");
        outboxEvent.setPayload(objectMapper.writeValueAsString(request));
        outboxRepository.save(outboxEvent);
    }
}
