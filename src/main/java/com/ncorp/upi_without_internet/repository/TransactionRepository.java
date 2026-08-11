package com.ncorp.upi_without_internet.repository;

import com.ncorp.upi_without_internet.dto.TransactionDto;
import com.ncorp.upi_without_internet.entitty.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findTop20ByOrderByIdDesc();
    boolean existsByPacketHash(String packetHash);
}
