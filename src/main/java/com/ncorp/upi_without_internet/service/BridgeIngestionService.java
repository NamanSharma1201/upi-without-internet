package com.ncorp.upi_without_internet.service;

import com.ncorp.upi_without_internet.crypto.HybridCryptoService;
import com.ncorp.upi_without_internet.dto.PaymentInstructionDto;
import com.ncorp.upi_without_internet.dto.TransactionDto;
import com.ncorp.upi_without_internet.modal.MeshPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class BridgeIngestionService {
    @Autowired
    private HybridCryptoService crypto;

    @Autowired
    private IdempotencyService idempotency;

    @Autowired
    private  SettlementService settlement;

    @Value("${upi.mesh.packet-max-age-seconds:86400}")
    private long maxAgeSeconds;

    public IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount){
        try{
            String packetHash = crypto.hashCiphertext(packet.getCipherText());
            if(!idempotency.claim(packetHash)){
                log.info("Duplicate packet {} from bridge {} - dropped",
                        packetHash.substring(0, 12) + "..." , bridgeNodeId);

                return IngestResult.duplicate(packetHash);
            }
            PaymentInstructionDto instruction;
            try {
                instruction = crypto.decrypt(packet.getCipherText());

            } catch (Exception e) {
                log.warn("Decryption failed for packet {}: {}",
                        packetHash.substring(0, 12) + "...", e.getMessage());
                return IngestResult.invalid(packetHash, "decryption_failed");
            }

            long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt()) / 1000;
            if(ageSeconds > maxAgeSeconds){
                log.warn("Packet {} too old ({}s), rejected",
                        packetHash.substring(0, 12) + "...", ageSeconds);
                return IngestResult.invalid(packetHash, "stale_packet");
            }

            TransactionDto tx = settlement.settle(instruction, packetHash, bridgeNodeId, hopCount);
            return IngestResult.settled(packetHash, tx);

        }catch (Exception e){
            log.error("Ingestion error : {}", e.getMessage());
            return IngestResult.invalid("?", "internal error: " + e.getMessage())
        }
    }


    public record IngestResult(String outcome, String packetHash, String reason, Long transactionId) {
        public static IngestResult settled(String hash, TransactionDto tx) {
            return new IngestResult("SETTLED", hash, null, tx.getId());
        }
        public static IngestResult duplicate(String hash) {
            return new IngestResult("DUPLICATE_DROPPED", hash, null, null);
        }
        public static IngestResult invalid(String hash, String reason) {
            return new IngestResult("INVALID", hash, reason, null);
        }
    }





}
