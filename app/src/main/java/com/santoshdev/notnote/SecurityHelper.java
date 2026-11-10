package com.santoshdev.notnote;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecurityHelper {

    private static final String KEY_ALIAS = "NotNoteAiKeyAlias";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    // Keystore initialize aur naya key generate karna (agar pehle se nahi hai)
    private static void initKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build();
            keyGenerator.init(keySpec);
            keyGenerator.generateKey();
        }
    }

    // Generate ki gayi secret key ko retrieve karna
    private static SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
        return secretKeyEntry.getSecretKey();
    }

    // Plain text API key ko encrypt karna
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.trim().isEmpty()) return null;
        
        try {
            initKeyStore();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            
            byte[] iv = cipher.getIV();
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV aur Encrypted String dono ko Base64 mein encode karke combine karna
            String encodedIv = Base64.encodeToString(iv, Base64.NO_WRAP);
            String encodedCipher = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);

            // ']' symbol ka use as a separator taaki decrypt karte waqt split kar sakein
            return encodedIv + "]" + encodedCipher;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Encrypted string se wapas original API key nikalna memory mein use karne ke liye
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || !encryptedText.contains("]")) return null;
        
        try {
            String[] parts = encryptedText.split("\\]");
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] cipherText = Base64.decode(parts[1], Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            // 128 bit GCM authentication tag length
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);

            byte[] decryptedBytes = cipher.doFinal(cipherText);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
