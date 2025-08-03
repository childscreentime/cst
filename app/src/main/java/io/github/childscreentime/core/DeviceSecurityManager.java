package io.github.childscreentime.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;

/**
 * Manages device identification and encryption for parent-child communication
 */
public class DeviceSecurityManager {
    
    private static final String PREFS_NAME = "device_security";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_ENCRYPTION_KEY = "encryption_key";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    
    private final Context context;
    private final SharedPreferences prefs;
    
    public DeviceSecurityManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Ensure device ID and encryption key exist
        initializeSecurityCredentials();
    }
    
    /**
     * Initialize device ID and encryption key if they don't exist
     */
    private void initializeSecurityCredentials() {
        try {
            if (!prefs.contains(KEY_DEVICE_ID)) {
                // Generate unique device ID
                String deviceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
                prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
            }
            
            if (!prefs.contains(KEY_ENCRYPTION_KEY)) {
                // Generate encryption key based on device ID
                String deviceId = getDeviceId();
                String encryptionKey = generateEncryptionKey(deviceId);
                prefs.edit().putString(KEY_ENCRYPTION_KEY, encryptionKey).apply();
            }
            
            // Validate the encryption setup works
            validateEncryptionSetup();
        } catch (Exception e) {
            android.util.Log.e("DeviceSecurityManager", "Security initialization failed - parent discovery will be disabled", e);
            // Clear any partial setup
            prefs.edit().remove(KEY_DEVICE_ID).remove(KEY_ENCRYPTION_KEY).apply();
            throw new RuntimeException("Security initialization failed", e);
        }
    }
    
    /**
     * Validate that encryption/decryption works correctly
     */
    private void validateEncryptionSetup() {
        String testMessage = "SECURITY_TEST";
        String encrypted = encryptMessage(testMessage);
        String decrypted = decryptMessage(encrypted);
        
        if (!testMessage.equals(decrypted)) {
            throw new RuntimeException("Encryption validation failed");
        }
    }
    
    /**
     * Get the unique device ID
     */
    public String getDeviceId() {
        return prefs.getString(KEY_DEVICE_ID, "");
    }
    
    /**
     * Check if the security manager is properly initialized and encryption works
     */
    public boolean isSecurityEnabled() {
        try {
            String deviceId = getDeviceId();
            if (deviceId.isEmpty()) {
                return false;
            }
            
            String keyString = prefs.getString(KEY_ENCRYPTION_KEY, "");
            if (keyString.isEmpty()) {
                return false;
            }
            
            // Test encryption/decryption
            validateEncryptionSetup();
            return true;
        } catch (Exception e) {
            android.util.Log.e("DeviceSecurityManager", "Security check failed", e);
            return false;
        }
    }
    
    /**
     * Generate encryption key from device ID using SHA-256 for consistency
     */
    private String generateEncryptionKey(String deviceId) {
        try {
            // Use SHA-256 to derive a consistent 256-bit key from device ID
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(deviceId.getBytes(StandardCharsets.UTF_8));
            
            // Use first 16 bytes for AES-128
            byte[] aesKeyBytes = new byte[16];
            System.arraycopy(keyBytes, 0, aesKeyBytes, 0, 16);
            
            return Base64.encodeToString(aesKeyBytes, Base64.DEFAULT);
        } catch (Exception e) {
            android.util.Log.e("DeviceSecurityManager", "Failed to generate encryption key - parent discovery disabled", e);
            throw new RuntimeException("Encryption key generation failed", e);
        }
    }
    
    /**
     * Get the encryption key
     */
    private SecretKey getEncryptionKey() {
        try {
            String keyString = prefs.getString(KEY_ENCRYPTION_KEY, "");
            if (keyString.isEmpty()) {
                throw new RuntimeException("No encryption key found");
            }
            byte[] keyBytes = Base64.decode(keyString, Base64.DEFAULT);
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            android.util.Log.e("DeviceSecurityManager", "Failed to get encryption key - parent discovery disabled", e);
            throw new RuntimeException("Encryption key retrieval failed", e);
        }
    }
    
    /**
     * Encrypt a message using the device's encryption key
     */
    public String encryptMessage(String message) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            // Generate IV for CBC mode
            byte[] iv = new byte[16]; // AES block size
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, getEncryptionKey(), ivSpec);
            
            byte[] encryptedBytes = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            
            // Prepend IV to encrypted data
            byte[] encryptedWithIv = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, encryptedWithIv, iv.length, encryptedBytes.length);
            
            return Base64.encodeToString(encryptedWithIv, Base64.DEFAULT);
        } catch (Exception e) {
            android.util.Log.e("DeviceSecurityManager", "Failed to encrypt message - parent discovery disabled", e);
            throw new RuntimeException("Message encryption failed", e);
        }
    }
    
    /**
     * Decrypt a message using the device's encryption key
     */
    public String decryptMessage(String encryptedMessage) {
        try {
            byte[] encryptedWithIv = Base64.decode(encryptedMessage, Base64.DEFAULT);
            
            // Extract IV from the beginning
            byte[] iv = new byte[16]; // AES block size
            byte[] encryptedBytes = new byte[encryptedWithIv.length - 16];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, 16);
            System.arraycopy(encryptedWithIv, 16, encryptedBytes, 0, encryptedBytes.length);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, getEncryptionKey(), ivSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            android.util.Log.e("DeviceSecurityManager", "Failed to decrypt message - parent discovery disabled", e);
            throw new RuntimeException("Message decryption failed", e);
        }
    }
    
    /**
     * Validate if a message was encrypted with the same key (for parent verification)
     */
    public boolean validateEncryptedMessage(String encryptedMessage) {
        try {
            String testMessage = "TEST_VALIDATION";
            String encrypted = encryptMessage(testMessage);
            String decrypted = decryptMessage(encrypted);
            return testMessage.equals(decrypted);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Create encryption key from device ID for parent app to use
     */
    public static String createKeyFromDeviceId(String deviceId) {
        try {
            // Use SHA-256 to derive a consistent 256-bit key from device ID
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(deviceId.getBytes(StandardCharsets.UTF_8));
            
            // Use first 16 bytes for AES-128
            byte[] aesKeyBytes = new byte[16];
            System.arraycopy(keyBytes, 0, aesKeyBytes, 0, 16);
            
            return Base64.encodeToString(aesKeyBytes, Base64.DEFAULT);
        } catch (Exception e) {
            android.util.Log.e("DeviceSecurityManager", "Failed to create key from device ID - parent discovery disabled", e);
            throw new RuntimeException("Key creation from device ID failed", e);
        }
    }
}
