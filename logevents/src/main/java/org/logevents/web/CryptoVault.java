package org.logevents.web;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;

/**
 * Encrypts and decrypts strings with Blowfish cipher. Used to send encrypted cookies.
 */
public class CryptoVault {
    private final Cipher encryptCipher;
    private final Cipher decryptCipher;

    public CryptoVault(Optional<String> encryptionKey) {
        try {
            SecretKeySpec keySpec=new SecretKeySpec(
                    encryptionKey.orElseGet(() -> randomString(40)).getBytes(),
                    "Blowfish"
            );
            encryptCipher = Cipher.getInstance("Blowfish");
            encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec);
            decryptCipher = Cipher.getInstance("Blowfish");
            decryptCipher.init(Cipher.DECRYPT_MODE, keySpec);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Random random = new Random();

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public String encrypt(String string) {
        try {
            return Base64.getEncoder().encodeToString(encryptCipher.doFinal(string.getBytes()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public String decrypt(String value) throws GeneralSecurityException {
        return new String(decryptCipher.doFinal(Base64.getDecoder().decode(value)));
    }
}
