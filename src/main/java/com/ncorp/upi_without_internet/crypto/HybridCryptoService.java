package com.ncorp.upi_without_internet.crypto;

import com.ncorp.upi_without_internet.dto.PaymentInstructionDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

@Service
public class HybridCryptoService {
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int RSA_ENCRYPTED_KEY_BYTES = 256; // for 2048-bit RSA

    private final SecureRandom rng = new SecureRandom();
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private ServerKeyHolder serverKey;

    public String encrypt(PaymentInstructionDto instructions, PublicKey serverPublicKey) throws Exception{
        byte[] plaintext = json.writeValueAsBytes(instructions);
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_BITS);

        SecretKey aesKey = kg.generateKey();
        byte[] iv = new byte[GCM_IV_BYTES];
        rng.nextBytes(iv);
        Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
        aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] aesCiphertext = aes.doFinal(plaintext);
        Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
        OAEPParameterSpec opec = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );

        rsa.init(Cipher.ENCRYPT_MODE, serverPublicKey, opec);

        byte[] encryptedAesKey = rsa.doFinal(aesKey.getEncoded());

        ByteBuffer buf = ByteBuffer.allocate(encryptedAesKey.length + iv.length + aesCiphertext.length);
        buf.put(encryptedAesKey);
        buf.put(iv);
        buf.put(aesCiphertext);


        return Base64.getEncoder().encodeToString(buf.array());


    }
    public PaymentInstructionDto decrypt(String base64Ciphertext) throws Exception{
        byte[] all = Base64.getDecoder().decode(base64Ciphertext);
        if(all.length < (RSA_ENCRYPTED_KEY_BYTES + GCM_IV_BYTES + GCM_TAG_BITS)){
            throw new IllegalArgumentException("Ciphertext too short");
        }

        byte[] encryptedAesKey = new byte[RSA_ENCRYPTED_KEY_BYTES];
        byte[] iv = new byte[GCM_IV_BYTES];
        byte[] aesCipherText = new byte[all.length - RSA_ENCRYPTED_KEY_BYTES - GCM_IV_BYTES];

        ByteBuffer buf = ByteBuffer.wrap(all);
        buf.get(encryptedAesKey);
        buf.get(iv);
        buf.get(aesCipherText);
    }

}
