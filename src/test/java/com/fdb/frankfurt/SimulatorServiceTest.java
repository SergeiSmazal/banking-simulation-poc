package com.fdb.frankfurt;

import com.fdb.frankfurt.dto.TransferRequest;
import com.fdb.frankfurt.model.Account;
import com.fdb.frankfurt.repository.AccountRepository;
import com.fdb.frankfurt.service.SimulatorService;
import com.fdb.frankfurt.service.TransferService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SimulatorServiceTest {

    private static Account account(UUID id, String owner, BigDecimal balance) {
        Account account = new Account();
        account.setId(id);
        account.setOwnerName(owner);
        account.setBalance(balance);
        account.setCurrency("EUR");
        return account;
    }

    @Test
    public void doesNotGenerateTransfersWhenDisabled() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        TransferService transferService = mock(TransferService.class);
        when(accountRepository.findAll()).thenReturn(List.of(
                account(UUID.randomUUID(), "A", new BigDecimal("100")),
                account(UUID.randomUUID(), "B", new BigDecimal("200"))
        ));

        SimulatorService service = new SimulatorService(accountRepository, transferService);
        service.simulate();

        verify(transferService, never()).createTransfer(any(TransferRequest.class));
    }

    @Test
    public void generatesRandomTransferBetweenTwoAccountsWhenEnabled() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        TransferService transferService = mock(TransferService.class);
        when(accountRepository.findAll()).thenReturn(List.of(
                account(UUID.randomUUID(), "A", new BigDecimal("100")),
                account(UUID.randomUUID(), "B", new BigDecimal("200"))
        ));

        SimulatorService service = new SimulatorService(accountRepository, transferService);
        service.start();
        service.simulate();

        verify(transferService).createTransfer(any(TransferRequest.class));
    }

    @Test
    public void skipsWhenNotEnoughAccounts() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        TransferService transferService = mock(TransferService.class);
        when(accountRepository.findAll()).thenReturn(List.of(
                account(UUID.randomUUID(), "A", new BigDecimal("100"))
        ));

        SimulatorService service = new SimulatorService(accountRepository, transferService);
        service.start();
        service.simulate();

        verify(transferService, never()).createTransfer(any(TransferRequest.class));
    }

    @Test
    public void startStopToggleControlState() {
        AccountRepository accountRepository = mock(AccountRepository.class);
        TransferService transferService = mock(TransferService.class);
        SimulatorService service = new SimulatorService(accountRepository, transferService);

        assertThat(service.isEnabled()).isFalse();
        service.start();
        assertThat(service.isEnabled()).isTrue();
        service.stop();
        assertThat(service.isEnabled()).isFalse();
        service.toggle();
        assertThat(service.isEnabled()).isTrue();
    }
}