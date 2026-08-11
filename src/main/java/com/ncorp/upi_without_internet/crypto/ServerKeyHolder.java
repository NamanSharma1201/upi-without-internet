package com.ncorp.upi_without_internet.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

@Component
@Slf4j
public class ServerKeyHolder {
    private KeyPair keyPair;
    @PostConstruct
    public void init() throws Exception{
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2024);
        this.keyPair = gen.generateKeyPair();
        log.info("Server RSA keypair generated (2048-bit). Public key fingerprint: {}",
                getPublicKeyBase64().substring(0, 32) + "...");
    }
    public PublicKey getPublicKey(){
        return keyPair.getPublic();
    }
    public PrivateKey getPrivateKey(){
        return keyPair.getPrivate();
    }
    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}
