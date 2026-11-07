package com.fdb.frankfurt.service;

import com.fdb.frankfurt.dto.TransferRequest;
import com.fdb.frankfurt.model.Account;
import com.fdb.frankfurt.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimulatorService {

    private final AccountRepository accountRepository;
    private final TransferService transferService;

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final Random random = new Random();

    public void start() {
        enabled.set(true);
        log.info("Simulator started");
    }

    public void stop() {
        enabled.set(false);
        log.info("Simulator stopped");
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void simulate() {
        if (!enabled.get()) {
            return;
        }

        List<Account> accounts = accountRepository.findAll();
        if (accounts.size() < 2) {
            log.warn("Simulator: not enough accounts to generate a transfer");
            return;
        }

        Account from = accounts.get(random.nextInt(accounts.size()));
        Account to;
        do {
            to = accounts.get(random.nextInt(accounts.size()));
        } while (to.getId().equals(from.getId()));

        BigDecimal amount = BigDecimal.valueOf(random.nextInt(1, 500))
                .setScale(2, RoundingMode.HALF_UP);

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(from.getId());
        request.setToAccountId(to.getId());
        request.setAmount(amount);

        try {
            transferService.createTransfer(request);
            log.info("SIM: {} -> {} : {}", from.getOwnerName(), to.getOwnerName(), amount);
        } catch (RuntimeException e) {
            log.warn("SIM: skipped transfer {} -> {}: {}", from.getId(), to.getId(), e.getMessage());
        }
    }
}