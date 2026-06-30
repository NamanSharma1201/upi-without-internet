package com.ncorp.upi_without_internet.repository;

import com.ncorp.upi_without_internet.entitty.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface AccountRepository extends JpaRepository<Account, String> {
}
