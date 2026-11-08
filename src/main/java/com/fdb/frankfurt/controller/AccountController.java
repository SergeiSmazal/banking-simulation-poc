package com.fdb.frankfurt.controller;

import com.fdb.frankfurt.model.Account;
import com.fdb.frankfurt.model.Transaction;
import com.fdb.frankfurt.repository.AccountRepository;
import com.fdb.frankfurt.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @GetMapping
    public ResponseEntity<List<Account>> getAllAccounts() {
        return ResponseEntity.ok(accountRepository.findAll());
    }
}

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
class TransactionHistoryController {

    private final TransactionRepository transactionRepository;

    @GetMapping("/history")
    public ResponseEntity<List<Transaction>> getHistory() {
        List<Transaction> transactions = transactionRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(100)
                .collect(Collectors.toList());
        return ResponseEntity.ok(transactions);
    }
}