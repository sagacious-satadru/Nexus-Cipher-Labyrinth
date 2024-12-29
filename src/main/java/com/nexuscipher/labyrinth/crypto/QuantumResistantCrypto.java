package com.nexuscipher.labyrinth.crypto;

import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;  // For DILITHIUM_MODE_III

import java.security.*;

public class QuantumResistantCrypto {
    private static final Logger logger = LoggerFactory.getLogger(QuantumResistantCrypto.class);

    private KeyPair keyPair;

    public QuantumResistantCrypto() {
        try {
            // Register Bouncy Castle Provider
            Security.addProvider(new BouncyCastlePQCProvider());

            // Generate quantum-resistant keys (we'll use Dilithium for now)
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Dilithium");
            kpg.initialize(DilithiumParameterSpec.dilithium3);
            keyPair = kpg.generateKeyPair();

            logger.info("Initialized quantum-resistant cryptography");
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to initialize quantum-resistant cryptography", e);
            throw new RuntimeException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getPublicKey() {
        return keyPair.getPublic().getEncoded();
    }

    public byte[] sign(byte[] data) throws SignatureException {
        try {
            Signature signature = Signature.getInstance("Dilithium");
            signature.initSign(keyPair.getPrivate());
            signature.update(data);
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SignatureException("Failed to sign data", e);
        }
    }

    public boolean verify(byte[] data, byte[] signature, byte[] publicKey) {
        try {
            Signature verifier = Signature.getInstance("Dilithium");
            verifier.initVerify(KeyFactory.getInstance("Dilithium")
                    .generatePublic(new X509EncodedKeySpec(publicKey)));
            verifier.update(data);
            return verifier.verify(signature);
        } catch (Exception e) {
            logger.error("Failed to verify signature", e);
            return false;
        }
    }
}