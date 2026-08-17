package com.ncorp.upi_without_internet.service;

import com.ncorp.upi_without_internet.dto.AccountDto;
import com.ncorp.upi_without_internet.entitty.Account;
import com.ncorp.upi_without_internet.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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

    public AccountDto findById(String vpa){
        return toDto(accountRepository.findById(vpa));
    }

    public AccountDto updateBalance(BigDecimal amount, String vpa){
        Optional<Account> acc = accountRepository.findById(vpa);
        if(acc.isEmpty()){
            return null;
        }
        Account account = acc.get();
        account.setBalance(amount);
        return  toDto(accountRepository.save(account));
    }

    public void addAccount(AccountDto acc){
        Account account =  Account.builder().balance(acc.getBalance()).holderName(acc.getHolderName()).vpa(acc.getVpa()).build();
        accountRepository.save(account);
    }



    private List<AccountDto> toDto(List<Account> accounts){
        return accounts.stream().map(acc -> {
            return AccountDto.builder().vpa(acc.getVpa())
                    .holderName(acc.getHolderName())
                    .balance(acc.getBalance())
                    .build();
        }).toList();
    }

    private AccountDto toDto(Account acc){


        return AccountDto.builder().vpa(acc.getVpa())
                .holderName(acc.getHolderName())
                .balance(acc.getBalance())
                .build();

    }

    private AccountDto toDto(Optional<Account> account){
        if(account.isEmpty()){
            return null;
        }

        Account acc = account.get();


        return AccountDto.builder().vpa(acc.getVpa())
                .holderName(acc.getHolderName())
                .balance(acc.getBalance())
                .build();

    }
}
