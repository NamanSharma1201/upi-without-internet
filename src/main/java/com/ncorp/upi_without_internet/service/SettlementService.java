package com.ncorp.upi_without_internet.service;

import com.ncorp.upi_without_internet.dto.AccountDto;
import com.ncorp.upi_without_internet.dto.PaymentInstructionDto;
import com.ncorp.upi_without_internet.dto.TransactionDto;
import com.ncorp.upi_without_internet.entitty.Transaction;
import com.ncorp.upi_without_internet.modal.Status;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@Slf4j
public class SettlementService {
    private final AccountService accountService;
    private final TransactionService transactionService;

    @Autowired
    SettlementService(AccountService accountService, TransactionService transactionService){
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @Transactional
    public TransactionDto settle(PaymentInstructionDto instruction, String packetHash,
                                 String bridgeNodeId, int hopCount){
        AccountDto sender = accountService.findById(instruction.getSenderVpa());
        if(sender == null){
            throw new IllegalArgumentException(
                    "Unknown sender VPA: "
                    + instruction.getSenderVpa()
            );
        }
        AccountDto receiver = accountService.findById(instruction.getReceiverVpa());

        if(receiver == null){
            throw new IllegalArgumentException(
                    "Unknown receiver VPA: "
                            + instruction.getReceiverVpa()
            );
        }

        BigDecimal amount = instruction.getAmount();
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (sender.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance: {} has ₹{}, tried to send ₹{}",
                    sender.getVpa(), sender.getBalance(), amount);
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount);
        }

        sender = accountService.updateBalance(sender.getBalance().subtract(amount), sender.getVpa());
        receiver = accountService.updateBalance(receiver.getBalance().add(amount), receiver.getVpa());

        Transaction tx = new Transaction();
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(sender.getVpa());
        tx.setReceiverVpa(receiver.getVpa());
        tx.setAmount(amount);
        tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        tx.setSettledAt(Instant.now());
        tx.setHopCount(hopCount);
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setStatus(Status.SETTLED);

        TransactionDto transaction = transactionService.save(tx);

        log.info("SETTLED ₹{} from {} to {} (packetHash={}, bridge={}, hops={})",
                amount, sender.getVpa(), receiver.getVpa(),
                packetHash.substring(0, 12) + "...", bridgeNodeId, hopCount);




        return transaction;

    }

    private TransactionDto recordRejected(PaymentInstructionDto instructionDto, String packetHash, String bridgeNodeId, int hopCount){
        Transaction tx = new Transaction();
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instructionDto.getSenderVpa());
        tx.setReceiverVpa(instructionDto.getReceiverVpa());
        tx.setAmount(instructionDto.getAmount());
        tx.setSignedAt(Instant.ofEpochMilli(instructionDto.getSignedAt()));
        tx.setSettledAt(Instant.now());
        tx.setHopCount(hopCount);
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setStatus(Status.REJECTED);

        return transactionService.save(tx);
    }
}
