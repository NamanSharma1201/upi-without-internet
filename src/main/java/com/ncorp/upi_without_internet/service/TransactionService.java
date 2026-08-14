package com.ncorp.upi_without_internet.service;

import com.ncorp.upi_without_internet.dto.TransactionDto;
import com.ncorp.upi_without_internet.entitty.Transaction;
import com.ncorp.upi_without_internet.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository){
        this.transactionRepository = transactionRepository;
    }

    public List<TransactionDto> getTransactions(){
        return toDto(transactionRepository.findTop20ByOrderByIdDesc());
    }

    public boolean existsByPacketHash(String packetHash){
        return transactionRepository.existsByPacketHash(packetHash);
    }

    public TransactionDto save(Transaction tx){
        return toDto(transactionRepository.save(tx));
    }

    private TransactionDto toDto(Transaction t){
        return TransactionDto.builder()
                .id(t.getId())
                .senderVpa(t.getSenderVpa())
                .receiverVpa(t.getReceiverVpa())
                .amount(t.getAmount())
                .settledAt(t.getSettledAt())
                .signedAt(t.getSignedAt())
                .bridgeNodeId(t.getBridgeNodeId())
                .hopCount(t.getHopCount())
                .status(t.getStatus())
                .hopCount(t.getHopCount()).build();
    }

    private List<TransactionDto> toDto(List<Transaction> transactions){
        return transactions.stream().map(t -> {
            return TransactionDto.builder()
                    .id(t.getId())
                    .senderVpa(t.getSenderVpa())
                    .receiverVpa(t.getReceiverVpa())
                    .amount(t.getAmount())
                    .settledAt(t.getSettledAt())
                    .signedAt(t.getSignedAt())
                    .bridgeNodeId(t.getBridgeNodeId())
                    .hopCount(t.getHopCount())
                    .status(t.getStatus())
                    .hopCount(t.getHopCount()).build();
        }).toList();
    }
}
