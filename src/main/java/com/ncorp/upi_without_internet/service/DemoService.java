package com.ncorp.upi_without_internet.service;

import com.ncorp.upi_without_internet.crypto.HybridCryptoService;
import com.ncorp.upi_without_internet.crypto.ServerKeyHolder;
import com.ncorp.upi_without_internet.dto.AccountDto;
import com.ncorp.upi_without_internet.dto.PaymentInstructionDto;
import com.ncorp.upi_without_internet.modal.MeshPacket;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.ParameterResolutionDelegate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class DemoService {
    @Autowired
    private AccountService accountService;

    @Autowired
    private HybridCryptoService crypto;

    @Autowired
    private ServerKeyHolder serverKey;

    @PostConstruct
    public void seedAccounts(){
        if(accountService.getAccounts().isEmpty()){
            accountService.addAccount(new AccountDto("Naman@demo", "Naman",   new BigDecimal("5000.00")));
            accountService.addAccount(new AccountDto("bob@demo", "Bob", new BigDecimal("1000.00")));
            accountService.addAccount(new AccountDto("carol@demo", "Carol", new BigDecimal("2500.00")));
            accountService.addAccount(new AccountDto("dave@demo", "Dave", new BigDecimal("500.00")));
            log.info("Seeded 4 demo accounts");
        }
    }


    public MeshPacket createPacket(String senderVpa, String receiverVpa,
                                   BigDecimal amount, String pin, int ttl) throws Exception{
        PaymentInstructionDto instruction = new PaymentInstructionDto(
                senderVpa,
                receiverVpa,
                amount,
                sha256Hex(pin),
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli()
        );
        String ciphertext = crypto.encrypt(instruction, serverKey.getPublicKey());
        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCipherText(ciphertext);


        return packet;
    }


    private String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }


}
