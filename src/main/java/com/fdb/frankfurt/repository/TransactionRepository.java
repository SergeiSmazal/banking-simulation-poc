package com.fdb.frankfurt.repository;

import com.fdb.frankfurt.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}
