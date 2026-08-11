package com.ncorp.upi_without_internet.service;

import com.ncorp.upi_without_internet.dto.AccountDto;
import com.ncorp.upi_without_internet.entitty.Account;
import com.ncorp.upi_without_internet.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    @Autowired
    public AccountService(AccountRepository accountRepository){
        this.accountRepository = accountRepository;
    }

    public List<AccountDto> getAccounts(){
        return toDto(accountRepository.findAll());
    }

    private List<AccountDto> toDto(List<Account> accounts){
        return accounts.stream().map(acc -> {
            return AccountDto.builder().vpa(acc.getVpa())
                    .holderName(acc.getHolderName())
                    .balance(acc.getBalance())
                    .build();
        }).toList();
    }
}
