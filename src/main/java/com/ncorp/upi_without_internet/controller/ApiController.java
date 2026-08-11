package com.ncorp.upi_without_internet.controller;

import com.ncorp.upi_without_internet.dto.AccountDto;
import com.ncorp.upi_without_internet.dto.DemoSendRequest;
import com.ncorp.upi_without_internet.dto.TransactionDto;
import com.ncorp.upi_without_internet.entitty.Transaction;
import com.ncorp.upi_without_internet.modal.MeshPacket;
import com.ncorp.upi_without_internet.modal.VirtualDevice;
import com.ncorp.upi_without_internet.service.AccountService;
import com.ncorp.upi_without_internet.service.MeshSimulatorService;
import com.ncorp.upi_without_internet.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private MeshSimulatorService mesh;
    @GetMapping("/server-key")
    public Map<String, String> getServerPublicKey(){
        return Map.of(
                "publicKey", serverKey.getPublicKeyBase64(),
                "algorithm", "RSA-2048 / OAEP-SHA256",
                "hybridScheme", "RSA-OAEP encrypts an AES-256-GCM session key"
        );
    }

    @PostMapping("/demo/send")
    public ResponseEntity<?> demoSend(@RequestBody DemoSendRequest req) throws  Exception{
        MeshPacket packet = demo.createPacket(
                req.getSenderVpa(),
                req.getReceiverVpa(),
                req.getAmount(),
                req.getPin(),
                req.getTtl() == null ? 5 : req.getTtl()

        );
        String startDevice = req.getStartDevice() == null ? "phone-naman" : req.getStartDevice();
        mesh.inject(startDevice, packet);
        return ResponseEntity.ok(Map.of(
                "packetId", packet.getPacketId(),
                "ciphertextPreview", packet.getCipherText().substring(0, 64) + "...",
                "ttl", packet.getTtl(),
                "injectedAt", startDevice
        ));
    }


    @GetMapping("/mesh/state")
    public Map<String, Object> meshState(){
        List<Map<String, Object>> deviceData = new ArrayList<>();
        for(VirtualDevice d : mesh.getDevices()){
            deviceData.add(Map.of(
                    "deviceId", d.getDeviceId(),
                    "hasInternet", d.hasInternet(),
                    "packetCount", d.packetCount(),
                    "packetIds", d.getHeldPackets().stream()
                            .map(p -> p.getPacketId().substring(0, 8))
                            .toList()
            ));
        }


        return Map.of("devices", deviceData,
                "idempotencyCacheSize", idempotency.size());
    }


    @PostMapping("/mesh/gossip")
    public Map<String, Object> meshGossip(){
        MeshSimulatorService.GossipResult r = mesh.gossipOnce();
        return Map.of("transfer", r.transfers(),
                "deviceCounts", r.deviceCounts());
    }


    @PostMapping("/mesh/flush")
    public Map<String, Object> meshFlush(){
        List<MeshSimulatorService.BridgeUpload> uploads = mesh.collectBridgeUploads();

        List<Map<String, Object>> results = new ArrayList<>();

        uploads.parallelStream().forEach(up -> {
            BridgeIngestionService.IngestResult r =
                    bridge.ingest(up.packet(),
                            up.bridgeNodeId(), 5 - up.packet().getTtl());
            synchronized (results){
                results.add(Map.of(
                        "bridgeNode", up.bridgeNodeId(),
                        "packetId", up.packet().getPacketId().substring(0, 8),
                        "outcome", r.outcome(),
                        "reason", r.reason() == null ? "" : r.reason(),
                        "transactionId", r.transactionId() == null ? -1 : r.transactionId()
                ));
            }
        });

        return Map.of(
                "uploadsAttempted", uploads.size(),
                "results", results
        );
    }


    @PostMapping("/mesh/reset")
    public Map<String, Object> meshReset(){
        mesh.resetMesh();
        idempotency.clear();
        return Map.of("status", "mesh and idempotency cache cleared");
    }


    @PostMapping("/bridge/ingest")
    public ResponseEntity<?> ingest(
            @RequestBody MeshPacket packet,
            @RequestHeader(value = "X-Bridge-Node-Id", defaultValue = "unknown") String bridgeNodeId,
            @RequestHeader(value = "X-Hop-Count", defaultValue = "0") int hopCount
    ){
        BridgeIngestionService.IngestResult r = bridge.ingest(packet,  bridgeNodeId, hopCount);
        return ResponseEntity.ok(r);
    }


    @GetMapping("/accounts")
    public List<AccountDto> listAccounts(){
        return accountService.getAccounts();
    }

    @GetMapping("/transactions")
    public List<TransactionDto> listTransactions(){
        return transactionService.getTransactions();
    }




}
